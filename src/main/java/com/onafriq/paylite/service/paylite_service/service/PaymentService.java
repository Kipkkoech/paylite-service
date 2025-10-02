package com.onafriq.paylite.service.paylite_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onafriq.paylite.service.paylite_service.dto.PaymentRequest;
import com.onafriq.paylite.service.paylite_service.dto.PaymentResponse;
import com.onafriq.paylite.service.paylite_service.entity.IdempotencyKey;
import com.onafriq.paylite.service.paylite_service.entity.Payment;
import com.onafriq.paylite.service.paylite_service.enums.PaymentStatus;
import com.onafriq.paylite.service.paylite_service.exception.IdempotencyConflictException;
import com.onafriq.paylite.service.paylite_service.exception.PaymentIdGenerationException;
import com.onafriq.paylite.service.paylite_service.exception.PaymentNotFoundException;
import com.onafriq.paylite.service.paylite_service.repository.IdempotencyKeyRepository;
import com.onafriq.paylite.service.paylite_service.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLTransientException;
import java.util.UUID;

import static com.onafriq.paylite.service.paylite_service.config.AppConstants.PAYMENT_ID_PREFIX;
import static com.onafriq.paylite.service.paylite_service.config.AppConstants.WEBHOOK_EVENT_SUCCEEDED;

@Service
public class PaymentService {
    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final IdempotencyService idempotencyService;

    private final ObjectMapper objectMapper;
    private final RetryTemplate retryTemplate;

    public PaymentService(PaymentRepository paymentRepository, IdempotencyService idempotencyService, ObjectMapper objectMapper, RetryTemplate retryTemplate) {
        this.paymentRepository = paymentRepository;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.retryTemplate = retryTemplate;
    }

    @Transactional
    public PaymentResponse createPayment(PaymentRequest request, String idempotencyKey) throws JsonProcessingException {
        String requestHash = idempotencyService.calculateRequestHash(request);

        // Check for existing response
        var existingResponse = idempotencyService.getExistingResponse(idempotencyKey, requestHash);
        if (existingResponse.isPresent()) {
            return objectMapper.readValue(existingResponse.get(), PaymentResponse.class);
        }

        // Check for conflict
        if (idempotencyService.hasConflict(idempotencyKey, requestHash)) {
            throw new IdempotencyConflictException("Idempotency-Key already used with different request");
        }

        String paymentId = generatePaymentId();

        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .reference(request.getReference())
                .customerEmail(request.getCustomerEmail())
                .status(PaymentStatus.PENDING.toString())
                .build();

        paymentRepository.save(payment);
        logger.info("Created payment with ID: {}", paymentId);

        // Store idempotency key with response
        String responseBody = String.format("{\"paymentId\":\"%s\",\"status\":\"PENDING\"}", paymentId);

        idempotencyService.storeIdempotencyKey(idempotencyKey,requestHash, responseBody, paymentId);
        logger.info("Stored idempotency key for payment: {}", paymentId);

        return PaymentResponse.builder().paymentId(paymentId)
                .status(PaymentStatus.PENDING.toString())
                .build();
    }

    public PaymentResponse getPayment(String paymentId) throws SQLTransientException {
        return retryTemplate.execute(context -> {
            Payment payment = paymentRepository.findByPaymentId(paymentId)
                    .orElseThrow(() -> new PaymentNotFoundException(
                            String.format("Payment with ID '%s' not found", paymentId)));

            return new PaymentResponse(
                    payment.getPaymentId(),
                    payment.getStatus(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getReference(),
                    payment.getCustomerEmail()
            );
        });
    }

    @Transactional
    public void processWebhook(String paymentId, String event) {
        retryTemplate.execute(context -> {
            Payment payment = paymentRepository.findByPaymentIdForUpdate(paymentId)
                    .orElseThrow(() -> new PaymentNotFoundException(
                            String.format("Payment with ID '%s' not found", paymentId)));

            String newStatus = WEBHOOK_EVENT_SUCCEEDED.equals(event) ? PaymentStatus.SUCCEEDED.toString() : PaymentStatus.FAILED.toString();

            if (!PaymentStatus.SUCCEEDED.toString().equals(payment.getStatus()) && !PaymentStatus.FAILED.toString().equals(payment.getStatus())) {
                payment.setStatus(newStatus);
                paymentRepository.save(payment);
                logger.info("Updated payment {} status to {}", paymentId, newStatus);
            } else {
                logger.info("Payment {} already in final status: {}", paymentId, payment.getStatus());
            }
            return null;
        });
    }

    private String generatePaymentId() {
        String paymentId;
        int attempts = 0;

        do {
            paymentId = PAYMENT_ID_PREFIX + UUID.randomUUID().toString().substring(0, 8);
            attempts++;

            if (attempts > 5) {
                throw new PaymentIdGenerationException("Failed to generate unique payment ID after 5 attempts");
            }
        } while (paymentRepository.existsByPaymentId(paymentId));

        return paymentId;
    }
}
package com.onafriq.paylite.service.paylite_service.service;

import com.onafriq.paylite.service.paylite_service.dto.WebhookRequest;
import com.onafriq.paylite.service.paylite_service.exception.IdempotencyConflictException;
import com.onafriq.paylite.service.paylite_service.exception.WebhookConflictException;
import com.onafriq.paylite.service.paylite_service.repository.WebhookEventRepository;
import com.onafriq.paylite.service.paylite_service.entity.WebhookEvent;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class WebhookService {
    private static final Logger logger = LoggerFactory.getLogger(WebhookService.class);

    private final PaymentService paymentService;
    private final WebhookEventRepository webhookEventRepository;

    // Event type constants
    private static final String EVENT_SUCCEEDED = "payment.succeeded";
    private static final String EVENT_FAILED = "payment.failed";

    public WebhookService(PaymentService paymentService,
                          WebhookEventRepository webhookEventRepository) {
        this.paymentService = paymentService;
        this.webhookEventRepository = webhookEventRepository;
    }


    public void processWebhook(WebhookRequest webhookRequest, String rawBody, HttpServletRequest request) {
        String paymentId = webhookRequest.getPaymentId();
        String eventType = webhookRequest.getEvent();

        logger.info("Processing webhook - payment: {}, event: {}", paymentId, eventType);

        if (!isValidEventType(eventType)) {
            throw new IllegalArgumentException("Invalid event type: " + eventType +
                    ". Must be 'payment.succeeded' or 'payment.failed'");
        }

        String eventId = generateEventId(request, webhookRequest);

        // Fetch existing event if duplicate
        WebhookEvent existingEvent = getDuplicateEvent(paymentId, eventType);

        if (existingEvent != null) {
            if (existingEvent.getRawPayload().equals(rawBody)) {
                logger.info("Duplicate webhook with same payload detected - returning same response");
                return; // idempotent: same payload, do nothing
            } else {
                logger.warn("Duplicate webhook with different payload detected - returning 409 Conflict");
                throw new WebhookConflictException("Duplicate webhook with different payload for payment: " + paymentId);
            }
        }

        paymentService.processWebhook(paymentId, eventType);

        recordWebhookEvent(eventId, paymentId, eventType, rawBody);

        logger.info("Completed webhook processing - payment: {}, event: {}", paymentId, eventType);
    }

    private WebhookEvent getDuplicateEvent(String paymentId, String eventType) {
        // Strategy 1: by paymentId
        WebhookEvent byPaymentId = webhookEventRepository.findByPaymentId(paymentId);
        if (byPaymentId != null) {
            return byPaymentId;
        }

        // Strategy 2: by business key (paymentId + eventType)
        WebhookEvent byBusinessKey = webhookEventRepository.findByPaymentIdAndEventType(paymentId, eventType);
        if (byBusinessKey != null) {
            return byBusinessKey;
        }

        return null;
    }

    /**
     * Check if webhook event type is valid
     */
    private boolean isValidEventType(String eventType) {
        return EVENT_SUCCEEDED.equals(eventType) || EVENT_FAILED.equals(eventType);
    }

    /**
     * Generate unique event ID for deduplication
     */
    private String generateEventId(HttpServletRequest request, WebhookRequest webhookRequest) {
        // Try to get event ID from PSP headers
        String pspEventId = request.getHeader("X-Request-Id");
        if (pspEventId != null && !pspEventId.trim().isEmpty()) {
            return "psp_" + pspEventId;
        }

        // Fallback: generate deterministic ID from content + timestamp
        return "psp_" + webhookRequest.getPaymentId() + "_" + webhookRequest.getEvent() + "_" +
                System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6);
    }

    public boolean isDuplicateWebhook(String eventId, String paymentId, String eventType) {
        // Strategy 1: Payment ID-based deduplication
        if (eventId != null && webhookEventRepository.existsByPaymentId(paymentId)) {
            logger.debug("Duplicate detected by payment ID: {}", eventId);
            return true;
        }

        // Strategy 2: Business key-based deduplication (paymentId + eventType)
        if (webhookEventRepository.existsByPaymentIdAndEventType(paymentId, eventType)) {
            logger.debug("Duplicate detected by business key - payment: {}, event: {}", paymentId, eventType);
            return true;
        }

        return false;
    }

    /**
     * Record webhook event in database for audit and deduplication
     */
    @Transactional
    public void recordWebhookEvent(String eventId, String paymentId, String eventType, String rawPayload) {
        WebhookEvent event = WebhookEvent.builder()
                .eventId(eventId)
                .paymentId(paymentId)
                .eventType(eventType)
                .rawPayload(rawPayload)
                .build();
        webhookEventRepository.save(event);
        logger.debug("Recorded webhook event - ID: {}, payment: {}, event: {}", eventId, paymentId, eventType);
    }

}
package com.onafriq.paylite.service.paylite_service.controller;


import com.onafriq.paylite.service.paylite_service.dto.PaymentRequest;
import com.onafriq.paylite.service.paylite_service.dto.PaymentResponse;
import com.onafriq.paylite.service.paylite_service.exception.BadRequestException;
import com.onafriq.paylite.service.paylite_service.exception.ErrorResponse;
import com.onafriq.paylite.service.paylite_service.exception.RateLimitExceedeException;
import com.onafriq.paylite.service.paylite_service.exception.UnauthorizedException;
import com.onafriq.paylite.service.paylite_service.service.IdempotencyService;
import com.onafriq.paylite.service.paylite_service.service.PaymentService;
import com.onafriq.paylite.service.paylite_service.service.RateLimiterService;
import com.onafriq.paylite.service.paylite_service.service.SecurityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.SQLTransientException;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;
    private final SecurityService securityService;
    private final ObjectMapper objectMapper;
    @Autowired
    private RateLimiterService rateLimiter;

    public PaymentController(PaymentService paymentService,
                             IdempotencyService idempotencyService,
                             SecurityService securityService,
                             ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.idempotencyService = idempotencyService;
        this.securityService = securityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<?> createPayment(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest request,
            HttpServletRequest httpRequest) throws Exception {

        logger.warn("Starting create payment: {}", httpRequest.getRemoteAddr());
        // Validate API key
        if (!securityService.isValidApiKey(apiKey)) {
            logger.warn("Unauthorized API key attempt from: {}", httpRequest.getRemoteAddr());
            throw new UnauthorizedException("Unauthorized access. Please check your credentials.");
        }

        // Validate idempotency key
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new BadRequestException("Idempotency-Key header is required");
        }

        if (!rateLimiter.allowRequest(httpRequest.getRemoteAddr())) {
            long resetTime = rateLimiter.getResetTime(httpRequest.getRemoteAddr());
            throw new RateLimitExceedeException("Rate limit exceeded. Try again later");
        }

        PaymentResponse response = paymentService.createPayment(request, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<?> getPayment(
            @RequestHeader("X-API-Key") String apiKey,
            @PathVariable String paymentId,
            HttpServletRequest httpRequest) throws SQLTransientException {

        // Validate API key
        if (!securityService.isValidApiKey(apiKey)) {
            logger.warn("Unauthorized API key attempt from: {}", httpRequest.getRemoteAddr());
            throw new UnauthorizedException("Unauthorized access. Please check your credentials.");
        }
        PaymentResponse response = paymentService.getPayment(paymentId);
        return ResponseEntity.ok(response);

    }
}
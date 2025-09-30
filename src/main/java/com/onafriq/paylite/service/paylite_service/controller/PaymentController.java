package com.onafriq.paylite.service.paylite_service.controller;


import com.onafriq.paylite.service.paylite_service.dto.PaymentRequest;
import com.onafriq.paylite.service.paylite_service.dto.PaymentResponse;
import com.onafriq.paylite.service.paylite_service.service.IdempotencyService;
import com.onafriq.paylite.service.paylite_service.service.PaymentService;
import com.onafriq.paylite.service.paylite_service.service.SecurityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);
    
    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;
    private final SecurityService securityService;
    private final ObjectMapper objectMapper;
    
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
            HttpServletRequest httpRequest) {

        logger.warn("Starting create payment: {}", httpRequest.getRemoteAddr());
        // Validate API key
        if (!securityService.isValidApiKey(apiKey)) {
            logger.warn("Unauthorized API key attempt from: {}", httpRequest.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        // Validate idempotency key
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Idempotency-Key header is required");
        }
        
        try {
            // Calculate request hash for idempotency check
            String requestHash = idempotencyService.calculateRequestHash(request);
            
            // Check for existing idempotency key
            var existingResponse = idempotencyService.getExistingResponse(idempotencyKey, requestHash);
            if (existingResponse.isPresent()) {
                logger.info("Returning cached response for idempotency key: {}", idempotencyKey);
                PaymentResponse response = objectMapper.readValue(existingResponse.get(), PaymentResponse.class);
                return ResponseEntity.ok(response);
            }
            
            // Check if idempotency key exists with different request (conflict)
            if (idempotencyService.getExistingResponse(idempotencyKey, "different").isPresent()) {
                logger.warn("Idempotency key conflict for key: {}", idempotencyKey);
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Idempotency-Key already used with different request");
            }
            // Create new payment
            PaymentResponse response = paymentService.createPayment(request);
            
            // Store idempotency key with response
            String responseBody = objectMapper.writeValueAsString(response);
            idempotencyService.storeIdempotencyKey(idempotencyKey, requestHash, responseBody, response.getPaymentId());
            
            logger.info("Created new payment with ID: {}", response.getPaymentId());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error creating payment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error creating payment");
        }
    }
    
    @GetMapping("/{paymentId}")
    public ResponseEntity<?> getPayment(
            @RequestHeader("X-API-Key") String apiKey,
            @PathVariable String paymentId,
            HttpServletRequest httpRequest) {
        
        // Validate API key
        if (!securityService.isValidApiKey(apiKey)) {
            logger.warn("Unauthorized API key attempt from: {}", httpRequest.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        try {
            PaymentResponse response = paymentService.getPayment(paymentId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            logger.warn("Payment not found: {}", paymentId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error retrieving payment: {}", paymentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving payment");
        }
    }
}
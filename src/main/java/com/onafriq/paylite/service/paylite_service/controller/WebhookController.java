package com.onafriq.paylite.service.paylite_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onafriq.paylite.service.paylite_service.dto.WebhookRequest;
import com.onafriq.paylite.service.paylite_service.service.PaymentService;
import com.onafriq.paylite.service.paylite_service.service.SecurityService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {
    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);

    private static final String SIGNATURE_HEADER = "X-PSP-Signature";

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private SecurityService securityService;

    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping("/psp")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String rawBody,
            HttpServletRequest httpRequest) {

        String signature = httpRequest.getHeader(SIGNATURE_HEADER);

        logger.info("Received webhook with raw body: {}", rawBody);

        // Validate signature
        if (!securityService.verifyWebhookSignature(signature, rawBody)) {
            logger.warn("Invalid webhook signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            // Parse JSON using ObjectMapper
            WebhookRequest request = objectMapper.readValue(rawBody, WebhookRequest.class);

            if (request.getPaymentId() == null || request.getPaymentId().isEmpty() ||
                    request.getEvent() == null || request.getEvent().isEmpty()) {
                logger.warn("Invalid webhook payload - missing required fields");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            logger.info("Processing webhook for payment: {}, event: {}", request.getPaymentId(), request.getEvent());

            paymentService.processWebhook(request.getPaymentId(), request.getEvent());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Error processing webhook", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    private String getRawRequestBody(HttpServletRequest request) {
        try {
            BufferedReader reader = request.getReader();
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            logger.error("Error reading request body", e);
            return "";
        }
    }
}
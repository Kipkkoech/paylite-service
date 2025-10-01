package com.onafriq.paylite.service.paylite_service.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onafriq.paylite.service.paylite_service.dto.WebhookRequest;
import com.onafriq.paylite.service.paylite_service.exception.InvalidWebhookPayloadException;
import com.onafriq.paylite.service.paylite_service.exception.PaymentIdGenerationException;
import com.onafriq.paylite.service.paylite_service.exception.PaymentNotFoundException;
import com.onafriq.paylite.service.paylite_service.exception.UnauthorizedException;
import com.onafriq.paylite.service.paylite_service.service.PaymentService;
import com.onafriq.paylite.service.paylite_service.service.SecurityService;
import com.onafriq.paylite.service.paylite_service.service.WebhookService;
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
    private WebhookService webhookService;

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
            throw new UnauthorizedException("Invalid webhook signature");
        }

        WebhookRequest request =new WebhookRequest();
        try {
            // Parse JSON using ObjectMapper
            request= objectMapper.readValue(rawBody, WebhookRequest.class);
            logger.info("Processing webhook for payment: {}, event: {}", request.getPaymentId(), request.getEvent());

            webhookService.processWebhook(request, rawBody, httpRequest);
            return ResponseEntity.ok().build();
        } catch (InvalidWebhookPayloadException | JsonProcessingException e) {
            logger.error("Error processing webhook", e);
            throw new InvalidWebhookPayloadException("Invalid JSON payload", e);
        }
        catch (PaymentNotFoundException e) {
            logger.error("Error processing webhook", e);
            throw new PaymentNotFoundException(String.format("Payment with ID '%s' not found", request.getPaymentId()));
        }
    }

}
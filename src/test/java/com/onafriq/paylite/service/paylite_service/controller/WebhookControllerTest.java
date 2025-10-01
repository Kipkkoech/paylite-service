package com.onafriq.paylite.service.paylite_service.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onafriq.paylite.service.paylite_service.dto.WebhookRequest;
import com.onafriq.paylite.service.paylite_service.exception.InvalidWebhookPayloadException;
import com.onafriq.paylite.service.paylite_service.exception.PaymentNotFoundException;
import com.onafriq.paylite.service.paylite_service.exception.UnauthorizedException;
import com.onafriq.paylite.service.paylite_service.service.SecurityService;
import com.onafriq.paylite.service.paylite_service.service.WebhookService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebhookControllerTest {

    @Mock
    private WebhookService webhookService;

    @Mock
    private SecurityService securityService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private WebhookController webhookController;

    private final String rawBody = "{\"paymentId\":\"123\",\"event\":\"PAYMENT_SUCCESS\"}";
    private final String signature = "valid-signature";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void handleWebhook_ShouldReturnOk_WhenValidRequest() throws Exception {
        WebhookRequest request = new WebhookRequest();
        request.setPaymentId("123");
        request.setEvent("PAYMENT_SUCCESS");

        when(httpRequest.getHeader("X-PSP-Signature")).thenReturn(signature);
        when(securityService.verifyWebhookSignature(signature, rawBody)).thenReturn(true);
        when(objectMapper.readValue(rawBody, WebhookRequest.class)).thenReturn(request);

        ResponseEntity<Void> response = webhookController.handleWebhook(rawBody, httpRequest);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        verify(webhookService, times(1)).processWebhook(eq(request), eq(rawBody), eq(httpRequest));
    }

    @Test
    void handleWebhook_ShouldThrowUnauthorized_WhenSignatureInvalid() {
        when(httpRequest.getHeader("X-PSP-Signature")).thenReturn("bad-signature");
        when(securityService.verifyWebhookSignature(anyString(), eq(rawBody))).thenReturn(false);

        assertThrows(UnauthorizedException.class, () ->
                webhookController.handleWebhook(rawBody, httpRequest)
        );

        verify(webhookService, never()).processWebhook(any(), anyString(), any());
    }

    @Test
    void handleWebhook_ShouldThrowInvalidPayload_WhenJsonInvalid() throws Exception {
        when(httpRequest.getHeader("X-PSP-Signature")).thenReturn(signature);
        when(securityService.verifyWebhookSignature(signature, rawBody)).thenReturn(true);
        when(objectMapper.readValue(rawBody, WebhookRequest.class))
                .thenThrow(new JsonProcessingException("Invalid JSON") {});

        assertThrows(InvalidWebhookPayloadException.class, () ->
                webhookController.handleWebhook(rawBody, httpRequest)
        );

        verify(webhookService, never()).processWebhook(any(), anyString(), any());
    }

    @Test
    void handleWebhook_ShouldThrowPaymentNotFound_WhenServiceThrows() throws Exception {
        WebhookRequest request = new WebhookRequest();
        request.setPaymentId("missing-id");
        request.setEvent("PAYMENT_FAILED");

        when(httpRequest.getHeader("X-PSP-Signature")).thenReturn(signature);
        when(securityService.verifyWebhookSignature(signature, rawBody)).thenReturn(true);
        when(objectMapper.readValue(rawBody, WebhookRequest.class)).thenReturn(request);
        doThrow(new PaymentNotFoundException("Payment not found"))
                .when(webhookService).processWebhook(eq(request), eq(rawBody), eq(httpRequest));

        assertThrows(PaymentNotFoundException.class, () ->
                webhookController.handleWebhook(rawBody, httpRequest)
        );
    }
}

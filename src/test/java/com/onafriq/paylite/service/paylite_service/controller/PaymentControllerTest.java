package com.onafriq.paylite.service.paylite_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onafriq.paylite.service.paylite_service.dto.PaymentRequest;
import com.onafriq.paylite.service.paylite_service.dto.PaymentResponse;
import com.onafriq.paylite.service.paylite_service.exception.BadRequestException;
import com.onafriq.paylite.service.paylite_service.exception.RateLimitExceedeException;
import com.onafriq.paylite.service.paylite_service.exception.UnauthorizedException;
import com.onafriq.paylite.service.paylite_service.service.IdempotencyService;
import com.onafriq.paylite.service.paylite_service.service.PaymentService;
import com.onafriq.paylite.service.paylite_service.service.RateLimiterService;
import com.onafriq.paylite.service.paylite_service.service.SecurityService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLTransientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;
    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private SecurityService securityService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private RateLimiterService rateLimiter;
    @Mock
    private HttpServletRequest httpRequest;


    @InjectMocks
    private PaymentController paymentController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        paymentController = new PaymentController(paymentService, idempotencyService, securityService, objectMapper);
        ReflectionTestUtils.setField(paymentController, "rateLimiter", rateLimiter); // injects the mock
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Test
    void createPayment_ShouldReturnOk_WhenValidRequest() throws Exception {
        PaymentRequest request = new PaymentRequest();
        PaymentResponse response = new PaymentResponse();
        when(securityService.isValidApiKey("validKey")).thenReturn(true);
        when(rateLimiter.allowRequest("127.0.0.1")).thenReturn(true);
        when(paymentService.createPayment(any(), eq("idempotencyKey"))).thenReturn(response);

        ResponseEntity<?> result = paymentController.createPayment("validKey", "idempotencyKey", request, httpRequest);

        assertThat(result.getBody()).isEqualTo(response);
        verify(paymentService).createPayment(any(), eq("idempotencyKey"));
    }

    @Test
    void createPayment_ShouldThrowUnauthorized_WhenApiKeyInvalid() {
        PaymentRequest request = new PaymentRequest();
        when(securityService.isValidApiKey("badKey")).thenReturn(false);

        assertThrows(UnauthorizedException.class,
                () -> paymentController.createPayment("badKey", "idempotencyKey", request, httpRequest));
    }

    @Test
    void createPayment_ShouldThrowBadRequest_WhenIdempotencyKeyMissing() {
        PaymentRequest request = new PaymentRequest();
        when(securityService.isValidApiKey("validKey")).thenReturn(true);

        assertThrows(BadRequestException.class,
                () -> paymentController.createPayment("validKey", " ", request, httpRequest));
    }

    @Test
    void createPayment_ShouldThrowRateLimitExceeded_WhenRateLimitBreached() {
        PaymentRequest request = new PaymentRequest();
        when(securityService.isValidApiKey("validKey")).thenReturn(true);
        when(rateLimiter.allowRequest("127.0.0.1")).thenReturn(false);
        when(rateLimiter.getResetTime("127.0.0.1")).thenReturn(1000L);

        assertThrows(RateLimitExceedeException.class,
                () -> paymentController.createPayment("validKey", "idempotencyKey", request, httpRequest));
    }

    @Test
    void getPayment_ShouldReturnOk_WhenValidRequest() throws SQLTransientException {
        PaymentResponse response = new PaymentResponse();
        when(securityService.isValidApiKey("validKey")).thenReturn(true);
        when(paymentService.getPayment("payment123")).thenReturn(response);

        ResponseEntity<?> result = paymentController.getPayment("validKey", "payment123", httpRequest);

        assertThat(result.getBody()).isEqualTo(response);
        verify(paymentService).getPayment("payment123");
    }

    @Test
    void getPayment_ShouldThrowUnauthorized_WhenApiKeyInvalid() {
        when(securityService.isValidApiKey("badKey")).thenReturn(false);

        assertThrows(UnauthorizedException.class,
                () -> paymentController.getPayment("badKey", "payment123", httpRequest));
    }
}

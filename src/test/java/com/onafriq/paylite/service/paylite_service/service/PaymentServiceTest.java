package com.onafriq.paylite.service.paylite_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onafriq.paylite.service.paylite_service.dto.PaymentRequest;
import com.onafriq.paylite.service.paylite_service.dto.PaymentResponse;
import com.onafriq.paylite.service.paylite_service.entity.Payment;
import com.onafriq.paylite.service.paylite_service.enums.PaymentStatus;
import com.onafriq.paylite.service.paylite_service.exception.IdempotencyConflictException;
import com.onafriq.paylite.service.paylite_service.exception.PaymentIdGenerationException;
import com.onafriq.paylite.service.paylite_service.exception.PaymentNotFoundException;
import com.onafriq.paylite.service.paylite_service.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;

import java.sql.SQLTransientException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RetryTemplate retryTemplate;

    @InjectMocks
    private PaymentService paymentService;

    private PaymentRequest paymentRequest;
    private Payment payment;
    private PaymentResponse paymentResponse;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        paymentRequest = new PaymentRequest();
        paymentRequest.setAmount(1000L);
        paymentRequest.setCurrency("KES");
        paymentRequest.setCustomerEmail("test@example.com");
        paymentRequest.setReference("REF-001");

        payment = Payment.builder()
                .paymentId("pl_12345678")
                .amount(1000L)
                .currency("KES")
                .customerEmail("test@example.com")
                .reference("REF-001")
                .status(PaymentStatus.PENDING.toString())
                .build();

        paymentResponse = PaymentResponse.builder()
                .paymentId("pl_12345678")
                .status(PaymentStatus.PENDING.toString())
                .build();

        idempotencyKey = "idempotency-key-123";
    }

    // ===== CREATE PAYMENT TESTS =====

    @Test
    void createPayment_WithNewRequest_ShouldCreatePayment() throws JsonProcessingException {
        // Arrange
        when(idempotencyService.calculateRequestHash(any())).thenReturn("hash123");
        when(idempotencyService.getExistingResponse(anyString(), anyString())).thenReturn(Optional.empty());
        when(idempotencyService.hasConflict(anyString(), anyString())).thenReturn(false);
        when(paymentRepository.existsByPaymentId(anyString())).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        PaymentResponse result = paymentService.createPayment(paymentRequest, idempotencyKey);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getPaymentId());
        assertTrue(result.getPaymentId().startsWith("pl_"));
        assertEquals(PaymentStatus.PENDING.toString(), result.getStatus());

        verify(idempotencyService).calculateRequestHash(paymentRequest);
        verify(idempotencyService).getExistingResponse(idempotencyKey, "hash123");
        verify(idempotencyService).hasConflict(idempotencyKey, "hash123");
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void createPayment_WithExistingIdempotencyKey_ShouldReturnCachedResponse() throws JsonProcessingException {
        // Arrange
        String cachedResponseJson = "{\"paymentId\":\"pl_12345678\",\"status\":\"PENDING\"}";
        when(idempotencyService.calculateRequestHash(any())).thenReturn("hash123");
        when(idempotencyService.getExistingResponse(anyString(), anyString()))
                .thenReturn(Optional.of(cachedResponseJson));
        when(objectMapper.readValue(cachedResponseJson, PaymentResponse.class))
                .thenReturn(paymentResponse);

        // Act
        PaymentResponse result = paymentService.createPayment(paymentRequest, idempotencyKey);

        // Assert
        assertNotNull(result);
        assertEquals("pl_12345678", result.getPaymentId());
        assertEquals(PaymentStatus.PENDING.toString(), result.getStatus());

        verify(idempotencyService).calculateRequestHash(paymentRequest);
        verify(idempotencyService).getExistingResponse(idempotencyKey, "hash123");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createPayment_WithIdempotencyConflict_ShouldThrowException() throws JsonProcessingException {
        // Arrange
        when(idempotencyService.calculateRequestHash(any())).thenReturn("hash123");
        when(idempotencyService.getExistingResponse(anyString(), anyString())).thenReturn(Optional.empty());
        when(idempotencyService.hasConflict(anyString(), anyString())).thenReturn(true);

        // Act & Assert
        IdempotencyConflictException exception = assertThrows(
                IdempotencyConflictException.class,
                () -> paymentService.createPayment(paymentRequest, idempotencyKey)
        );

        assertEquals("Idempotency-Key already used with different request", exception.getMessage());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createPayment_WithPaymentIdCollision_ShouldRetryGeneration() throws JsonProcessingException {
        // Arrange
        when(idempotencyService.calculateRequestHash(any())).thenReturn("hash123");
        when(idempotencyService.getExistingResponse(anyString(), anyString())).thenReturn(Optional.empty());
        when(idempotencyService.hasConflict(anyString(), anyString())).thenReturn(false);
        
        // First 2 attempts find existing IDs, 3rd attempt succeeds
        when(paymentRepository.existsByPaymentId(anyString()))
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        PaymentResponse result = paymentService.createPayment(paymentRequest, idempotencyKey);

        // Assert
        assertNotNull(result);
        verify(paymentRepository, times(3)).existsByPaymentId(anyString());
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void createPayment_WithTooManyCollisions_ShouldThrowException() throws JsonProcessingException {
        // Arrange
        when(idempotencyService.calculateRequestHash(any())).thenReturn("hash123");
        when(idempotencyService.getExistingResponse(anyString(), anyString())).thenReturn(Optional.empty());
        when(idempotencyService.hasConflict(anyString(), anyString())).thenReturn(false);
        when(paymentRepository.existsByPaymentId(anyString())).thenReturn(true);

        // Act & Assert
        assertThrows(
                PaymentIdGenerationException.class,
                () -> paymentService.createPayment(paymentRequest, idempotencyKey)
        );

        verify(paymentRepository, times(5)).existsByPaymentId(anyString());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createPayment_ShouldSavePaymentWithCorrectFields() throws JsonProcessingException {
        // Arrange
        when(idempotencyService.calculateRequestHash(any())).thenReturn("hash123");
        when(idempotencyService.getExistingResponse(anyString(), anyString())).thenReturn(Optional.empty());
        when(idempotencyService.hasConflict(anyString(), anyString())).thenReturn(false);
        when(paymentRepository.existsByPaymentId(anyString())).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);

        // Act
        paymentService.createPayment(paymentRequest, idempotencyKey);

        // Assert
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();

        assertTrue(savedPayment.getPaymentId().startsWith("pl_"));
        assertEquals(1000L, savedPayment.getAmount());
        assertEquals("KES", savedPayment.getCurrency());
        assertEquals("test@example.com", savedPayment.getCustomerEmail());
        assertEquals("REF-001", savedPayment.getReference());
        assertEquals(PaymentStatus.PENDING.toString(), savedPayment.getStatus());
    }

    // ===== GET PAYMENT TESTS =====

    @Test
    void getPayment_WithValidId_ShouldReturnPayment() throws Exception {
        // Arrange
        when(retryTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RetryCallback<Object, Exception> callback =
                    (RetryCallback<Object, Exception>) invocation.getArgument(0);
            return callback.doWithRetry(null);
        });

        when(paymentRepository.findByPaymentId("pl_12345678")).thenReturn(Optional.of(payment));

        // Act
        PaymentResponse result = paymentService.getPayment("pl_12345678");

        // Assert
        assertNotNull(result);
        assertEquals("pl_12345678", result.getPaymentId());
        assertEquals(PaymentStatus.PENDING.toString(), result.getStatus());
        assertEquals(1000L, result.getAmount());
        assertEquals("KES", result.getCurrency());
        assertEquals("REF-001", result.getReference());
        assertEquals("test@example.com", result.getCustomerEmail());

        verify(paymentRepository).findByPaymentId("pl_12345678");
    }


    @Test
    void getPayment_WithInvalidId_ShouldThrowException() {
        when(retryTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RetryCallback<Object, Exception> callback =
                    (RetryCallback<Object, Exception>) invocation.getArgument(0);
            try {
                return callback.doWithRetry(null);
            } catch (SQLTransientException e) {
                // Simulate how your service should behave
                throw new PaymentNotFoundException("Payment not found for given ID", e);
            }
        });


        when(paymentRepository.findByPaymentId("invalid-id")).thenReturn(Optional.empty());

        // Act & Assert
        PaymentNotFoundException exception = assertThrows(
                PaymentNotFoundException.class,
                () -> paymentService.getPayment("invalid-id")
        );

        assertFalse(exception.getMessage().contains("Payment not found for given ID"));
        verify(paymentRepository).findByPaymentId("invalid-id");
    }


    // ===== PROCESS WEBHOOK TESTS =====

    @Test
    void processWebhook_WithSucceededEvent_ShouldUpdateStatusToSucceeded() {
        // Arrange
        when(retryTemplate.execute(any())).thenAnswer(invocation -> {
            return invocation.getArgument(0, org.springframework.retry.RetryCallback.class).doWithRetry(null);
        });
        when(paymentRepository.findByPaymentIdForUpdate("pl_12345678"))
                .thenReturn(Optional.of(payment));

        // Act
        paymentService.processWebhook("pl_12345678", "payment.succeeded");

        // Assert
        verify(paymentRepository).findByPaymentIdForUpdate("pl_12345678");
        verify(paymentRepository).save(argThat(p -> 
            PaymentStatus.SUCCEEDED.toString().equals(p.getStatus())
        ));
    }

    @Test
    void processWebhook_WithFailedEvent_ShouldUpdateStatusToFailed() {
        // Arrange
        when(retryTemplate.execute(any())).thenAnswer(invocation -> {
            return invocation.getArgument(0, org.springframework.retry.RetryCallback.class).doWithRetry(null);
        });
        when(paymentRepository.findByPaymentIdForUpdate("pl_12345678"))
                .thenReturn(Optional.of(payment));

        // Act
        paymentService.processWebhook("pl_12345678", "payment.failed");

        // Assert
        verify(paymentRepository).findByPaymentIdForUpdate("pl_12345678");
        verify(paymentRepository).save(argThat(p -> 
            PaymentStatus.FAILED.toString().equals(p.getStatus())
        ));
    }

    @Test
    void processWebhook_WithAlreadySucceededPayment_ShouldNotUpdate() {
        // Arrange
        payment.setStatus(PaymentStatus.SUCCEEDED.toString());
        when(retryTemplate.execute(any())).thenAnswer(invocation -> {
            return invocation.getArgument(0, org.springframework.retry.RetryCallback.class).doWithRetry(null);
        });
        when(paymentRepository.findByPaymentIdForUpdate("pl_12345678"))
                .thenReturn(Optional.of(payment));

        // Act
        paymentService.processWebhook("pl_12345678", "payment.succeeded");

        // Assert
        verify(paymentRepository).findByPaymentIdForUpdate("pl_12345678");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processWebhook_WithAlreadyFailedPayment_ShouldNotUpdate() {
        // Arrange
        payment.setStatus(PaymentStatus.FAILED.toString());
        when(retryTemplate.execute(any())).thenAnswer(invocation -> {
            return invocation.getArgument(0, org.springframework.retry.RetryCallback.class).doWithRetry(null);
        });
        when(paymentRepository.findByPaymentIdForUpdate("pl_12345678"))
                .thenReturn(Optional.of(payment));

        // Act
        paymentService.processWebhook("pl_12345678", "payment.failed");

        // Assert
        verify(paymentRepository).findByPaymentIdForUpdate("pl_12345678");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processWebhook_WithInvalidPaymentId_ShouldThrowException() {
        // Arrange
        when(retryTemplate.execute(any())).thenAnswer(invocation -> {
            return invocation.getArgument(0, org.springframework.retry.RetryCallback.class).doWithRetry(null);
        });
        when(paymentRepository.findByPaymentIdForUpdate("invalid-id"))
                .thenReturn(Optional.empty());

        // Act & Assert
        PaymentNotFoundException exception = assertThrows(
                PaymentNotFoundException.class,
                () -> paymentService.processWebhook("invalid-id", "payment.succeeded")
        );

        assertTrue(exception.getMessage().contains("invalid-id"));
        verify(paymentRepository).findByPaymentIdForUpdate("invalid-id");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processWebhook_WithUnknownEvent_ShouldSetFailedStatus() {
        // Arrange
        when(retryTemplate.execute(any())).thenAnswer(invocation -> {
            return invocation.getArgument(0, org.springframework.retry.RetryCallback.class).doWithRetry(null);
        });
        when(paymentRepository.findByPaymentIdForUpdate("pl_12345678"))
                .thenReturn(Optional.of(payment));

        // Act
        paymentService.processWebhook("pl_12345678", "payment.unknown");

        // Assert
        verify(paymentRepository).save(argThat(p -> 
            PaymentStatus.FAILED.toString().equals(p.getStatus())
        ));
    }
}
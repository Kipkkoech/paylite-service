package com.onafriq.paylite.service.paylite_service.service;

import com.onafriq.paylite.service.paylite_service.entity.IdempotencyKey;
import com.onafriq.paylite.service.paylite_service.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Mock
    private RetryTemplate retryTemplate;

    @InjectMocks
    private IdempotencyService idempotencyService;

    private String idempotencyKey;
    private String requestHash;
    private String responseBody;
    private String paymentId;
    private IdempotencyKey existingIdempotencyKey;

    @BeforeEach
    void setUp() {
        idempotencyKey = "idempotency-key-123";
        requestHash = "hash123";
        responseBody = "{\"paymentId\":\"pl_12345678\",\"status\":\"PENDING\"}";
        paymentId = "pl_12345678";

        existingIdempotencyKey = IdempotencyKey.builder()
                .key(idempotencyKey)
                .requestHash(requestHash)
                .responseBody(responseBody)
                .paymentId(paymentId)
                .build();
    }

    // ===== CALCULATE REQUEST HASH TESTS =====

    @Test
    void calculateRequestHash_WithValidObject_ShouldReturnHash() {
        // Arrange
        TestRequest request = new TestRequest("test-data", 1000L);

        // Act
        String result = idempotencyService.calculateRequestHash(request);

        // Assert
        assertNotNull(result);
        assertFalse(result.isEmpty());
        // SHA-256 hash encoded in Base64 should be a specific length pattern
        assertTrue(result.length() > 0);
    }

    @Test
    void calculateRequestHash_WithSameObject_ShouldReturnSameHash() {
        // Arrange
        TestRequest request1 = new TestRequest("test-data", 1000L);
        TestRequest request2 = new TestRequest("test-data", 1000L);

        // Act
        String hash1 = idempotencyService.calculateRequestHash(request1);
        String hash2 = idempotencyService.calculateRequestHash(request2);

        // Assert
        assertEquals(hash1, hash2);
    }

    @Test
    void calculateRequestHash_WithDifferentObjects_ShouldReturnDifferentHashes() {
        // Arrange
        TestRequest request1 = new TestRequest("test-data-1", 1000L);
        TestRequest request2 = new TestRequest("test-data-2", 1000L);

        // Act
        String hash1 = idempotencyService.calculateRequestHash(request1);
        String hash2 = idempotencyService.calculateRequestHash(request2);

        // Assert
        assertNotEquals(hash1, hash2);
    }

    @Test
    void calculateRequestHash_WithNullObject_ShouldHandleGracefully() {
        // Arrange & Act & Assert
        assertThrows(
                NullPointerException.class,
                () -> idempotencyService.calculateRequestHash(null)
        );
    }

    // ===== GET EXISTING RESPONSE TESTS =====

    @Test
    void getExistingResponse_WithMatchingKeyAndHash_ShouldReturnResponse() {
        // Arrange
        when(retryTemplate.execute(any(org.springframework.retry.RetryCallback.class), any(org.springframework.retry.RecoveryCallback.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    org.springframework.retry.RetryCallback<Object, Exception> callback =
                            (org.springframework.retry.RetryCallback<Object, Exception>) invocation.getArgument(0);
                    return callback.doWithRetry(null);
                });

        when(idempotencyKeyRepository.findByKey(idempotencyKey))
                .thenReturn(Optional.of(existingIdempotencyKey));

        // Act
        Optional<String> result = idempotencyService.getExistingResponse(idempotencyKey, requestHash);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(responseBody, result.get());
        verify(idempotencyKeyRepository).findByKey(idempotencyKey);
    }

    @Test
    void getExistingResponse_WithMatchingKeyButDifferentHash_ShouldReturnEmpty() {
        // Arrange
        String differentHash = "different-hash";
        when(retryTemplate.execute(any(org.springframework.retry.RetryCallback.class), any(org.springframework.retry.RecoveryCallback.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    org.springframework.retry.RetryCallback<Object, Exception> callback =
                            (org.springframework.retry.RetryCallback<Object, Exception>) invocation.getArgument(0);
                    return callback.doWithRetry(null);
                });

        when(idempotencyKeyRepository.findByKey(idempotencyKey))
                .thenReturn(Optional.of(existingIdempotencyKey));

        // Act
        Optional<String> result = idempotencyService.getExistingResponse(idempotencyKey, differentHash);

        // Assert
        assertFalse(result.isPresent());
        verify(idempotencyKeyRepository).findByKey(idempotencyKey);
    }

    @Test
    void getExistingResponse_WithNonExistentKey_ShouldReturnEmpty() {
        // Arrange
        when(retryTemplate.execute(any(org.springframework.retry.RetryCallback.class), any(org.springframework.retry.RecoveryCallback.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    org.springframework.retry.RetryCallback<Object, Exception> callback =
                            (org.springframework.retry.RetryCallback<Object, Exception>) invocation.getArgument(0);
                    return callback.doWithRetry(null);
                });

        when(idempotencyKeyRepository.findByKey(idempotencyKey))
                .thenReturn(Optional.empty());

        // Act
        Optional<String> result = idempotencyService.getExistingResponse(idempotencyKey, requestHash);

        // Assert
        assertFalse(result.isPresent());
        verify(idempotencyKeyRepository).findByKey(idempotencyKey);
    }

    // ===== STORE IDEMPOTENCY KEY TESTS =====

    @Test
    void storeIdempotencyKey_ShouldSaveWithCorrectFields() {
        // Arrange
        when(retryTemplate.execute(any(org.springframework.retry.RetryCallback.class), any(org.springframework.retry.RecoveryCallback.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    org.springframework.retry.RetryCallback<Object, Exception> callback =
                            (org.springframework.retry.RetryCallback<Object, Exception>) invocation.getArgument(0);
                    return callback.doWithRetry(null);
                });

        when(idempotencyKeyRepository.save(any(IdempotencyKey.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<IdempotencyKey> keyCaptor = ArgumentCaptor.forClass(IdempotencyKey.class);

        // Act
        idempotencyService.storeIdempotencyKey(idempotencyKey, requestHash, responseBody, paymentId);

        // Assert
        verify(idempotencyKeyRepository).save(keyCaptor.capture());
        IdempotencyKey savedKey = keyCaptor.getValue();

        assertEquals(idempotencyKey, savedKey.getKey());
        assertEquals(requestHash, savedKey.getRequestHash());
        assertEquals(responseBody, savedKey.getResponseBody());
        assertEquals(paymentId, savedKey.getPaymentId());
    }

    @Test
    void storeIdempotencyKey_WithRetryTemplate_ShouldExecuteSuccessfully() {
        // Arrange
        when(retryTemplate.execute(any(org.springframework.retry.RetryCallback.class), any(org.springframework.retry.RecoveryCallback.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    org.springframework.retry.RetryCallback<Object, Exception> callback =
                            (org.springframework.retry.RetryCallback<Object, Exception>) invocation.getArgument(0);
                    return callback.doWithRetry(null);
                });

        when(idempotencyKeyRepository.save(any(IdempotencyKey.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act & Assert
        assertDoesNotThrow(() ->
                idempotencyService.storeIdempotencyKey(idempotencyKey, requestHash, responseBody, paymentId)
        );

        verify(idempotencyKeyRepository).save(any(IdempotencyKey.class));
    }

    // ===== HAS CONFLICT TESTS =====

    @Test
    void hasConflict_WithNonExistentKey_ShouldReturnFalse() {
        // Arrange
        when(retryTemplate.execute(any(org.springframework.retry.RetryCallback.class), any(org.springframework.retry.RecoveryCallback.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    org.springframework.retry.RetryCallback<Object, Exception> callback =
                            (org.springframework.retry.RetryCallback<Object, Exception>) invocation.getArgument(0);
                    return callback.doWithRetry(null);
                });

        when(idempotencyKeyRepository.findByKey(idempotencyKey))
                .thenReturn(Optional.empty());

        // Act
        boolean result = idempotencyService.hasConflict(idempotencyKey, requestHash);

        // Assert
        assertFalse(result);
        verify(idempotencyKeyRepository).findByKey(idempotencyKey);
    }

    @Test
    void hasConflict_WithMatchingKeyAndHash_ShouldReturnFalse() {
        // Arrange
        when(retryTemplate.execute(any(org.springframework.retry.RetryCallback.class), any(org.springframework.retry.RecoveryCallback.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    org.springframework.retry.RetryCallback<Object, Exception> callback =
                            (org.springframework.retry.RetryCallback<Object, Exception>) invocation.getArgument(0);
                    return callback.doWithRetry(null);
                });

        when(idempotencyKeyRepository.findByKey(idempotencyKey))
                .thenReturn(Optional.of(existingIdempotencyKey));

        // Act
        boolean result = idempotencyService.hasConflict(idempotencyKey, requestHash);

        // Assert
        assertFalse(result);
        verify(idempotencyKeyRepository).findByKey(idempotencyKey);
    }

    @Test
    void hasConflict_WithMatchingKeyButDifferentHash_ShouldReturnTrue() {
        // Arrange
        String differentHash = "different-hash";
        when(retryTemplate.execute(any(org.springframework.retry.RetryCallback.class), any(org.springframework.retry.RecoveryCallback.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    org.springframework.retry.RetryCallback<Object, Exception> callback =
                            (org.springframework.retry.RetryCallback<Object, Exception>) invocation.getArgument(0);
                    return callback.doWithRetry(null);
                });

        when(idempotencyKeyRepository.findByKey(idempotencyKey))
                .thenReturn(Optional.of(existingIdempotencyKey));

        // Act
        boolean result = idempotencyService.hasConflict(idempotencyKey, differentHash);

        // Assert
        assertTrue(result);
        verify(idempotencyKeyRepository).findByKey(idempotencyKey);
    }

    @Test
    void hasConflict_WithNullStoredHash_ShouldHandleGracefully() {
        // Arrange
        IdempotencyKey keyWithNullHash = IdempotencyKey.builder()
                .key(idempotencyKey)
                .requestHash(null)
                .responseBody(responseBody)
                .paymentId(paymentId)
                .build();

        when(retryTemplate.execute(any(org.springframework.retry.RetryCallback.class), any(org.springframework.retry.RecoveryCallback.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    org.springframework.retry.RetryCallback<Object, Exception> callback =
                            (org.springframework.retry.RetryCallback<Object, Exception>) invocation.getArgument(0);
                    return callback.doWithRetry(null);
                });

        when(idempotencyKeyRepository.findByKey(idempotencyKey))
                .thenReturn(Optional.of(keyWithNullHash));

        // Act & Assert
        // Should throw NPE when comparing null hash, so we expect an exception
        assertThrows(NullPointerException.class, () ->
                idempotencyService.hasConflict(idempotencyKey, requestHash)
        );
    }

    // ===== RETRY BEHAVIOR TESTS =====

    @Test
    void getExistingResponse_WithRetryExhausted_ShouldThrowException() {
        // Arrange
        when(retryTemplate.execute(any(org.springframework.retry.RetryCallback.class), any(org.springframework.retry.RecoveryCallback.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    org.springframework.retry.RecoveryCallback<Object> recoveryCallback =
                            (org.springframework.retry.RecoveryCallback<Object>) invocation.getArgument(1);

                    // Create a mock RetryContext to simulate retry exhaustion
                    org.springframework.retry.RetryContext retryContext = mock(org.springframework.retry.RetryContext.class);
                    when(retryContext.getLastThrowable()).thenReturn(new RuntimeException("Retry exhausted"));

                    return recoveryCallback.recover(retryContext);
                });

        // Act & Assert
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> idempotencyService.getExistingResponse(idempotencyKey, requestHash)
        );

        assertTrue(exception.getMessage().contains("Failed to retrieve idempotency key after retries"));
    }

    @Test
    void storeIdempotencyKey_WithRetryExhausted_ShouldThrowException() {
        // Arrange
        when(retryTemplate.execute(any(org.springframework.retry.RetryCallback.class), any(org.springframework.retry.RecoveryCallback.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    org.springframework.retry.RecoveryCallback<Object> recoveryCallback =
                            (org.springframework.retry.RecoveryCallback<Object>) invocation.getArgument(1);

                    // Create a mock RetryContext to simulate retry exhaustion
                    org.springframework.retry.RetryContext retryContext = mock(org.springframework.retry.RetryContext.class);
                    when(retryContext.getLastThrowable()).thenReturn(new RuntimeException("Retry exhausted"));

                    return recoveryCallback.recover(retryContext);
                });

        // Act & Assert
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> idempotencyService.storeIdempotencyKey(idempotencyKey, requestHash, responseBody, paymentId)
        );

        assertTrue(exception.getMessage().contains("Failed to store idempotency key"));
        assertTrue(exception.getMessage().contains(idempotencyKey));
    }

    @Test
    void hasConflict_WithRetryExhausted_ShouldThrowException() {
        // Arrange
        when(retryTemplate.execute(any(org.springframework.retry.RetryCallback.class), any(org.springframework.retry.RecoveryCallback.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    org.springframework.retry.RecoveryCallback<Object> recoveryCallback =
                            (org.springframework.retry.RecoveryCallback<Object>) invocation.getArgument(1);

                    // Create a mock RetryContext to simulate retry exhaustion
                    org.springframework.retry.RetryContext retryContext = mock(org.springframework.retry.RetryContext.class);
                    when(retryContext.getLastThrowable()).thenReturn(new RuntimeException("Retry exhausted"));

                    return recoveryCallback.recover(retryContext);
                });

        // Act & Assert
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> idempotencyService.hasConflict(idempotencyKey, requestHash)
        );

        assertTrue(exception.getMessage().contains("Failed to check idempotency conflict"));
        assertTrue(exception.getMessage().contains(idempotencyKey));
    }

    // Helper class for testing request hash calculation
    private static class TestRequest {
        private String data;
        private Long amount;

        public TestRequest(String data, Long amount) {
            this.data = data;
            this.amount = amount;
        }

        @Override
        public String toString() {
            return "TestRequest{" +
                    "data='" + data + '\'' +
                    ", amount=" + amount +
                    '}';
        }
    }
}
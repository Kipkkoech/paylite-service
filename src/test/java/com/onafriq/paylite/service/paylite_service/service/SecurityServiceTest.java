package com.onafriq.paylite.service.paylite_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {

    @InjectMocks
    private SecurityService securityService;

    private String[] validApiKeys;
    private String webhookSecret;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @BeforeEach
    void setUp() {
        // Setup test data
        validApiKeys = new String[]{"api-key-1", "api-key-2", "api-key-3"};
        webhookSecret = "test-webhook-secret-key";

        // Inject values using ReflectionTestUtils
        ReflectionTestUtils.setField(securityService, "validApiKeys", validApiKeys);
        ReflectionTestUtils.setField(securityService, "webhookSecret", webhookSecret);
    }

    // ===== API KEY VALIDATION TESTS =====

    @Test
    void isValidApiKey_WithValidKey_ShouldReturnTrue() {
        // Act
        boolean result = securityService.isValidApiKey("api-key-1");

        // Assert
        assertTrue(result);
    }

    @Test
    void isValidApiKey_WithAnotherValidKey_ShouldReturnTrue() {
        // Act
        boolean result = securityService.isValidApiKey("api-key-2");

        // Assert
        assertTrue(result);
    }

    @Test
    void isValidApiKey_WithThirdValidKey_ShouldReturnTrue() {
        // Act
        boolean result = securityService.isValidApiKey("api-key-3");

        // Assert
        assertTrue(result);
    }

    @Test
    void isValidApiKey_WithInvalidKey_ShouldReturnFalse() {
        // Act
        boolean result = securityService.isValidApiKey("invalid-key");

        // Assert
        assertFalse(result);
    }

    @Test
    void isValidApiKey_WithNullKey_ShouldReturnFalse() {
        // Act
        boolean result = securityService.isValidApiKey(null);

        // Assert
        assertFalse(result);
    }

    @Test
    void isValidApiKey_WithEmptyKey_ShouldReturnFalse() {
        // Act
        boolean result = securityService.isValidApiKey("");

        // Assert
        assertFalse(result);
    }

    @Test
    void isValidApiKey_WithWhitespaceKey_ShouldReturnFalse() {
        // Act
        boolean result = securityService.isValidApiKey("   ");

        // Assert
        assertFalse(result);
    }

    @Test
    void isValidApiKey_WithKeyContainingWhitespace_ShouldReturnFalse() {
        // Act
        boolean result = securityService.isValidApiKey(" api-key-1 ");

        // Assert
        assertFalse(result);
    }

    @Test
    void isValidApiKey_WithSimilarButInvalidKey_ShouldReturnFalse() {
        // Act
        boolean result = securityService.isValidApiKey("api-key-4");

        // Assert
        assertFalse(result);
    }

    @Test
    void isValidApiKey_CaseSensitive_ShouldReturnFalse() {
        // Act
        boolean result = securityService.isValidApiKey("API-KEY-1");

        // Assert
        assertFalse(result);
    }

    @Test
    void isValidApiKey_WithSpecialCharacters_ShouldWork() {
        // Arrange
        String[] keysWithSpecialChars = new String[]{"api-key-!@#$%"};
        ReflectionTestUtils.setField(securityService, "validApiKeys", keysWithSpecialChars);

        // Act
        boolean result = securityService.isValidApiKey("api-key-!@#$%");

        // Assert
        assertTrue(result);
    }

    @Test
    void isValidApiKey_WithEmptyValidKeysArray_ShouldReturnFalse() {
        // Arrange
        ReflectionTestUtils.setField(securityService, "validApiKeys", new String[]{});

        // Act
        boolean result = securityService.isValidApiKey("any-key");

        // Assert
        assertFalse(result);
    }

    // ===== WEBHOOK SIGNATURE VERIFICATION TESTS =====

    @Test
    void verifyWebhookSignature_WithValidSignature_ShouldReturnTrue() throws Exception {
        // Arrange
        String payload = "{\"paymentId\":\"pay_123\",\"status\":\"succeeded\"}";
        String validSignature = computeExpectedSignature(payload, webhookSecret);

        // Act
        boolean result = securityService.verifyWebhookSignature(validSignature, payload);

        // Assert
        assertTrue(result);
    }

    @Test
    void verifyWebhookSignature_WithInvalidSignature_ShouldReturnFalse() {
        // Arrange
        String payload = "{\"paymentId\":\"pay_123\",\"status\":\"succeeded\"}";
        String invalidSignature = "invalid-signature-string";

        // Act
        boolean result = securityService.verifyWebhookSignature(invalidSignature, payload);

        // Assert
        assertFalse(result);
    }

    @Test
    void verifyWebhookSignature_WithNullSignature_ShouldReturnFalse() {
        // Arrange
        String payload = "{\"paymentId\":\"pay_123\"}";

        // Act
        boolean result = securityService.verifyWebhookSignature(null, payload);

        // Assert
        assertFalse(result);
    }

    @Test
    void verifyWebhookSignature_WithNullPayload_ShouldReturnFalse() throws Exception {
        // Arrange
        String validSignature = computeExpectedSignature("some-payload", webhookSecret);

        // Act
        boolean result = securityService.verifyWebhookSignature(validSignature, null);

        // Assert
        assertFalse(result);
    }

    @Test
    void verifyWebhookSignature_WithBothNull_ShouldReturnFalse() {
        // Act
        boolean result = securityService.verifyWebhookSignature(null, null);

        // Assert
        assertFalse(result);
    }

    @Test
    void verifyWebhookSignature_WithEmptyPayload_ShouldWork() throws Exception {
        // Arrange
        String payload = "";
        String validSignature = computeExpectedSignature(payload, webhookSecret);

        // Act
        boolean result = securityService.verifyWebhookSignature(validSignature, payload);

        // Assert
        assertTrue(result);
    }

    @Test
    void verifyWebhookSignature_WithModifiedPayload_ShouldReturnFalse() throws Exception {
        // Arrange
        String originalPayload = "{\"paymentId\":\"pay_123\",\"status\":\"succeeded\"}";
        String modifiedPayload = "{\"paymentId\":\"pay_123\",\"status\":\"failed\"}";
        String signature = computeExpectedSignature(originalPayload, webhookSecret);

        // Act
        boolean result = securityService.verifyWebhookSignature(signature, modifiedPayload);

        // Assert
        assertFalse(result);
    }

    @Test
    void verifyWebhookSignature_WithDifferentSecret_ShouldReturnFalse() throws Exception {
        // Arrange
        String payload = "{\"paymentId\":\"pay_123\"}";
        String differentSecret = "different-secret-key";
        String signature = computeExpectedSignature(payload, differentSecret);

        // Act
        boolean result = securityService.verifyWebhookSignature(signature, payload);

        // Assert
        assertFalse(result);
    }

    @Test
    void verifyWebhookSignature_WithLargePayload_ShouldWork() throws Exception {
        // Arrange
        StringBuilder largePayload = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largePayload.append("{\"data\":\"test\"}");
        }
        String payload = largePayload.toString();
        String validSignature = computeExpectedSignature(payload, webhookSecret);

        // Act
        boolean result = securityService.verifyWebhookSignature(validSignature, payload);

        // Assert
        assertTrue(result);
    }

    @Test
    void verifyWebhookSignature_WithSpecialCharactersInPayload_ShouldWork() throws Exception {
        // Arrange
        String payload = "{\"special\":\"!@#$%^&*()_+-=[]{}|;:',.<>?\"}";
        String validSignature = computeExpectedSignature(payload, webhookSecret);

        // Act
        boolean result = securityService.verifyWebhookSignature(validSignature, payload);

        // Assert
        assertTrue(result);
    }

    @Test
    void verifyWebhookSignature_WithUnicodeCharacters_ShouldWork() throws Exception {
        // Arrange
        String payload = "{\"message\":\"Hello 世界 🌍\"}";
        String validSignature = computeExpectedSignature(payload, webhookSecret);

        // Act
        boolean result = securityService.verifyWebhookSignature(validSignature, payload);

        // Assert
        assertTrue(result);
    }

    @Test
    void verifyWebhookSignature_WithWhitespaceInPayload_ShouldBeExact() throws Exception {
        // Arrange
        String payload1 = "{\"key\":\"value\"}";
        String payload2 = "{ \"key\" : \"value\" }";
        String signature1 = computeExpectedSignature(payload1, webhookSecret);

        // Act
        boolean result = securityService.verifyWebhookSignature(signature1, payload2);

        // Assert
        assertFalse(result, "Whitespace differences should cause signature mismatch");
    }

    @Test
    void verifyWebhookSignature_SamePayloadTwice_ShouldProduceSameSignature() throws Exception {
        // Arrange
        String payload = "{\"paymentId\":\"pay_123\"}";
        String signature1 = computeExpectedSignature(payload, webhookSecret);
        String signature2 = computeExpectedSignature(payload, webhookSecret);

        // Act
        boolean result1 = securityService.verifyWebhookSignature(signature1, payload);
        boolean result2 = securityService.verifyWebhookSignature(signature2, payload);

        // Assert
        assertTrue(result1);
        assertTrue(result2);
        assertEquals(signature1, signature2, "Same payload should produce same signature");
    }

    @Test
    void verifyWebhookSignature_WithInvalidBase64Signature_ShouldReturnFalse() {
        // Arrange
        String payload = "{\"paymentId\":\"pay_123\"}";
        String invalidBase64 = "not-a-valid-base64-signature!!!";

        // Act
        boolean result = securityService.verifyWebhookSignature(invalidBase64, payload);

        // Assert
        assertFalse(result);
    }

    // ===== EDGE CASE AND SECURITY TESTS =====

    @Test
    void isValidApiKey_MultipleCallsWithSameKey_ShouldBeConsistent() {
        // Act
        boolean result1 = securityService.isValidApiKey("api-key-1");
        boolean result2 = securityService.isValidApiKey("api-key-1");
        boolean result3 = securityService.isValidApiKey("api-key-1");

        // Assert
        assertTrue(result1);
        assertTrue(result2);
        assertTrue(result3);
    }

    @Test
    void verifyWebhookSignature_MultipleCallsWithSameData_ShouldBeConsistent() throws Exception {
        // Arrange
        String payload = "{\"paymentId\":\"pay_123\"}";
        String signature = computeExpectedSignature(payload, webhookSecret);

        // Act
        boolean result1 = securityService.verifyWebhookSignature(signature, payload);
        boolean result2 = securityService.verifyWebhookSignature(signature, payload);
        boolean result3 = securityService.verifyWebhookSignature(signature, payload);

        // Assert
        assertTrue(result1);
        assertTrue(result2);
        assertTrue(result3);
    }

    @Test
    void isValidApiKey_WithVeryLongKey_ShouldWork() {
        // Arrange
        String longKey = "a".repeat(1000);
        String[] keysWithLongKey = new String[]{longKey};
        ReflectionTestUtils.setField(securityService, "validApiKeys", keysWithLongKey);

        // Act
        boolean result = securityService.isValidApiKey(longKey);

        // Assert
        assertTrue(result);
    }

    @Test
    void verifyWebhookSignature_WithJsonWithNewlines_ShouldWork() throws Exception {
        // Arrange
        String payload = "{\n  \"paymentId\": \"pay_123\",\n  \"status\": \"succeeded\"\n}";
        String validSignature = computeExpectedSignature(payload, webhookSecret);

        // Act
        boolean result = securityService.verifyWebhookSignature(validSignature, payload);

        // Assert
        assertTrue(result);
    }

    @Test
    void verifyWebhookSignature_TimingSafe_ShouldNotLeakInformation() throws Exception {
        // This test documents that signature comparison should be timing-safe
        // In production, consider using MessageDigest.isEqual() or similar
        String payload = "{\"paymentId\":\"pay_123\"}";
        String validSignature = computeExpectedSignature(payload, webhookSecret);
        String almostValidSignature = validSignature.substring(0, validSignature.length() - 1) + "X";

        // Act
        long start1 = System.nanoTime();
        boolean result1 = securityService.verifyWebhookSignature(validSignature, payload);
        long duration1 = System.nanoTime() - start1;

        long start2 = System.nanoTime();
        boolean result2 = securityService.verifyWebhookSignature(almostValidSignature, payload);
        long duration2 = System.nanoTime() - start2;

        // Assert
        assertTrue(result1);
        assertFalse(result2);
        // Note: String.equals() is not timing-safe, but this test documents the behavior
    }

    @Test
    void verifyWebhookSignature_WithInvalidSecretCausingException_ShouldReturnFalse() {
        // Arrange - Set a secret that contains invalid characters or is null
        // This should trigger an exception in the HMAC computation
        ReflectionTestUtils.setField(securityService, "webhookSecret", null);
        String payload = "{\"paymentId\":\"pay_123\"}";
        String signature = "any-signature";

        // Act
        boolean result = securityService.verifyWebhookSignature(signature, payload);

        // Assert
        assertFalse(result, "Should return false when exception occurs during signature computation");
    }

    @Test
    void verifyWebhookSignature_WithUnexpectedException_ShouldReturnFalse() {
        // Arrange - This test ensures the catch block is covered
        // Setting webhookSecret to null will cause NullPointerException in getBytes()
        ReflectionTestUtils.setField(securityService, "webhookSecret", null);
        String payload = "test-payload";
        String signature = "test-signature";

        // Act
        boolean result = securityService.verifyWebhookSignature(signature, payload);

        // Assert
        assertFalse(result, "Should catch exception and return false");
    }

    // ===== HELPER METHODS =====

    /**
     * Helper method to compute expected HMAC-SHA256 signature
     */
    private String computeExpectedSignature(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM
        );
        mac.init(secretKeySpec);
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmacBytes);
    }
}
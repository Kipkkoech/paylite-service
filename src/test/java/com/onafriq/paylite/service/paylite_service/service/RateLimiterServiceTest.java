package com.onafriq.paylite.service.paylite_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @InjectMocks
    private RateLimiterService rateLimiterService;

    private static final String CLIENT_ID = "client-123";
    private static final String CLIENT_ID_2 = "client-456";

    // These should match your AppConstants values
    // UPDATE THESE TO MATCH YOUR ACTUAL AppConstants!
    private static final int MAX_REQUESTS = 5;
    private static final long WINDOW_SIZE_SECONDS = 60;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService();
    }

    // ===== ALLOW REQUEST TESTS =====

    @Test
    void allowRequest_FirstRequest_ShouldReturnTrue() {
        // Act
        boolean result = rateLimiterService.allowRequest(CLIENT_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    void allowRequest_WithinLimit_ShouldReturnTrue() {
        // Act & Assert
        for (int i = 0; i < MAX_REQUESTS; i++) {
            assertTrue(rateLimiterService.allowRequest(CLIENT_ID),
                    "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    void allowRequest_ExceedingLimit_ShouldReturnFalse() {
        // Arrange - Use up all allowed requests
        for (int i = 0; i < MAX_REQUESTS; i++) {
            rateLimiterService.allowRequest(CLIENT_ID);
        }

        // Act - Try one more request
        boolean result = rateLimiterService.allowRequest(CLIENT_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    void allowRequest_AtExactLimit_ShouldReturnFalseForNextRequest() {
        // Arrange
        for (int i = 0; i < MAX_REQUESTS; i++) {
            assertTrue(rateLimiterService.allowRequest(CLIENT_ID));
        }

        // Act
        boolean result = rateLimiterService.allowRequest(CLIENT_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    void allowRequest_DifferentClients_ShouldHaveSeparateLimits() {
        // Arrange - Client 1 uses all requests
        for (int i = 0; i < MAX_REQUESTS; i++) {
            rateLimiterService.allowRequest(CLIENT_ID);
        }

        // Act - Client 2 should still be able to make requests
        boolean result = rateLimiterService.allowRequest(CLIENT_ID_2);

        // Assert
        assertTrue(result);
    }

    @Test
    void allowRequest_MultipleClients_ShouldMaintainSeparateBuckets() {
        // Act
        boolean client1Result1 = rateLimiterService.allowRequest(CLIENT_ID);
        boolean client2Result1 = rateLimiterService.allowRequest(CLIENT_ID_2);
        boolean client1Result2 = rateLimiterService.allowRequest(CLIENT_ID);
        boolean client2Result2 = rateLimiterService.allowRequest(CLIENT_ID_2);

        // Assert
        assertTrue(client1Result1);
        assertTrue(client2Result1);
        assertTrue(client1Result2);
        assertTrue(client2Result2);
    }

    @Test
    void allowRequest_AfterWindowExpiry_ShouldResetAndAllow() throws Exception {
        // Arrange - Exhaust the limit
        for (int i = 0; i < MAX_REQUESTS; i++) {
            rateLimiterService.allowRequest(CLIENT_ID);
        }
        assertFalse(rateLimiterService.allowRequest(CLIENT_ID));

        // Simulate window expiry by manipulating the bucket's windowStart
        // This requires reflection to access the internal state
        simulateWindowExpiry(CLIENT_ID);

        // Act
        boolean result = rateLimiterService.allowRequest(CLIENT_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    void allowRequest_PartiallyThroughWindow_ShouldMaintainCount() {
        // Arrange
        int halfLimit = MAX_REQUESTS / 2;
        for (int i = 0; i < halfLimit; i++) {
            rateLimiterService.allowRequest(CLIENT_ID);
        }

        // Act - Should still allow more requests
        boolean result = rateLimiterService.allowRequest(CLIENT_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    void allowRequest_WithNullClientId_ShouldHandleGracefully() {
        // Act & Assert - Will throw NPE as the service doesn't handle null
        // This test documents the current behavior
        assertThrows(NullPointerException.class, () ->
                rateLimiterService.allowRequest(null)
        );
    }

    @Test
    void allowRequest_WithEmptyClientId_ShouldWork() {
        // Act
        boolean result = rateLimiterService.allowRequest("");

        // Assert
        assertTrue(result);
    }

    // ===== GET RESET TIME TESTS =====

    @Test
    void getResetTime_WithExistingClient_ShouldReturnFutureTime() {
        // Arrange
        rateLimiterService.allowRequest(CLIENT_ID);
        long currentTime = Instant.now().getEpochSecond();

        // Act
        long resetTime = rateLimiterService.getResetTime(CLIENT_ID);

        // Assert
        assertTrue(resetTime > currentTime);
        assertTrue(resetTime <= currentTime + WINDOW_SIZE_SECONDS + 1); // +1 for timing tolerance
    }

    @Test
    void getResetTime_WithNonExistentClient_ShouldReturnCurrentTime() {
        // Arrange
        long beforeTime = Instant.now().getEpochSecond();

        // Act
        long resetTime = rateLimiterService.getResetTime("non-existent-client");

        // Assert
        long afterTime = Instant.now().getEpochSecond();
        assertTrue(resetTime >= beforeTime);
        assertTrue(resetTime <= afterTime + 1); // +1 for timing tolerance
    }

    @Test
    void getResetTime_AfterMultipleRequests_ShouldRemainConstant() {
        // Arrange
        rateLimiterService.allowRequest(CLIENT_ID);
        long firstResetTime = rateLimiterService.getResetTime(CLIENT_ID);

        // Act
        rateLimiterService.allowRequest(CLIENT_ID);
        rateLimiterService.allowRequest(CLIENT_ID);
        long secondResetTime = rateLimiterService.getResetTime(CLIENT_ID);

        // Assert
        assertEquals(firstResetTime, secondResetTime);
    }

    @Test
    void getResetTime_ShouldMatchWindowStart() {
        // Arrange
        rateLimiterService.allowRequest(CLIENT_ID);
        long resetTime = rateLimiterService.getResetTime(CLIENT_ID);
        long currentTime = Instant.now().getEpochSecond();

        // Assert
        long expectedResetTime = resetTime - currentTime;
        assertTrue(expectedResetTime <= WINDOW_SIZE_SECONDS);
        assertTrue(expectedResetTime >= 0);
    }

    // ===== CONCURRENT ACCESS TESTS =====

    @Test
    void allowRequest_ConcurrentRequests_ShouldRespectLimit() throws InterruptedException {
        // Arrange
        int threadCount = 10;
        int requestsPerThread = 5; // More than enough to exceed limit
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // Act
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    for (int j = 1; j < requestsPerThread; j++) {
                        if (rateLimiterService.allowRequest(CLIENT_ID)) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads simultaneously
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert
        assertEquals(MAX_REQUESTS, successCount.get(),
                "Should allow exactly MAX_REQUESTS despite concurrent access");
    }

    @Test
    void allowRequest_MultipleConcurrentClients_ShouldIsolateLimits() throws InterruptedException {
        // Arrange
        String[] clientIds = {"client1", "client2", "client3"};
        int threadCount = clientIds.length;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ConcurrentHashMap<String, AtomicInteger> successCounts = new ConcurrentHashMap<>();

        for (String clientId : clientIds) {
            successCounts.put(clientId, new AtomicInteger(0));
        }

        // Act
        for (String clientId : clientIds) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    for (int j = 0; j < MAX_REQUESTS + 5; j++) {
                        if (rateLimiterService.allowRequest(clientId)) {
                            successCounts.get(clientId).incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads simultaneously
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert
        for (String clientId : clientIds) {
            assertEquals(MAX_REQUESTS, successCounts.get(clientId).get(),
                    "Each client should have exactly MAX_REQUESTS allowed");
        }
    }

    // ===== WINDOW RESET TESTS =====

    @Test
    void allowRequest_AfterWindowReset_ShouldAllowNewRequests() throws Exception {
        // Arrange - Exhaust limit
        for (int i = 0; i < MAX_REQUESTS; i++) {
            assertTrue(rateLimiterService.allowRequest(CLIENT_ID));
        }
        assertFalse(rateLimiterService.allowRequest(CLIENT_ID));

        // Simulate window expiry
        simulateWindowExpiry(CLIENT_ID);

        // Act - Should allow full quota again
        int newRequestCount = 0;
        for (int i = 0; i < MAX_REQUESTS; i++) {
            if (rateLimiterService.allowRequest(CLIENT_ID)) {
                newRequestCount++;
            }
        }

        // Assert
        assertEquals(MAX_REQUESTS, newRequestCount);
    }

    @Test
    void allowRequest_JustBeforeWindowExpiry_ShouldStillBlock() throws Exception {
        // Arrange
        for (int i = 0; i < MAX_REQUESTS; i++) {
            rateLimiterService.allowRequest(CLIENT_ID);
        }

        // Act - Still within window
        boolean result = rateLimiterService.allowRequest(CLIENT_ID);

        // Assert
        assertFalse(result);
    }

    // ===== EDGE CASE TESTS =====

    @Test
    void allowRequest_WithVeryLongClientId_ShouldWork() {
        // Arrange
        String longClientId = "a".repeat(1000);

        // Act
        boolean result = rateLimiterService.allowRequest(longClientId);

        // Assert
        assertTrue(result);
    }

    @Test
    void allowRequest_WithSpecialCharacters_ShouldWork() {
        // Arrange
        String specialClientId = "client-!@#$%^&*()_+-=[]{}|;:',.<>?";

        // Act
        boolean result = rateLimiterService.allowRequest(specialClientId);

        // Assert
        assertTrue(result);
    }

    @Test
    void allowRequest_SameClientMultipleTimes_ShouldIncrementCount() {
        // Act
        rateLimiterService.allowRequest(CLIENT_ID);
        rateLimiterService.allowRequest(CLIENT_ID);
        rateLimiterService.allowRequest(CLIENT_ID);

        // Assert - Should have consumed 3 requests
        int remainingRequests = 0;
        for (int i = 0; i < MAX_REQUESTS; i++) {
            if (rateLimiterService.allowRequest(CLIENT_ID)) {
                remainingRequests++;
            }
        }
        assertEquals(MAX_REQUESTS - 3, remainingRequests);
    }

    // ===== HELPER METHODS =====

    /**
     * Simulate window expiry by manipulating internal bucket state using reflection
     */
    private void simulateWindowExpiry(String clientId) throws Exception {
        Field bucketsField = RateLimiterService.class.getDeclaredField("buckets");
        bucketsField.setAccessible(true);

        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Object> buckets =
                (ConcurrentHashMap<String, Object>) bucketsField.get(rateLimiterService);

        Object bucket = buckets.get(clientId);
        if (bucket != null) {
            Field windowStartField = bucket.getClass().getDeclaredField("windowStart");
            windowStartField.setAccessible(true);
            // Set window start to past (more than WINDOW_SIZE_SECONDS ago)
            windowStartField.set(bucket, Instant.now().getEpochSecond() - WINDOW_SIZE_SECONDS - 1);
        }
    }
}
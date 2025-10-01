package com.onafriq.paylite.service.paylite_service.service;

import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Instant;

import static com.onafriq.paylite.service.paylite_service.config.AppConstants.MAX_REQUESTS;
import static com.onafriq.paylite.service.paylite_service.config.AppConstants.WINDOW_SIZE_SECONDS;

@Service
public class RateLimiterService {
    
    private final ConcurrentHashMap<String, UserBucket> buckets = new ConcurrentHashMap<>();
    
    public boolean allowRequest(String clientId) {
        buckets.putIfAbsent(clientId, new UserBucket());
        UserBucket bucket = buckets.get(clientId);
        
        long now = Instant.now().getEpochSecond();
        
        // Reset if window has passed
        if (now - bucket.windowStart >= WINDOW_SIZE_SECONDS) {
            bucket.reset(now);
        }
        
        // Check if under limit
        if (bucket.requestCount.get() < MAX_REQUESTS) {
            bucket.requestCount.incrementAndGet();
            return true;
        }
        
        return false;
    }
    
    public long getResetTime(String clientId) {
        UserBucket bucket = buckets.get(clientId);
        if (bucket == null) return Instant.now().getEpochSecond();
        return bucket.windowStart + WINDOW_SIZE_SECONDS;
    }
    
    private static class UserBucket {
        AtomicInteger requestCount = new AtomicInteger(0);
        long windowStart = Instant.now().getEpochSecond();
        
        void reset(long newWindowStart) {
            requestCount.set(0);
            windowStart = newWindowStart;
        }
    }
}
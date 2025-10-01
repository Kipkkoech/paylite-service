package com.onafriq.paylite.service.paylite_service.service;

import com.onafriq.paylite.service.paylite_service.entity.IdempotencyKey;
import com.onafriq.paylite.service.paylite_service.repository.IdempotencyKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;

@Service
public class IdempotencyService {
    private static final Logger logger = LoggerFactory.getLogger(IdempotencyService.class);
    
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final RetryTemplate retryTemplate;
    
    public IdempotencyService(IdempotencyKeyRepository idempotencyKeyRepository, RetryTemplate retryTemplate) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.retryTemplate = retryTemplate;
    }
    
    public String calculateRequestHash(Object request) {
        try {
            String requestString = request.toString();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(requestString.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to calculate request hash", e);
        }
    }
    
    @Transactional(readOnly = true)
    public Optional<String> getExistingResponse(String idempotencyKey, String requestHash) {
        return retryTemplate.execute(context -> {
            return idempotencyKeyRepository.findByKey(idempotencyKey)
                    .filter(storedKey -> storedKey.getRequestHash().equals(requestHash))
                    .map(IdempotencyKey::getResponseBody);
        }, context -> {
            // Recovery logic if retries exhausted
            throw new RuntimeException("Failed to retrieve idempotency key after retries", context.getLastThrowable());
        });
    }
    
    @Transactional
    public void storeIdempotencyKey(String idempotencyKey, String requestHash,
                                    String responseBody, String paymentId) {
        IdempotencyKey key = IdempotencyKey.builder()
                .key(idempotencyKey)
                .requestHash(requestHash)
                .responseBody(responseBody)
                .paymentId(paymentId)
                .build();

        retryTemplate.execute(context -> {
            idempotencyKeyRepository.save(key);
            logger.info("Stored idempotency key for payment: {}", paymentId);
            return null; // must return something since callback has return type
        }, context -> {
            throw new RuntimeException(
                    "Failed to store idempotency key [" + idempotencyKey + "] after retries",
                    context.getLastThrowable()
            );
        });
    }


    public boolean hasConflict(String idempotencyKey, String requestHash) {
        return retryTemplate.execute(context -> {
            Optional<IdempotencyKey> existingRecord =
                    idempotencyKeyRepository.findByKey(idempotencyKey);

            if (existingRecord.isEmpty()) {
                return false; // No conflict - key doesn't exist
            }

            IdempotencyKey record = existingRecord.get();

            // Conflict if hash is different
            return !record.getRequestHash().equals(requestHash);
        }, context -> {
            // Recovery callback if retries exhausted
            throw new RuntimeException(
                    "Failed to check idempotency conflict for key [" + idempotencyKey + "] after retries",
                    context.getLastThrowable()
            );
        });
    }

}
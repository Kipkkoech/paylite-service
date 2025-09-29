package com.onafriq.paylite.service.paylite_service.service;

import com.onafriq.paylite.service.paylite_service.entity.IdempotencyKey;
import com.onafriq.paylite.service.paylite_service.repository.IdempotencyKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    
    public IdempotencyService(IdempotencyKeyRepository idempotencyKeyRepository) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
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
    
    @Transactional
    public Optional<String> getExistingResponse(String idempotencyKey, String requestHash) {
        return idempotencyKeyRepository.findByKey(idempotencyKey)
                .filter(storedKey -> storedKey.getRequestHash().equals(requestHash))
                .map(IdempotencyKey::getResponseBody);
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
        idempotencyKeyRepository.save(key);
        logger.info("Stored idempotency key for payment: {}", paymentId);
    }
}
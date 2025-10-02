package com.onafriq.paylite.service.paylite_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import static com.onafriq.paylite.service.paylite_service.config.AppConstants.HMAC_ALGORITHM;
import static javax.xml.crypto.dsig.SignatureMethod.HMAC_SHA256;

@Service
public class SecurityService {
    private static final Logger logger = LoggerFactory.getLogger(SecurityService.class);
    
    @Value("${app.api-keys}")
    private String[] validApiKeys;
    
    @Value("${app.webhook-secret}")
    private String webhookSecret;

    private static final String HMAC_SHA256q = "HmacSHA256";
    
    public boolean isValidApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.warn("No api Key set");
            return false;
        }
        
        for (String validKey : validApiKeys) {
            logger.warn("set api key: {}", validKey);
            if (validKey.equals(apiKey)) {

                return true;
            }
        }
        
        logger.warn("Invalid API key attempted");
        return false;
    }
    
    public boolean verifyWebhookSignature(String signature, String payload) {
        if (signature == null || payload == null) {
            return false;
        }
        
        try {
            String computedSignature = computeHmacSha256(payload, webhookSecret);
            logger.info("computedSignature {}, received signature {}", computedSignature, signature );
            return computedSignature.equals(signature);
        } catch (Exception e) {
            logger.error("Error verifying webhook signature", e);
            return false;
        }
    }
    
    private String computeHmacSha256(String data, String secret) 
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        mac.init(secretKeySpec);
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmacBytes);
    }
}
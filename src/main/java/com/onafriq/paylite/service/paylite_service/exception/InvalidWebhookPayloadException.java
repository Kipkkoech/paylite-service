package com.onafriq.paylite.service.paylite_service.exception;

public class InvalidWebhookPayloadException extends RuntimeException {
    
    public InvalidWebhookPayloadException(String message) {
        super(message);
    }
    
    public InvalidWebhookPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
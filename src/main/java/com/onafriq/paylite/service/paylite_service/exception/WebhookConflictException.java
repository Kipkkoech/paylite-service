package com.onafriq.paylite.service.paylite_service.exception;

public class WebhookConflictException extends RuntimeException {
    public WebhookConflictException(String message) {
        super(message);
    }
}

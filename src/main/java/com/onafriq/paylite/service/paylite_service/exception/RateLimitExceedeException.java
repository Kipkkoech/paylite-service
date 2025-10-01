package com.onafriq.paylite.service.paylite_service.exception;

public class RateLimitExceedeException  extends RuntimeException {
    public RateLimitExceedeException(String message) {
        super(message);
    }

    public RateLimitExceedeException(String message, Throwable cause) {
        super(message, cause);
    }
}
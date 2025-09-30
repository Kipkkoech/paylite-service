package com.onafriq.paylite.service.paylite_service.exception;

public class PaymentIdGenerationException extends RuntimeException {
    
    public PaymentIdGenerationException(String message) {
        super(message);
    }
    
    public PaymentIdGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
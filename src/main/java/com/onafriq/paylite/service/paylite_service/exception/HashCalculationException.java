package com.onafriq.paylite.service.paylite_service.exception;

public class HashCalculationException extends RuntimeException {

    public HashCalculationException(String message) {
        super(message);
    }

    public HashCalculationException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.onafriq.paylite.service.paylite_service.enums;

public enum PaymentStatus
{
    PENDING("PENDING"),
    SUCCEEDED("SUCCEEDED"),
    FAILED("FAILED");

    private final String status;

    PaymentStatus(String status) {
        this.status = status;
    }
}
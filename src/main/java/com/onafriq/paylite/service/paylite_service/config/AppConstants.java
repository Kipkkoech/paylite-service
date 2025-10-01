package com.onafriq.paylite.service.paylite_service.config;

public final class AppConstants {

    private AppConstants() {

    }

    public static final String PAYMENT_ID_PREFIX = "pl_";

    public static final String WEBHOOK_EVENT_SUCCEEDED = "payment.succeeded";
    public static final String WEBHOOK_EVENT_FAILED = "payment.failed";

    // Security
    public static final String HMAC_ALGORITHM = "HmacSHA256";
    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String WEBHOOK_SIGNATURE_HEADER = "X-PSP-Signature";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    //    Rate limiting
    public static final int MAX_REQUESTS = 5; // requests per window
    public static final long WINDOW_SIZE_SECONDS = 60; // 1 minute window
}
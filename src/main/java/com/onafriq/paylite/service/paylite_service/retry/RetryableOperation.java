package com.onafriq.paylite.service.paylite_service.retry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Retryable(
    retryFor = {
        org.springframework.dao.TransientDataAccessException.class,
        org.springframework.dao.QueryTimeoutException.class,
        org.springframework.dao.CannotAcquireLockException.class,
        org.springframework.dao.DeadlockLoserDataAccessException.class,
        org.springframework.dao.TransientDataAccessResourceException.class,
        org.springframework.dao.ConcurrencyFailureException.class,
        java.sql.SQLTransientException.class
    },
    maxAttempts = 3,
    backoff = @Backoff(
        delay = 100,        // Initial delay: 100ms
        multiplier = 2.0,   // Exponential backoff multiplier
        maxDelay = 2000     // Max delay: 2 seconds
    )
)
public @interface RetryableOperation {
}
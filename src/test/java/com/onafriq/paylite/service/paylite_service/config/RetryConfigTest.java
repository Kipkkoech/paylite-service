package com.onafriq.paylite.service.paylite_service.config;

import com.onafriq.paylite.service.paylite_service.config.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.sql.SQLTransientException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryConfigTest {

    private RetryConfig retryConfig;
    private RetryTemplate retryTemplate;

    @BeforeEach
    void setUp() {
        retryConfig = new RetryConfig();
        retryTemplate = retryConfig.retryTemplate();
    }

    @Test
    void retryTemplate_ShouldBeConfigured() {
        // Verify bean creation
        assertThat(retryTemplate).isNotNull();

        // Execute a simple callback and check the result
        String result = retryTemplate.execute(context -> "ok");

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void shouldRetryOnTransientDataAccessException() {
        AtomicInteger attempts = new AtomicInteger();

        TransientDataAccessException thrown = assertThrows(TransientDataAccessException.class, () ->
                retryTemplate.execute(context -> {
                    attempts.incrementAndGet();
                    throw new TransientDataAccessException("transient") {};
                })
        );

        assertThat(thrown).isNotNull();
        assertThat(attempts.get()).isEqualTo(3); // 3 attempts total
    }

    @Test
    void shouldRetryOnSqlTransientException() {
        AtomicInteger attempts = new AtomicInteger();

        SQLTransientException thrown = assertThrows(SQLTransientException.class, () ->
                retryTemplate.execute(context -> {
                    attempts.incrementAndGet();
                    throw new SQLTransientException("sql transient");
                })
        );

        // Verify exception details
        assertThat(thrown.getMessage()).contains("sql transient");
        assertThat(attempts.get()).isEqualTo(3);
    }




    @Test
    void shouldRetryOnCannotAcquireLockException() {
        AtomicInteger attempts = new AtomicInteger();

        CannotAcquireLockException thrown = assertThrows(CannotAcquireLockException.class, () ->
                retryTemplate.execute(context -> {
                    attempts.incrementAndGet();
                    throw new CannotAcquireLockException("lock failure");
                })
        );

        assertThat(thrown).isNotNull();
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void shouldNotRetryOnNonRetryableException() {
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(IllegalArgumentException.class, () ->
            retryTemplate.execute(context -> {
                attempts.incrementAndGet();
                throw new IllegalArgumentException("not retryable");
            })
        );

        // Should fail immediately without retrying
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void backoffPolicy_ShouldBeExponential() {
        ExponentialBackOffPolicy backOffPolicy = retryConfig.exponentialBackOffPolicy();

        assertThat(backOffPolicy.getInitialInterval()).isEqualTo(100);
        assertThat(backOffPolicy.getMultiplier()).isEqualTo(2.0);
        assertThat(backOffPolicy.getMaxInterval()).isEqualTo(2000);
    }
}

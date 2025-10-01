package com.onafriq.paylite.service.paylite_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.sql.SQLTransientException;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class RetryConfig {

    private static final Logger log = LoggerFactory.getLogger(RetryConfig.class);

    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        retryTemplate.setRetryPolicy(retryPolicy());
        retryTemplate.setBackOffPolicy(exponentialBackOffPolicy());

        // Attach logging listener
        retryTemplate.registerListener(new RetryListener() {

            @Override
            public <T, E extends Throwable> boolean open(RetryContext context,
                                                         RetryCallback<T, E> callback) {
                log.info("Starting retry operation...");
                return true; // allow retries
            }

            @Override
            public <T, E extends Throwable> void close(RetryContext context,
                                                       RetryCallback<T, E> callback,
                                                       Throwable throwable) {
                if (throwable == null) {
                    log.info("Retry operation completed successfully after {} attempts",
                            context.getRetryCount());
                } else {
                    log.error("Retry operation failed after {} attempts. Last error: {}",
                            context.getRetryCount(), throwable.getMessage());
                }
            }

            @Override
            public <T, E extends Throwable> void onError(RetryContext context,
                                                         RetryCallback<T, E> callback,
                                                         Throwable throwable) {
                log.warn("Retry attempt {} failed due to: {}",
                        context.getRetryCount(), throwable.getMessage());
            }
        });

        return retryTemplate;
    }

    private RetryPolicy retryPolicy() {
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(TransientDataAccessException.class, true);
        retryableExceptions.put(SQLTransientException.class, true);

        retryableExceptions.put(org.springframework.dao.QueryTimeoutException.class, true);
        retryableExceptions.put(org.springframework.dao.CannotAcquireLockException.class, true);
        retryableExceptions.put(org.springframework.dao.DeadlockLoserDataAccessException.class, true);
        retryableExceptions.put(org.springframework.dao.TransientDataAccessResourceException.class, true);
        retryableExceptions.put(org.springframework.dao.ConcurrencyFailureException.class, true);

        return new SimpleRetryPolicy(3, retryableExceptions);
    }

   public ExponentialBackOffPolicy exponentialBackOffPolicy() {
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(100);
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(2000);
        return backOffPolicy;
    }
}

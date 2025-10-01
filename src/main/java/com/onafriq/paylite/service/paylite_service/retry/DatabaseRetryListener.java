package com.onafriq.paylite.service.paylite_service.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.stereotype.Component;

/**
 * Listener to log retry attempts for observability
 */
@Component
public class DatabaseRetryListener implements RetryListener {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseRetryListener.class);

    @Override
    public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
        // Called before the first attempt
        return true; // Continue with retry
    }

    @Override
    public <T, E extends Throwable> void onError(
            RetryContext context,
            RetryCallback<T, E> callback,
            Throwable throwable) {

        logger.warn("Retry attempt {} failed due to transient error: {} - {}",
                context.getRetryCount(),
                throwable.getClass().getSimpleName(),
                throwable.getMessage());
    }

    @Override
    public <T, E extends Throwable> void close(
            RetryContext context,
            RetryCallback<T, E> callback,
            Throwable throwable) {

        if (throwable != null) {
            logger.error("All retry attempts exhausted. Final error: {} - {}",
                    throwable.getClass().getSimpleName(),
                    throwable.getMessage());
        } else if (context.getRetryCount() > 0) {
            logger.info("Operation succeeded after {} retry attempts",
                    context.getRetryCount());
        }
    }
}
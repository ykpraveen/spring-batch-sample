package com.example.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.stereotype.Component;

/**
 * Custom SkipPolicy for batch processing
 * Skips records that encounter specific exceptions and logs them to dead letter queue
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BatchSkipPolicy implements SkipPolicy {
    
    private final ErrorLogger errorLogger;
    
    // Exceptions that can be skipped (business-level, recoverable)
    private static final Class<?>[] SKIPPABLE_EXCEPTIONS = {
        ValidationException.class,
        IllegalArgumentException.class,
        NumberFormatException.class,
        NullPointerException.class
    };
    
    @Override
    public boolean shouldSkip(Throwable throwable, long skipCount) throws SkipLimitExceededException {
        log.debug("Evaluating skip for {} (skipCount={})", throwable.getClass().getSimpleName(), skipCount);

        if (isSkippable(throwable)) {
            log.warn("Skipping record due to {}: {}", throwable.getClass().getSimpleName(), throwable.getMessage());
            return true;
        }
        
        log.error("Non-skippable exception encountered: {}", throwable.getMessage(), throwable);
        return false;
    }
    
    /**
     * Check if the exception is skippable
     */
    private boolean isSkippable(Throwable throwable) {
        for (Class<?> skippableException : SKIPPABLE_EXCEPTIONS) {
            if (skippableException.isInstance(throwable)) {
                return true;
            }
        }
        return false;
    }
}

package com.example.batch;

import com.example.entity.DeadLetterQueue;
import com.example.repository.DeadLetterQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;

/**
 * Service for logging errors to the dead letter queue
 * Provides centralized error tracking and recovery mechanism
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ErrorLogger {
    
    private final DeadLetterQueueRepository deadLetterQueueRepository;
    
    /**
     * Log a record that failed processing to the dead letter queue
     */
    @Transactional
    public void logFailedRecord(Long sourceId, String sourceTable, String errorMessage, 
                               Exception exception, String rawData, Integer partitionId, 
                               String stepName, Integer retryCount) {
        try {
            String stacktrace = getStacktrace(exception);
            String errorType = exception != null ? exception.getClass().getSimpleName() : "UNKNOWN";
            
            DeadLetterQueue dlq = DeadLetterQueue.builder()
                .sourceId(sourceId)
                .sourceTable(sourceTable)
                .errorMessage(errorMessage)
                .errorType(errorType)
                .errorStacktrace(stacktrace)
                .rawData(rawData)
                .partitionId(partitionId)
                .stepName(stepName)
                .failedAt(LocalDateTime.now())
                .retryCount(retryCount)
                .isResolved(false)
                .build();
            
            deadLetterQueueRepository.save(dlq);
            
            log.error("Record logged to dead letter queue - sourceId: {}, table: {}, partition: {}, error: {}",
                    sourceId, sourceTable, partitionId, errorType);
                    
        } catch (Exception e) {
            log.error("Failed to log error to dead letter queue", e);
        }
    }
    
    /**
     * Mark a record as resolved in the dead letter queue
     */
    @Transactional
    public void markAsResolved(Long dlqId, String resolutionNotes) {
        deadLetterQueueRepository.findById(dlqId).ifPresent(dlq -> {
            dlq.setIsResolved(true);
            dlq.setResolvedAt(LocalDateTime.now());
            dlq.setResolutionNotes(resolutionNotes);
            deadLetterQueueRepository.save(dlq);
            log.info("Dead letter queue record {} marked as resolved", dlqId);
        });
    }
    
    /**
     * Get full stacktrace from exception
     */
    private String getStacktrace(Exception exception) {
        if (exception == null) {
            return null;
        }
        
        StringWriter sw = new StringWriter();
        exception.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}

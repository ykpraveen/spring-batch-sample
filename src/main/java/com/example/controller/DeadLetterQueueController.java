package com.example.controller;

import com.example.entity.DeadLetterQueue;
import com.example.repository.DeadLetterQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for managing dead letter queue records
 * Provides endpoints to view and manage failed batch records
 */
@RestController
@RequestMapping("/api/batch/dlq")
@RequiredArgsConstructor
@Slf4j
public class DeadLetterQueueController {
    
    private final DeadLetterQueueRepository deadLetterQueueRepository;
    
    /**
     * Get all unresolved dead letter queue records
     */
    @GetMapping("/unresolved")
    public ResponseEntity<List<DeadLetterQueue>> getUnresolvedRecords() {
        List<DeadLetterQueue> records = deadLetterQueueRepository.findByIsResolvedFalse();
        return ResponseEntity.ok(records);
    }
    
    /**
     * Get all dead letter queue records (resolved and unresolved)
     */
    @GetMapping
    public ResponseEntity<List<DeadLetterQueue>> getAllRecords() {
        List<DeadLetterQueue> records = deadLetterQueueRepository.findAll();
        return ResponseEntity.ok(records);
    }
    
    /**
     * Get a specific dead letter queue record by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<DeadLetterQueue> getRecord(@PathVariable Long id) {
        return deadLetterQueueRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Get dead letter queue records by partition
     */
    @GetMapping("/partition/{partitionId}")
    public ResponseEntity<List<DeadLetterQueue>> getRecordsByPartition(@PathVariable Integer partitionId) {
        List<DeadLetterQueue> records = deadLetterQueueRepository.findByPartitionIdAndIsResolvedFalse(partitionId);
        return ResponseEntity.ok(records);
    }
    
    /**
     * Get dead letter queue records by step name
     */
    @GetMapping("/step/{stepName}")
    public ResponseEntity<List<DeadLetterQueue>> getRecordsByStep(@PathVariable String stepName) {
        List<DeadLetterQueue> records = deadLetterQueueRepository.findByStepNameAndIsResolvedFalse(stepName);
        return ResponseEntity.ok(records);
    }
    
    /**
     * Mark a record as resolved
     */
    @PostMapping("/{id}/resolve")
    public ResponseEntity<?> resolveRecord(@PathVariable Long id, 
                                           @RequestBody Map<String, String> request) {
        String resolutionNotes = request.getOrDefault("resolutionNotes", "");
        
        return deadLetterQueueRepository.findById(id)
                .map(dlq -> {
                    dlq.setIsResolved(true);
                    dlq.setResolutionNotes(resolutionNotes);
                    deadLetterQueueRepository.save(dlq);
                    log.info("Record {} marked as resolved with notes: {}", id, resolutionNotes);
                    return ResponseEntity.ok().body(dlq);
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Get DLQ statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long unresolvedCount = deadLetterQueueRepository.countByIsResolvedFalse();
        long totalCount = deadLetterQueueRepository.count();
        
        return ResponseEntity.ok(Map.of(
                "unresolvedCount", unresolvedCount,
                "totalCount", totalCount,
                "resolvedCount", totalCount - unresolvedCount
        ));
    }
    
    /**
     * Delete a resolved record from DLQ
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRecord(@PathVariable Long id) {
        return deadLetterQueueRepository.findById(id)
                .map(dlq -> {
                    if (!dlq.getIsResolved()) {
                        return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(Map.of("error", "Cannot delete unresolved records"));
                    }
                    deadLetterQueueRepository.deleteById(id);
                    log.info("Dead letter queue record {} deleted", id);
                    return ResponseEntity.ok().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}

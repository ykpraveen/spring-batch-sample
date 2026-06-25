package com.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Dead Letter Queue entity for storing records that failed processing
 * Enables recovery and audit trail for problematic data
 */
@Entity
@Table(name = "dead_letter_queue", indexes = {
    @Index(name = "idx_dlq_is_resolved", columnList = "is_resolved"),
    @Index(name = "idx_dlq_partition_id", columnList = "partition_id"),
    @Index(name = "idx_dlq_step_name", columnList = "step_name"),
    @Index(name = "idx_dlq_failed_at", columnList = "failed_at")
})
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeadLetterQueue {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Version
    private Long version;
    
    @Column(nullable = false)
    private Long sourceId;
    
    @Column(name = "source_table", nullable = false)
    private String sourceTable;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "error_type")
    private String errorType;
    
    @Column(name = "error_stacktrace", columnDefinition = "TEXT")
    private String errorStacktrace;
    
    @Column(name = "raw_data", columnDefinition = "TEXT")
    private String rawData;
    
    @Column(name = "partition_id")
    private Integer partitionId;
    
    @Column(name = "step_name")
    private String stepName;
    
    @Column(nullable = false)
    private LocalDateTime failedAt;
    
    @Column(name = "retry_count")
    private Integer retryCount;
    
    @Column(name = "is_resolved")
    private Boolean isResolved;
    
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
    
    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;
}

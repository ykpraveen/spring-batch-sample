package com.example.repository;

import com.example.entity.DeadLetterQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DeadLetterQueueRepository extends JpaRepository<DeadLetterQueue, Long> {
    
    /**
     * Find all unresolved records in the dead letter queue
     */
    List<DeadLetterQueue> findByIsResolvedFalse();
    
    /**
     * Find records from a specific partition that failed
     */
    List<DeadLetterQueue> findByPartitionIdAndIsResolvedFalse(Integer partitionId);
    
    /**
     * Find records that failed in a specific step
     */
    List<DeadLetterQueue> findByStepNameAndIsResolvedFalse(String stepName);
    
    /**
     * Find recently failed records
     */
    List<DeadLetterQueue> findByFailedAtAfterAndIsResolvedFalseOrderByFailedAtDesc(LocalDateTime afterTime);
    
    /**
     * Count unresolved errors
     */
    long countByIsResolvedFalse();
}

package com.example.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "processed_data", indexes = {
    @Index(name = "idx_processed_data_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_processed_data_source_id", columnList = "source_id"),
    @Index(name = "idx_processed_data_region", columnList = "region"),
    @Index(name = "idx_processed_data_partition_id", columnList = "partition_id")
})
@Getter
@Setter
@ToString
@NoArgsConstructor
public class ProcessedData {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Version
    private Long version;
    
    @Column(name = "source_id")
    private Long sourceId;
    
    @Column(name = "transaction_id")
    private String transactionId;
    
    @Column(name = "original_amount")
    private BigDecimal originalAmount;
    
    @Column(name = "processed_amount")
    private BigDecimal processedAmount;
    
    @Column(name = "tax_amount")
    private BigDecimal taxAmount;
    
    @Column(name = "discount_applied")
    private BigDecimal discountApplied;
    
    @Column(name = "region")
    private String region;
    
    @Column(name = "product_category")
    private String productCategory;
    
    @Column(name = "partition_id")
    private Integer partitionId;
    
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;
}

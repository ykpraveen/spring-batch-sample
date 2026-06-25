package com.example.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sales_data", indexes = {
    @Index(name = "idx_sales_data_processed_id", columnList = "processed, id"),
    @Index(name = "idx_sales_data_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_sales_data_region", columnList = "region"),
    @Index(name = "idx_sales_data_created_at", columnList = "created_at")
})
@Getter
@Setter
@ToString
@NoArgsConstructor
public class SalesData {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Version
    private Long version;
    
    @Column(name = "transaction_id")
    private String transactionId;
    
    @Column(name = "amount")
    private BigDecimal amount;
    
    @Column(name = "quantity")
    private Integer quantity;
    
    @Column(name = "region")
    private String region;
    
    @Column(name = "product_category")
    private String productCategory;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "processed")
    private boolean processed;
}

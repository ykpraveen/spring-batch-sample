package com.example.processor;

import com.example.batch.DataValidator;
import com.example.entity.ProcessedData;
import com.example.entity.SalesData;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class SalesDataProcessor implements ItemProcessor<SalesData, ProcessedData> {
    
    private final DataValidator dataValidator;
    private static final BigDecimal TAX_RATE = new BigDecimal("0.10");
    private static final BigDecimal BULK_DISCOUNT_THRESHOLD = new BigDecimal("1000");
    private static final BigDecimal BULK_DISCOUNT_RATE = new BigDecimal("0.05");

    @Override
    public ProcessedData process(SalesData salesData) throws Exception {
        log.debug("Processing record {} (txn={}, amount={}, qty={})",
                salesData.getId(), salesData.getTransactionId(),
                salesData.getAmount(), salesData.getQuantity());

        dataValidator.validate(salesData);

        long startTime = System.currentTimeMillis();
        
        ProcessedData processedData = new ProcessedData();
        processedData.setSourceId(salesData.getId());
        processedData.setTransactionId(salesData.getTransactionId());
        processedData.setOriginalAmount(salesData.getAmount());
        processedData.setRegion(salesData.getRegion());
        processedData.setProductCategory(salesData.getProductCategory());
        
        BigDecimal taxAmount = salesData.getAmount()
                .multiply(TAX_RATE)
                .setScale(2, RoundingMode.HALF_UP);
        processedData.setTaxAmount(taxAmount);
        
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (salesData.getAmount().compareTo(BULK_DISCOUNT_THRESHOLD) > 0) {
            discountAmount = salesData.getAmount()
                    .multiply(BULK_DISCOUNT_RATE)
                    .setScale(2, RoundingMode.HALF_UP);
        }
        processedData.setDiscountApplied(discountAmount);
        
        BigDecimal finalAmount = salesData.getAmount()
                .add(taxAmount)
                .subtract(discountAmount)
                .setScale(2, RoundingMode.HALF_UP);
        processedData.setProcessedAmount(finalAmount);
        
        processedData.setProcessedAt(LocalDateTime.now());
        long endTime = System.currentTimeMillis();
        processedData.setProcessingTimeMs(endTime - startTime);

        log.debug("Record {} processed: tax={}, discount={}, final={} ({}ms)",
                salesData.getId(), taxAmount, discountAmount, finalAmount, endTime - startTime);
        
        return processedData;
    }
}

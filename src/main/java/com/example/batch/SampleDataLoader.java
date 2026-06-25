package com.example.batch;

import com.example.entity.SalesData;
import com.example.repository.SalesDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
@Slf4j
public class SampleDataLoader implements CommandLineRunner {

    private final SalesDataRepository salesDataRepository;

    public SampleDataLoader(SalesDataRepository salesDataRepository) {
        this.salesDataRepository = salesDataRepository;
    }
    
    private static final String[] REGIONS = {"North", "South", "East", "West", "Central"};
    private static final String[] CATEGORIES = {"Electronics", "Clothing", "Home", "Sports", "Books"};
    private static final int SAMPLE_DATA_COUNT = 5000; // Generate 5000 sample records
    
    @Override
    public void run(String... args) throws Exception {
        long existingCount = salesDataRepository.count();
        
        if (existingCount == 0) {
            log.info("Generating {} sample sales records...", SAMPLE_DATA_COUNT);
            generateSampleData();
            log.info("Sample data generation completed!");
        } else {
            log.info("Sample data already exists. Skipping generation.");
        }
    }
    
    private void generateSampleData() {
        Random random = new Random();
        List<SalesData> salesDataList = new ArrayList<>();
        
        for (int i = 0; i < SAMPLE_DATA_COUNT; i++) {
            SalesData salesData = new SalesData();
            salesData.setTransactionId("TXN-" + System.nanoTime() + "-" + i);
            salesData.setAmount(generateRandomAmount(random));
            salesData.setQuantity(random.nextInt(100) + 1);
            salesData.setRegion(REGIONS[random.nextInt(REGIONS.length)]);
            salesData.setProductCategory(CATEGORIES[random.nextInt(CATEGORIES.length)]);
            salesData.setCreatedAt(LocalDateTime.now().minusDays(random.nextInt(30)));
            salesData.setProcessed(false);
            
            salesDataList.add(salesData);
            
            // Batch insert every 1000 records
            if ((i + 1) % 1000 == 0) {
                salesDataRepository.saveAll(salesDataList);
                salesDataList.clear();
                log.debug("Inserted {} records", i + 1);
            }
        }
        
        // Save remaining records
        if (!salesDataList.isEmpty()) {
            salesDataRepository.saveAll(salesDataList);
        }
    }
    
    private BigDecimal generateRandomAmount(Random random) {
        return new BigDecimal(random.nextDouble() * 10000).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}

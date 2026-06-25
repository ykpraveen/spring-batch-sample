package com.example.batch;

import org.springframework.context.annotation.Configuration;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;

@Configuration
@EnableBatchProcessing
public class BatchEnabledConfiguration {
    // Batch processing is now enabled
}

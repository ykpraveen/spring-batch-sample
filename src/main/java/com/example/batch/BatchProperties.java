package com.example.batch;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "batch")
public record BatchProperties(
        ThreadPool threadPool,
        Partitioning partitioning,
        Retry retry,
        Features features
) {
    public record ThreadPool(int corePoolSize, int maxPoolSize, int queueCapacity) {}
    public record Partitioning(int chunkSize, int pageSize, int gridSize, int partitionSize, int skipLimit) {}
    public record Retry(int retryLimit) {}
    public record Features(boolean metricsExport) {}
}

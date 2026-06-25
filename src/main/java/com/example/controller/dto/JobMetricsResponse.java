package com.example.controller.dto;

public record JobMetricsResponse(
        long jobId,
        String jobName,
        String status,
        long totalRecordsProcessed,
        long totalRecordsSkipped,
        long totalRecordsFailed,
        double recordsPerSecond,
        long estimatedRemainingTime,
        int completionPercentage,
        long currentMemoryUsage,
        long peakMemoryUsage,
        int activeThreadCount
) {
}

package com.example.controller.dto;

import java.time.LocalDateTime;
import java.util.List;

public record JobDetailsResponse(
        long jobId,
        long jobInstanceId,
        String jobName,
        String status,
        String exitCode,
        LocalDateTime startTime,
        LocalDateTime endTime,
        long duration,
        long totalReadCount,
        long totalWriteCount,
        long totalSkipCount,
        List<StepDetailsResponse> steps
) {
}

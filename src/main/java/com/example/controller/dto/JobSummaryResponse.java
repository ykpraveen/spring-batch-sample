package com.example.controller.dto;

import java.time.LocalDateTime;

public record JobSummaryResponse(
        String jobName,
        long jobInstanceCount,
        Long lastExecutionId,
        String lastStatus,
        String lastExitCode,
        boolean running,
        LocalDateTime startTime,
        LocalDateTime endTime
) {
}
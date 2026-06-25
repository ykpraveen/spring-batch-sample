package com.example.controller.dto;

import java.time.LocalDateTime;

public record JobExecutionHistoryResponse(
        long executionId,
        long jobInstanceId,
        String status,
        String exitCode,
        LocalDateTime startTime,
        LocalDateTime endTime,
        long readCount,
        long writeCount,
        long skipCount,
        long duration
) {
}

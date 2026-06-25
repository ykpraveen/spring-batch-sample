package com.example.controller.dto;

import java.time.LocalDateTime;

public record StepDetailsResponse(
        String stepName,
        String status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        long duration,
        long readCount,
        long writeCount,
        long skipCount,
        long commitCount,
        long rollbackCount
) {
}

package com.example.controller;

import com.example.batch.JobExecutionService;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.controller.dto.JobSummaryResponse;
import com.example.controller.dto.JobExecutionHistoryResponse;
import com.example.controller.dto.JobDetailsResponse;
import com.example.controller.dto.StepDetailsResponse;
import com.example.controller.dto.JobMetricsResponse;

import java.util.List;

@RestController
@RequestMapping("/api/batch")
@Slf4j
@Timed
public class BatchJobController {

    private final JobExecutionService jobExecutionService;

    public BatchJobController(JobExecutionService jobExecutionService) {
        this.jobExecutionService = jobExecutionService;
    }
    
    @PostMapping("/start")
    public ResponseEntity<String> startJob() {
        long start = System.currentTimeMillis();
        try {
            jobExecutionService.runDistributedProcessingJob();
            log.info("POST /api/batch/start completed in {}ms", System.currentTimeMillis() - start);
            return ResponseEntity.ok("Distributed processing job started successfully!");
        } catch (Exception e) {
            log.error("POST /api/batch/start failed in {}ms: {}", System.currentTimeMillis() - start, e.getMessage());
            return ResponseEntity.status(500).body("Failed to start job: " + e.getMessage());
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Batch application is running!");
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<JobSummaryResponse>> listJobs() {
        return ResponseEntity.ok(jobExecutionService.getJobSummaries());
    }

    @GetMapping("/jobs/{jobName}/executions")
    public ResponseEntity<List<JobExecutionHistoryResponse>> getJobExecutionHistory(
            @PathVariable String jobName) {
        return ResponseEntity.ok(jobExecutionService.getJobExecutionHistory(jobName));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<JobDetailsResponse> getJobDetails(@PathVariable Long jobId) {
        JobDetailsResponse jobDetails = jobExecutionService.getJobDetails(jobId);
        if (jobDetails == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(jobDetails);
    }

    @GetMapping("/jobs/{jobId}/steps/{stepName}")
    public ResponseEntity<StepDetailsResponse> getStepDetails(
            @PathVariable Long jobId,
            @PathVariable String stepName) {
        StepDetailsResponse stepDetails = jobExecutionService.getStepDetails(jobId, stepName);
        if (stepDetails == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(stepDetails);
    }

    @PostMapping("/jobs/{jobId}/pause")
    public ResponseEntity<String> pauseJob(@PathVariable Long jobId) {
        boolean success = jobExecutionService.pauseJob(jobId);
        if (success) {
            log.info("Job {} paused", jobId);
            return ResponseEntity.ok("Job " + jobId + " paused successfully");
        } else {
            log.warn("Failed to pause job {}", jobId);
            return ResponseEntity.badRequest().body("Failed to pause job " + jobId);
        }
    }

    @PostMapping("/jobs/{jobId}/resume")
    public ResponseEntity<String> resumeJob(@PathVariable Long jobId) {
        boolean success = jobExecutionService.resumeJob(jobId);
        if (success) {
            log.info("Job {} resumed", jobId);
            return ResponseEntity.ok("Job " + jobId + " resumed successfully");
        } else {
            log.warn("Failed to resume job {}", jobId);
            return ResponseEntity.badRequest().body("Failed to resume job " + jobId);
        }
    }

    @GetMapping("/jobs/{jobId}/metrics")
    public ResponseEntity<JobMetricsResponse> getJobMetrics(@PathVariable Long jobId) {
        JobMetricsResponse metrics = jobExecutionService.getJobMetrics(jobId);
        if (metrics == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(metrics);
    }
}

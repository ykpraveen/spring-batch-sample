package com.example.batch;

import com.example.controller.dto.JobSummaryResponse;
import com.example.controller.dto.JobExecutionHistoryResponse;
import com.example.controller.dto.JobDetailsResponse;
import com.example.controller.dto.StepDetailsResponse;
import com.example.controller.dto.JobMetricsResponse;
import com.example.monitoring.BatchMetricsService;
import com.example.repository.ProcessedDataRepository;
import com.example.repository.SalesDataRepository;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Service;
import java.util.List;
import java.time.LocalDateTime;

@Service
@Slf4j
public class JobExecutionService {

    private final JobRepository jobRepository;
    private final JobOperator jobOperator;
    private final Job distributedProcessingJob;
    private final SalesDataRepository salesDataRepository;
    private final ProcessedDataRepository processedDataRepository;
    private final BatchMetricsService metricsService;

    public JobExecutionService(JobRepository jobRepository,
                               JobOperator jobOperator,
                               Job distributedProcessingJob,
                               SalesDataRepository salesDataRepository,
                               ProcessedDataRepository processedDataRepository,
                               BatchMetricsService metricsService) {
        this.jobRepository = jobRepository;
        this.jobOperator = jobOperator;
        this.distributedProcessingJob = distributedProcessingJob;
        this.salesDataRepository = salesDataRepository;
        this.processedDataRepository = processedDataRepository;
        this.metricsService = metricsService;
    }

    public List<JobSummaryResponse> getJobSummaries() {
        return jobRepository.getJobNames().stream()
                .sorted()
                .map(this::toJobSummaryResponse)
                .toList();
    }

    public List<JobExecutionHistoryResponse> getJobExecutionHistory(String jobName) {
        JobInstance lastJobInstance = jobRepository.getLastJobInstance(jobName);
        if (lastJobInstance == null) {
            return List.of();
        }

        return jobRepository.getJobExecutions(lastJobInstance).stream()
                .map(execution -> {
                    long totalReadCount = execution.getStepExecutions().stream()
                            .mapToLong(StepExecution::getReadCount).sum();
                    long totalWriteCount = execution.getStepExecutions().stream()
                            .mapToLong(StepExecution::getWriteCount).sum();
                    long totalSkipCount = execution.getStepExecutions().stream()
                            .mapToLong(StepExecution::getSkipCount).sum();
                    
                    return new JobExecutionHistoryResponse(
                            execution.getId(),
                            execution.getJobInstance().getId(),
                            execution.getStatus().name(),
                            execution.getExitStatus() != null ? execution.getExitStatus().getExitCode() : "UNKNOWN",
                            execution.getStartTime(),
                            execution.getEndTime(),
                            totalReadCount,
                            totalWriteCount,
                            totalSkipCount,
                            calculateDuration(execution.getStartTime(), execution.getEndTime())
                    );
                })
                .sorted((a, b) -> Long.compare(b.executionId(), a.executionId()))
                .toList();
    }

    private long calculateDuration(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0L;
        }
        return java.time.temporal.ChronoUnit.MILLIS.between(start, end);
    }
    
    @Timed(value = "batch.job.run", description = "Time taken to run the distributed processing job")
    public void runDistributedProcessingJob() throws Exception {
        log.info("=".repeat(80));
        log.info("Starting Distributed Data Processing Job at {}", LocalDateTime.now());
        log.info("=".repeat(80));

        metricsService.recordJobStarted();
        long startTime = System.currentTimeMillis();

        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("runId", String.valueOf(System.currentTimeMillis()))
                    .addLocalDateTime("startTime", LocalDateTime.now())
                    .toJobParameters();

            JobExecution jobExecution = jobOperator.start(distributedProcessingJob, jobParameters);

            long elapsed = System.currentTimeMillis() - startTime;

            long totalRead = jobExecution.getStepExecutions().stream()
                    .mapToLong(StepExecution::getReadCount).sum();
            long totalSkip = jobExecution.getStepExecutions().stream()
                    .mapToLong(StepExecution::getSkipCount).sum();
            long totalWrite = jobExecution.getStepExecutions().stream()
                    .mapToLong(StepExecution::getWriteCount).sum();
            long totalCommit = jobExecution.getStepExecutions().stream()
                    .mapToLong(StepExecution::getCommitCount).sum();

            metricsService.recordRecordsProcessed(totalWrite);
            if (totalSkip > 0) {
                for (long i = 0; i < totalSkip; i++) metricsService.recordSkip();
            }

            if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
                metricsService.recordJobCompleted();
                metricsService.recordJobDuration(elapsed);
                logJobSummary(jobExecution, totalRead, totalWrite, totalSkip, totalCommit, elapsed);
            } else if (jobExecution.getStatus() == BatchStatus.FAILED) {
                metricsService.recordJobFailed();
                log.error("✗ Job FAILED!");
                logJobSummary(jobExecution, totalRead, totalWrite, totalSkip, totalCommit, elapsed);
            }

        } catch (Exception e) {
            metricsService.recordJobFailed();
            log.error("Job execution failed with exception: ", e);
            throw e;
        }
    }

    private void logJobSummary(JobExecution jobExecution, long totalRead, long totalWrite,
                                long totalSkip, long totalCommit, long elapsedMs) {
        long totalRecords = salesDataRepository.count();
        double throughput = elapsedMs > 0 ? (totalRead * 1000.0) / elapsedMs : 0.0;

        log.info("");
        log.info("===============================================");
        log.info("  BATCH JOB SUMMARY");
        log.info("===============================================");
        log.info("  Job Name:            {}", jobExecution.getJobInstance().getJobName());
        log.info("  Execution ID:        {}", jobExecution.getId());
        log.info("  Status:              {}", jobExecution.getStatus());
        log.info("  Exit Code:           {}", jobExecution.getExitStatus().getExitCode());
        log.info("-----------------------------------------------");
        log.info("  Total Records:       {}", totalRecords);
        log.info("  Read:                {}", totalRead);
        log.info("  Written:             {}", totalWrite);
        log.info("  Skipped:             {}", totalSkip);
        log.info("  Commits:             {}", totalCommit);
        log.info("-----------------------------------------------");
        log.info("  Duration:            {} ms ({} s)", elapsedMs, elapsedMs / 1000);
        log.info("  Throughput:          {} records/s", (long) throughput);
        log.info("===============================================");

        for (StepExecution step : jobExecution.getStepExecutions()) {
            long stepDuration = 0;
            if (step.getStartTime() != null && step.getEndTime() != null) {
                stepDuration = java.time.temporal.ChronoUnit.MILLIS.between(step.getStartTime(), step.getEndTime());
            }
            log.info("  Step '{}': {} (read={}, write={}, skip={}, {}s)",
                    step.getStepName(), step.getStatus(), step.getReadCount(),
                    step.getWriteCount(), step.getSkipCount(), stepDuration / 1000);
        }
        log.info("");
    }

    private JobSummaryResponse toJobSummaryResponse(String jobName) {
        long instanceCount = 0L;
        JobInstance lastJobInstance = jobRepository.getLastJobInstance(jobName);
        JobExecution lastExecution = null;

        try {
            instanceCount = jobRepository.getJobInstanceCount(jobName);
        } catch (Exception e) {
            log.debug("Unable to load job instance count for {}", jobName, e);
        }

        if (lastJobInstance != null) {
            lastExecution = jobRepository.getLastJobExecution(lastJobInstance);
        }

        return new JobSummaryResponse(
                jobName,
                instanceCount,
                lastExecution != null ? lastExecution.getId() : null,
                lastExecution != null ? lastExecution.getStatus().name() : "UNKNOWN",
                lastExecution != null && lastExecution.getExitStatus() != null
                        ? lastExecution.getExitStatus().getExitCode()
                        : "UNKNOWN",
                lastExecution != null && lastExecution.isRunning(),
                lastExecution != null ? lastExecution.getStartTime() : null,
                lastExecution != null ? lastExecution.getEndTime() : null
        );
    }

    public JobDetailsResponse getJobDetails(Long jobId) {
        JobExecution jobExecution = jobRepository.getJobExecution(jobId);
        
        if (jobExecution == null) {
            return null;
        }

        long totalReadCount = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getReadCount).sum();
        long totalWriteCount = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getWriteCount).sum();
        long totalSkipCount = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getSkipCount).sum();

        List<StepDetailsResponse> steps = jobExecution.getStepExecutions().stream()
                .map(this::toStepDetailsResponse)
                .toList();

        return new JobDetailsResponse(
                jobExecution.getId(),
                jobExecution.getJobInstance().getId(),
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getStatus().name(),
                jobExecution.getExitStatus() != null ? jobExecution.getExitStatus().getExitCode() : "UNKNOWN",
                jobExecution.getStartTime(),
                jobExecution.getEndTime(),
                calculateDuration(jobExecution.getStartTime(), jobExecution.getEndTime()),
                totalReadCount,
                totalWriteCount,
                totalSkipCount,
                steps
        );
    }

    public StepDetailsResponse getStepDetails(Long jobId, String stepName) {
        JobExecution jobExecution = jobRepository.getJobExecution(jobId);
        
        if (jobExecution == null) {
            return null;
        }

        StepExecution stepExecution = jobExecution.getStepExecutions().stream()
                .filter(step -> step.getStepName().equals(stepName))
                .findFirst()
                .orElse(null);

        if (stepExecution == null) {
            return null;
        }

        return toStepDetailsResponse(stepExecution);
    }

    private StepDetailsResponse toStepDetailsResponse(StepExecution stepExecution) {
        return new StepDetailsResponse(
                stepExecution.getStepName(),
                stepExecution.getStatus().name(),
                stepExecution.getStartTime(),
                stepExecution.getEndTime(),
                calculateDuration(stepExecution.getStartTime(), stepExecution.getEndTime()),
                stepExecution.getReadCount(),
                stepExecution.getWriteCount(),
                stepExecution.getSkipCount(),
                stepExecution.getCommitCount(),
                stepExecution.getRollbackCount()
        );
    }

    public boolean pauseJob(Long jobId) {
        try {
            JobExecution jobExecution = jobRepository.getJobExecution(jobId);
            if (jobExecution == null) {
                log.error("Job execution {} not found", jobId);
                return false;
            }
            jobOperator.stop(jobExecution);
            log.info("Job {} paused successfully", jobId);
            return true;
        } catch (Exception e) {
            log.error("Failed to pause job {}: {}", jobId, e.getMessage());
            return false;
        }
    }

    public boolean resumeJob(Long jobId) {
        try {
            // Spring Batch doesn't have a native resume feature
            // Instead, we'll restart the job if it was stopped
            JobExecution jobExecution = jobRepository.getJobExecution(jobId);
            
            if (jobExecution == null) {
                log.error("Job execution {} not found", jobId);
                return false;
            }

            if (jobExecution.getStatus() == BatchStatus.STOPPED) {
                jobOperator.restart(jobExecution);
                log.info("Job {} resumed successfully", jobId);
                return true;
            } else {
                log.warn("Job {} is not in STOPPED state, cannot resume. Current status: {}", 
                        jobId, jobExecution.getStatus());
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to resume job {}: {}", jobId, e.getMessage());
            return false;
        }
    }

    public JobMetricsResponse getJobMetrics(Long jobId) {
        JobExecution jobExecution = jobRepository.getJobExecution(jobId);
        
        if (jobExecution == null) {
            return null;
        }

        long totalRead = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getReadCount).sum();
        long totalWrite = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getWriteCount).sum();
        long totalSkip = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getSkipCount).sum();

        long duration = calculateDuration(jobExecution.getStartTime(), jobExecution.getEndTime());
        double recordsPerSecond = duration > 0 ? (totalRead * 1000.0) / duration : 0.0;

        int completionPercentage = calculateCompletionPercentage(jobExecution);
        long estimatedRemainingTime = estimateRemainingTime(jobExecution, recordsPerSecond);

        Runtime runtime = Runtime.getRuntime();
        long currentMemory = runtime.totalMemory() - runtime.freeMemory();
        long peakMemory = runtime.maxMemory();

        return new JobMetricsResponse(
                jobExecution.getId(),
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getStatus().name(),
                totalRead,
                totalSkip,
                jobExecution.getFailureExceptions().size(),
                recordsPerSecond,
                estimatedRemainingTime,
                completionPercentage,
                currentMemory,
                peakMemory,
                Thread.activeCount()
        );
    }

    private long getTotalRecordCount() {
        return salesDataRepository.count();
    }

    private int calculateCompletionPercentage(JobExecution jobExecution) {
        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            return 100;
        }
        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            return 0;
        }

        long totalRead = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getReadCount).sum();
        long totalRecords = getTotalRecordCount();

        if (totalRecords > 0 && totalRead > 0) {
            int pct = (int) (totalRead * 100.0 / totalRecords);
            return Math.min(99, pct);
        }
        return 0;
    }

    private long estimateRemainingTime(JobExecution jobExecution, double recordsPerSecond) {
        if (jobExecution.getStatus() == BatchStatus.COMPLETED || 
            jobExecution.getStatus() == BatchStatus.FAILED) {
            return 0;
        }

        if (recordsPerSecond <= 0) {
            return 0;
        }

        long totalRecords = getTotalRecordCount();
        long totalRead = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getReadCount).sum();
        long remainingRecords = Math.max(0, totalRecords - totalRead);

        return (long) (remainingRecords / recordsPerSecond);
    }
}

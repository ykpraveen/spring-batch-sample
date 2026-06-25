package com.example.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class BatchMetricsService {

    private final MeterRegistry meterRegistry;

    private final Counter recordsProcessed;
    private final Counter recordsSkipped;
    private final Counter recordsFailed;
    private final Counter jobsStarted;
    private final Counter jobsCompleted;
    private final Counter jobsFailed;
    private final Timer jobExecutionTimer;
    private final Timer stepExecutionTimer;

    public BatchMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.recordsProcessed = Counter.builder("batch.records.processed")
                .description("Total records successfully processed")
                .register(meterRegistry);

        this.recordsSkipped = Counter.builder("batch.records.skipped")
                .description("Total records skipped due to errors")
                .register(meterRegistry);

        this.recordsFailed = Counter.builder("batch.records.failed")
                .description("Total records that failed processing")
                .register(meterRegistry);

        this.jobsStarted = Counter.builder("batch.jobs.started")
                .description("Total jobs started")
                .register(meterRegistry);

        this.jobsCompleted = Counter.builder("batch.jobs.completed")
                .description("Total jobs completed successfully")
                .register(meterRegistry);

        this.jobsFailed = Counter.builder("batch.jobs.failed")
                .description("Total jobs that failed")
                .register(meterRegistry);

        this.jobExecutionTimer = Timer.builder("batch.job.duration")
                .description("Job execution duration")
                .register(meterRegistry);

        this.stepExecutionTimer = Timer.builder("batch.step.duration")
                .description("Step execution duration")
                .register(meterRegistry);
    }

    public void recordRecordProcessed() {
        recordsProcessed.increment();
    }

    public void recordRecordsProcessed(long count) {
        recordsProcessed.increment(count);
    }

    public void recordSkip() {
        recordsSkipped.increment();
    }

    public void recordFailure() {
        recordsFailed.increment();
    }

    public void recordJobStarted() {
        jobsStarted.increment();
    }

    public void recordJobCompleted() {
        jobsCompleted.increment();
    }

    public void recordJobFailed() {
        jobsFailed.increment();
    }

    public void recordJobDuration(long durationMs) {
        jobExecutionTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordStepDuration(long durationMs) {
        stepExecutionTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }
}

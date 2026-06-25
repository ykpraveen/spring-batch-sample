package com.example.batch;

import com.example.entity.SalesData;
import com.example.entity.ProcessedData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.annotation.OnSkipInProcess;
import org.springframework.batch.core.annotation.OnSkipInRead;
import org.springframework.batch.core.annotation.OnSkipInWrite;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BatchSkipListener {

    private final ErrorLogger errorLogger;

    private String stepName;
    private Integer partitionId;

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        this.stepName = stepExecution.getStepName();
        this.partitionId = stepExecution.getExecutionContext().containsKey("partitionId")
                ? stepExecution.getExecutionContext().getInt("partitionId")
                : null;
    }

    @OnSkipInProcess
    public void onSkipInProcess(SalesData item, Throwable throwable) {
        log.warn("Skipping record {} (id={}) due to: {}",
                item.getTransactionId(), item.getId(), throwable.getMessage());

        errorLogger.logFailedRecord(
                item.getId(),
                "sales_data",
                throwable.getMessage(),
                throwable instanceof Exception e ? e : new RuntimeException(throwable),
                item.toString(),
                partitionId,
                stepName,
                0
        );
    }

    @OnSkipInRead
    public void onSkipInRead(Throwable throwable) {
        log.warn("Skipping read due to: {}", throwable.getMessage());
    }

    @OnSkipInWrite
    public void onSkipInWrite(ProcessedData item, Throwable throwable) {
        log.warn("Skipping write of processed record {} due to: {}", item.getId(), throwable.getMessage());
    }
}

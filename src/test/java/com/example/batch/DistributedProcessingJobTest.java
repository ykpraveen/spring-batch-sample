package com.example.batch;

import com.example.controller.dto.JobDetailsResponse;
import com.example.controller.dto.JobMetricsResponse;
import com.example.controller.dto.StepDetailsResponse;
import com.example.entity.ProcessedData;
import com.example.entity.SalesData;
import com.example.repository.ProcessedDataRepository;
import com.example.repository.SalesDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.jakarta.persistence.validation.mode=none",
    "logging.level.org.springframework.batch=INFO"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DistributedProcessingJobTest {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job distributedProcessingJob;

    @Autowired
    private SalesDataRepository salesDataRepository;

    @Autowired
    private ProcessedDataRepository processedDataRepository;

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private DataValidator dataValidator;

    @BeforeEach
    void setUp() {
        processedDataRepository.deleteAll();
        salesDataRepository.deleteAll();
    }

    private SalesData createSale(String txnId, BigDecimal amount, int quantity, String region, String category) {
        SalesData sale = new SalesData();
        sale.setTransactionId(txnId);
        sale.setAmount(amount);
        sale.setQuantity(quantity);
        sale.setRegion(region);
        sale.setProductCategory(category);
        sale.setCreatedAt(LocalDateTime.now());
        sale.setProcessed(false);
        return salesDataRepository.save(sale);
    }

    @Test
    void testDataValidatorRejectsInvalidSalesData() {
        SalesData invalidSale = new SalesData();
        invalidSale.setTransactionId("invalid id");
        invalidSale.setAmount(null);
        invalidSale.setQuantity(0);
        invalidSale.setRegion("");
        invalidSale.setProductCategory("TestCategory");
        invalidSale.setCreatedAt(LocalDateTime.now().plusDays(1));
        invalidSale.setProcessed(false);

        assertThatThrownBy(() -> dataValidator.validate(invalidSale))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Sales data failed validation");
    }

    @Test
    void testJobProcessesSalesDataSuccessfully() throws Exception {
        for (int i = 1; i <= 10; i++) {
            createSale("TXN" + String.format("%04d", i),
                    new BigDecimal(String.valueOf(i * 100)),
                    i * 5,
                    "Region" + (i % 3),
                    "Category" + (i % 2));
        }

        JobParameters params = new JobParametersBuilder()
                .addString("runId", "test-success-" + UUID.randomUUID())
                .toJobParameters();
        JobExecution jobExecution = jobLauncher.run(distributedProcessingJob, params);

        long timeoutMs = 60000;
        long start = System.currentTimeMillis();
        while (jobExecution.isRunning()) {
            if (System.currentTimeMillis() - start > timeoutMs) break;
            Thread.sleep(100);
        }

        assertThat(jobExecution.getStatus().toString()).isEqualTo("COMPLETED");
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
        assertThat(processedDataRepository.count()).isEqualTo(10L);
    }

    @Test
    void testJobAppliesTaxAndDiscountCorrectly() throws Exception {
        createSale("TXN-TEST-001", new BigDecimal("1500.00"), 50, "TestRegion", "TestCategory");

        JobParameters params = new JobParametersBuilder()
                .addString("runId", "test-tax-discount-" + UUID.randomUUID())
                .toJobParameters();
        JobExecution jobExecution = jobLauncher.run(distributedProcessingJob, params);

        long timeoutMs = 60000;
        long start = System.currentTimeMillis();
        while (jobExecution.isRunning()) {
            if (System.currentTimeMillis() - start > timeoutMs) break;
            Thread.sleep(100);
        }

        assertThat(jobExecution.getStatus().toString()).isEqualTo("COMPLETED");

        ProcessedData processed = processedDataRepository.findAll().get(0);
        assertThat(processed.getOriginalAmount()).isEqualTo(new BigDecimal("1500.00"));
        assertThat(processed.getTaxAmount()).isEqualTo(new BigDecimal("150.00"));
        assertThat(processed.getDiscountApplied()).isEqualTo(new BigDecimal("75.00"));
        assertThat(processed.getProcessedAmount()).isEqualTo(new BigDecimal("1575.00"));
    }

    @Test
    void testJobProcessesLargeDatasetInParallel() throws Exception {
        int recordCount = 500;
        for (int i = 1; i <= recordCount; i++) {
            createSale("BULK-TXN" + String.format("%06d", i),
                    new BigDecimal(String.valueOf((i % 50 + 1) * 100)),
                    i % 100 + 1,
                    "Region" + (i % 5),
                    "Category" + (i % 3));
        }

        long startTime = System.currentTimeMillis();
        JobParameters params = new JobParametersBuilder()
                .addString("runId", "test-parallel-" + UUID.randomUUID())
                .toJobParameters();
        JobExecution jobExecution = jobLauncher.run(distributedProcessingJob, params);

        long timeoutMs = 60000;
        long start = System.currentTimeMillis();
        while (jobExecution.isRunning()) {
            if (System.currentTimeMillis() - start > timeoutMs) break;
            Thread.sleep(100);
        }
        long duration = System.currentTimeMillis() - startTime;

        assertThat(jobExecution.getStatus().toString()).isEqualTo("COMPLETED");
        assertThat(duration).isLessThan(30000);
        assertThat(processedDataRepository.count()).isEqualTo(recordCount);
    }

    @Test
    void testJobSkipsInvalidRecordAndProcessesValidRecords() throws Exception {
        createSale("TXN-VALID-001", new BigDecimal("100.00"), 10, "TestRegion", "TestCategory");

        SalesData invalidSale = new SalesData();
        invalidSale.setTransactionId("TXN-INVALID-001");
        invalidSale.setAmount(null);
        invalidSale.setQuantity(5);
        invalidSale.setRegion("TestRegion");
        invalidSale.setProductCategory("TestCategory");
        invalidSale.setCreatedAt(LocalDateTime.now());
        invalidSale.setProcessed(false);
        salesDataRepository.save(invalidSale);

        JobParameters params = new JobParametersBuilder()
                .addString("runId", "test-skip-invalid-" + UUID.randomUUID())
                .toJobParameters();
        JobExecution jobExecution = jobLauncher.run(distributedProcessingJob, params);

        long timeoutMs = 60000;
        long start = System.currentTimeMillis();
        while (jobExecution.isRunning()) {
            if (System.currentTimeMillis() - start > timeoutMs) break;
            Thread.sleep(100);
        }

        assertThat(jobExecution.getStatus().toString()).isEqualTo("COMPLETED");
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
        assertThat(processedDataRepository.count()).isEqualTo(1L);

        ProcessedData processed = processedDataRepository.findAll().get(0);
        assertThat(processed.getTransactionId()).isEqualTo("TXN-VALID-001");
        assertThat(processed.getProcessedAmount()).isEqualTo(new BigDecimal("110.00"));
    }

    @Test
    void testJobDetailsEndpointReturnsJobInformation() throws Exception {
        createSale("TXN-DETAILS", new BigDecimal("200.00"), 5, "North", "Books");

        JobParameters params = new JobParametersBuilder()
                .addString("runId", "test-details-" + UUID.randomUUID())
                .toJobParameters();
        JobExecution jobExecution = jobLauncher.run(distributedProcessingJob, params);

        long timeoutMs = 60000;
        long start = System.currentTimeMillis();
        while (jobExecution.isRunning()) {
            if (System.currentTimeMillis() - start > timeoutMs) break;
            Thread.sleep(100);
        }

        JobDetailsResponse jobDetails = jobExecutionService.getJobDetails(jobExecution.getId());

        assertThat(jobDetails).isNotNull();
        assertThat(jobDetails.jobId()).isEqualTo(jobExecution.getId());
        assertThat(jobDetails.jobName()).isEqualTo("distributedProcessingJob");
        assertThat(jobDetails.status()).isEqualTo("COMPLETED");
        assertThat(jobDetails.exitCode()).isNotBlank();
        assertThat(jobDetails.steps()).isNotEmpty();
    }

    @Test
    void testStepDetailsEndpointReturnsStepMetrics() throws Exception {
        createSale("TXN-STEP-TEST", new BigDecimal("500.00"), 10, "TestRegion", "TestCategory");

        JobParameters params = new JobParametersBuilder()
                .addString("runId", "test-step-" + UUID.randomUUID())
                .toJobParameters();
        JobExecution jobExecution = jobLauncher.run(distributedProcessingJob, params);

        long timeoutMs = 60000;
        long start = System.currentTimeMillis();
        while (jobExecution.isRunning()) {
            if (System.currentTimeMillis() - start > timeoutMs) break;
            Thread.sleep(100);
        }

        StepDetailsResponse stepDetails = jobExecutionService.getStepDetails(
                jobExecution.getId(), "masterStep");

        assertThat(stepDetails).isNotNull();
        assertThat(stepDetails.stepName()).isEqualTo("masterStep");
        assertThat(stepDetails.status()).isEqualTo("COMPLETED");
        assertThat(stepDetails.readCount()).isGreaterThan(0);
    }

    @Test
    void testJobMetricsEndpointReturnsPerformanceMetrics() throws Exception {
        createSale("TXN-METRICS", new BigDecimal("300.00"), 3, "South", "Sports");

        JobParameters params = new JobParametersBuilder()
                .addString("runId", "test-metrics-" + UUID.randomUUID())
                .toJobParameters();
        JobExecution jobExecution = jobLauncher.run(distributedProcessingJob, params);

        long timeoutMs = 60000;
        long start = System.currentTimeMillis();
        while (jobExecution.isRunning()) {
            if (System.currentTimeMillis() - start > timeoutMs) break;
            Thread.sleep(100);
        }

        JobMetricsResponse metrics = jobExecutionService.getJobMetrics(jobExecution.getId());

        assertThat(metrics).isNotNull();
        assertThat(metrics.jobId()).isEqualTo(jobExecution.getId());
        assertThat(metrics.jobName()).isEqualTo("distributedProcessingJob");
        assertThat(metrics.status()).isEqualTo("COMPLETED");
        assertThat(metrics.completionPercentage()).isEqualTo(100);
        assertThat(metrics.totalRecordsFailed()).isEqualTo(0);
    }

    @Test
    void testJobSummariesEndpointListsAllJobs() throws Exception {
        createSale("TXN-SUMMARY", new BigDecimal("100.00"), 10, "TestRegion", "TestCategory");

        JobParameters params = new JobParametersBuilder()
                .addString("runId", "test-summary-" + UUID.randomUUID())
                .toJobParameters();
        JobExecution jobExecution = jobLauncher.run(distributedProcessingJob, params);

        long timeoutMs = 60000;
        long start = System.currentTimeMillis();
        while (jobExecution.isRunning()) {
            if (System.currentTimeMillis() - start > timeoutMs) break;
            Thread.sleep(100);
        }

        var summaries = jobExecutionService.getJobSummaries();

        assertThat(summaries).isNotEmpty();
        var jobSummary = summaries.stream()
                .filter(s -> s.jobName().equals("distributedProcessingJob"))
                .findFirst();

        assertThat(jobSummary).isPresent();
        assertThat(jobSummary.get().lastStatus()).isEqualTo("COMPLETED");
        assertThat(jobSummary.get().jobInstanceCount()).isGreaterThan(0);
    }
}

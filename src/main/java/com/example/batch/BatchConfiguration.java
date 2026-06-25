package com.example.batch;

import com.example.entity.ProcessedData;
import com.example.entity.SalesData;
import com.example.partitioner.SalesDataPartitioner;
import com.example.processor.SalesDataProcessor;
import com.example.repository.ProcessedDataRepository;
import com.example.repository.SalesDataRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.data.RepositoryItemReader;
import org.springframework.batch.infrastructure.item.data.RepositoryItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(BatchProperties.class)
@Slf4j
public class BatchConfiguration {

    private final SalesDataRepository salesDataRepository;
    private final ProcessedDataRepository processedDataRepository;
    private final SalesDataProcessor salesDataProcessor;
    private final SalesDataPartitioner salesDataPartitioner;
    private final BatchSkipPolicy batchSkipPolicy;
    private final BatchSkipListener batchSkipListener;
    private final BatchProperties batchProperties;

    public BatchConfiguration(SalesDataRepository salesDataRepository,
                              ProcessedDataRepository processedDataRepository,
                              SalesDataProcessor salesDataProcessor,
                              SalesDataPartitioner salesDataPartitioner,
                              BatchSkipPolicy batchSkipPolicy,
                              BatchSkipListener batchSkipListener,
                              BatchProperties batchProperties) {
        this.salesDataRepository = salesDataRepository;
        this.processedDataRepository = processedDataRepository;
        this.salesDataProcessor = salesDataProcessor;
        this.salesDataPartitioner = salesDataPartitioner;
        this.batchSkipPolicy = batchSkipPolicy;
        this.batchSkipListener = batchSkipListener;
        this.batchProperties = batchProperties;
    }

    @PostConstruct
    void logConfiguration() {
        var tp = batchProperties.threadPool();
        var pt = batchProperties.partitioning();
        var rt = batchProperties.retry();
        var ft = batchProperties.features();
        log.info("Batch configuration: thread-pool(core={}, max={}, queue={})", tp.corePoolSize(), tp.maxPoolSize(), tp.queueCapacity());
        log.info("  partitioning(chunk={}, page={}, grid={}, partition={}, skip-limit={})",
                pt.chunkSize(), pt.pageSize(), pt.gridSize(), pt.partitionSize(), pt.skipLimit());
        log.info("  retry(limit={}), features(metrics-export={})", rt.retryLimit(), ft.metricsExport());
    }
    
    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(batchProperties.threadPool().corePoolSize());
        executor.setMaxPoolSize(batchProperties.threadPool().maxPoolSize());
        executor.setQueueCapacity(batchProperties.threadPool().queueCapacity());
        executor.setThreadNamePrefix("batch-partition-");
        executor.initialize();
        return executor;
    }
    
    @Bean
    @StepScope
    public RepositoryItemReader<SalesData> partitionedReader(
            @Value("#{stepExecutionContext['minId']}") Long minId,
            @Value("#{stepExecutionContext['maxId']}") Long maxId,
            @Value("#{stepExecutionContext['partitionId']}") Integer partitionId) {
        
        Map<String, Sort.Direction> sorts = new HashMap<>();
        sorts.put("id", Sort.Direction.ASC);
        
        RepositoryItemReader<SalesData> reader = new RepositoryItemReader<>(salesDataRepository, sorts);
        reader.setMethodName("findUnprocessedByIdRange");
        reader.setArguments(java.util.List.of(minId, maxId));
        reader.setPageSize(batchProperties.partitioning().pageSize());
        
        return reader;
    }
    
    @Bean
    @StepScope
    public RepositoryItemWriter<ProcessedData> partitionedWriter(
            @Value("#{stepExecutionContext['partitionId']}") Integer partitionId) {
        
        RepositoryItemWriter<ProcessedData> writer = new RepositoryItemWriter<>(processedDataRepository);
        writer.setMethodName("save");
        
        return writer;
    }
    
    @Bean
    public Step partitionedWorkerStep(JobRepository jobRepository,
                                      RepositoryItemReader<SalesData> reader,
                                      RepositoryItemWriter<ProcessedData> writer) {
        
        return new StepBuilder("partitionedWorkerStep", jobRepository)
                .<SalesData, ProcessedData>chunk(batchProperties.partitioning().chunkSize())
                .reader(reader)
                .processor(salesDataProcessor)
                .writer(writer)
                .faultTolerant()
                .skipPolicy(batchSkipPolicy)
                .listener(batchSkipListener)
                .skipLimit(batchProperties.partitioning().skipLimit())
                .retry(Exception.class)
                .retryLimit(batchProperties.retry().retryLimit())
                .build();
    }
    
    @Bean
    public Step masterStep(JobRepository jobRepository,
                          TaskExecutor taskExecutor,
                          RepositoryItemReader<SalesData> reader,
                          RepositoryItemWriter<ProcessedData> writer) {
        
        return new StepBuilder("masterStep", jobRepository)
                .partitioner("partitionedWorkerStep", salesDataPartitioner)
                .step(partitionedWorkerStep(jobRepository, reader, writer))
                .gridSize(batchProperties.partitioning().gridSize())
                .taskExecutor(taskExecutor)
                .build();
    }
    
    @Bean
    public Job distributedProcessingJob(JobRepository jobRepository,
                                       TaskExecutor taskExecutor,
                                       RepositoryItemReader<SalesData> reader,
                                       RepositoryItemWriter<ProcessedData> writer) {
        
        return new JobBuilder("distributedProcessingJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(masterStep(jobRepository, taskExecutor, reader, writer))
                .build();
    }
}

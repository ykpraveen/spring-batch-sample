# Spring Batch Distributed Data Processing

A Spring Batch application demonstrating **multi-threaded partitioned data processing** with parallel execution across multiple threads using **Java 21**, **Spring Boot 4.1.0**, and **Spring Batch 6.0.4**.

Dev.to article: https://dev.to/ykpraveen/building-distributed-data-processing-with-spring-batch-6-spring-boot-4-5hf8

## Project Overview

- **Data Partitioning**: Splits large datasets into manageable chunks
- **Parallel Processing**: Processes multiple partitions concurrently using `ThreadPoolTaskExecutor`
- **Master-Worker Pattern**: Coordinated step execution with fault tolerance
- **Skip & Retry**: Fault-tolerant processing with dead letter queue for failed records
- **Monitoring**: Micrometer/Prometheus metrics, structured JSON logging, correlation IDs
- **REST API**: Full job lifecycle management and metrics endpoints
- **Validation**: Input validation with `DataValidator` and `ValidationException`

## Architecture

### Flow

```
Raw Sales Data
       ↓
  [Partitioner]  -- splits data by ID ranges
       ↓
   N Partitions (configurable grid size)
       ↓
  Thread Pool (core/max pool size, queue capacity)
       ↓
  Partition 1 → Read → Validate → Process (tax, discount) → Write
  Partition 2 → Read → Validate → Process (tax, discount) → Write
  ... (parallel execution)
       ↓
  Processed Data + Dead Letter Queue
```

### Components

| Component | Responsibility |
|-----------|---------------|
| `SalesDataPartitioner` | Splits unprocessed records into N partitions by ID range |
| `SalesDataProcessor` | Validates input, calculates tax (10%), applies bulk discount (5% over 1000) |
| `BatchSkipPolicy` | Skips recoverable exceptions (`ValidationException`, `IllegalArgumentException`, etc.) |
| `BatchSkipListener` | Logs skipped records to dead letter queue via `ErrorLogger` |
| `BatchConfiguration` | Assembles master/worker steps with partitioning, fault tolerance, skip/retry |
| `JobExecutionService` | Orchestrates job runs, logs structured summary reports |
| `BatchMetricsService` | Micrometer counters/timers for records, jobs, and steps |
| `CorrelationIdFilter` | Adds `X-Correlation-Id` header + MDC context |
| `BatchProperties` | Centralized configuration via `@ConfigurationProperties(prefix = "batch")` |

## Tech Stack

| Component | Version |
|-----------|---------|
| Java | 21 LTS |
| Spring Boot | 4.1.0 |
| Spring Batch | 6.0.4 |
| Spring Data JPA | (managed) |
| PostgreSQL | 16-alpine |
| H2 | (test scope) |
| Hibernate | (managed) |
| Micrometer | + Prometheus registry |
| SpringDoc / Swagger | 2.8.6 |
| Lombok | (optional) |

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.6+
- Docker (for PostgreSQL)

### Start PostgreSQL

```bash
docker compose up -d postgres
```

PostgreSQL available at `localhost:5433`, database `batch_db`, user `batch_user`, password `batch_password`.

### Build & Run

```bash
mvn clean package
java -jar target/spring-batch-distributed-1.0.0.jar
```

Or with dev profile (H2 in-memory, no PostgreSQL needed):
```bash
java -jar target/spring-batch-distributed-1.0.0.jar --spring.profiles.active=dev
```

### Quick Start via Docker

```bash
docker compose up -d --build
# App at http://localhost:8080
```

## Profiles

| Profile | Database | show-sql | Logging | Use Case |
|---------|----------|----------|---------|----------|
| (default) | PostgreSQL | false | JSON + DEBUG | Production-like |
| `dev` | H2 in-memory | true | Text + DEBUG | Local development |
| `prod` | PostgreSQL | false | JSON + INFO | Production tuning |

Activate via `SPRING_PROFILES_ACTIVE=dev` or `--spring.profiles.active=prod`.

## REST API

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/batch/start` | Start a distributed processing job |
| GET | `/api/batch/health` | Application health |
| GET | `/api/batch/jobs` | List all jobs |
| GET | `/api/batch/jobs/{jobName}/executions` | Execution history |
| GET | `/api/batch/jobs/{jobId}` | Job details |
| GET | `/api/batch/jobs/{jobId}/steps/{stepName}` | Step details |
| GET | `/api/batch/jobs/{jobId}/metrics` | Real-time metrics |
| POST | `/api/batch/jobs/{jobId}/pause` | Pause a running job |
| POST | `/api/batch/jobs/{jobId}/resume` | Resume a paused job |
| GET | `/api/batch/dlq` | List all DLQ records |
| GET | `/api/batch/dlq/unresolved` | List unresolved records |
| GET | `/api/batch/dlq/{id}` | Get DLQ record by ID |
| GET | `/api/batch/dlq/partition/{partitionId}` | Records by partition |
| GET | `/api/batch/dlq/step/{stepName}` | Records by step |
| POST | `/api/batch/dlq/{id}/resolve` | Mark record as resolved |
| GET | `/api/batch/dlq/stats` | DLQ statistics |
| DELETE | `/api/batch/dlq/{id}` | Delete a resolved record |

Full curl examples in [docs/API.md](docs/API.md).

### Swagger UI

Open `http://localhost:8080/swagger-ui.html` after starting the application.

## Actuator Endpoints

| Path | Description |
|------|-------------|
| `/actuator/health` | Health check |
| `/actuator/metrics` | Application metrics |
| `/actuator/prometheus` | Prometheus-scrapable metrics |

## Configuration

All batch settings are externalized via `BatchProperties` (prefix `batch.*`) and configurable in `application.yml` or via environment variables:

```yaml
batch:
  thread-pool:
    core-pool-size: 4      # BATCH_THREADPOOL_COREPOOLSIZE
    max-pool-size: 8       # BATCH_THREADPOOL_MAXPOOLSIZE
    queue-capacity: 100    # BATCH_THREADPOOL_QUEUECAPACITY
  partitioning:
    chunk-size: 500
    page-size: 1000
    grid-size: 8
    partition-size: 1000
    skip-limit: 1000
  retry:
    retry-limit: 2
  features:
    metrics-export: true
```

## Testing

```bash
mvn test
```

9 integration tests in `DistributedProcessingJobTest`:
- Successful processing, tax/discount calculation
- Large dataset parallel execution (500 records)
- Skip invalid records, validate
- Job details, step details, metrics, summaries endpoints

Tests use H2 in-memory database with `@DirtiesContext(classMode = AFTER_CLASS)`.

## Error Handling

- **Skip policy**: `ValidationException`, `IllegalArgumentException`, `NumberFormatException`, `NullPointerException` are skipped and logged to dead letter queue
- **Retry policy**: Transient failures retry up to configured limit (default 2)
- **Dead letter queue**: Skipped records stored in `dead_letter_queue` table with metadata, stack trace, and resolution tracking

## Project Structure

```
spring-batch-sample/
├── Dockerfile
├── docker-compose.yml
├── .dockerignore
├── pom.xml
├── README.md
├── PLAN.md
├── docs/
│   ├── API.md
│   └── TROUBLESHOOTING.md
├── src/main/java/com/example/
│   ├── SpringBatchApplication.java
│   ├── batch/
│   │   ├── BatchConfiguration.java
│   │   ├── BatchProperties.java
│   │   ├── BatchSkipListener.java
│   │   ├── BatchSkipPolicy.java
│   │   ├── DataValidator.java
│   │   ├── ErrorLogger.java
│   │   ├── JobExecutionService.java
│   │   ├── SampleDataLoader.java
│   │   └── ValidationException.java
│   ├── controller/
│   │   ├── BatchJobController.java
│   │   ├── DeadLetterQueueController.java
│   │   └── dto/ (JobSummaryResponse, JobDetailsResponse, etc.)
│   ├── entity/
│   │   ├── SalesData.java
│   │   ├── ProcessedData.java
│   │   └── DeadLetterQueue.java
│   ├── monitoring/
│   │   ├── BatchMetricsService.java
│   │   ├── CorrelationIdFilter.java
│   │   └── MetricsConfiguration.java
│   ├── partitioner/
│   │   └── SalesDataPartitioner.java
│   ├── processor/
│   │   └── SalesDataProcessor.java
│   └── repository/
│       ├── SalesDataRepository.java
│       ├── ProcessedDataRepository.java
│       └── DeadLetterQueueRepository.java
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   └── application-prod.yml
└── src/test/java/com/example/batch/
    └── DistributedProcessingJobTest.java
```

## Key Learning Points

- **Spring Batch 6**: `SkipListener` removed; use `@OnSkipInProcess`/`@OnSkipInRead`/`@OnSkipInWrite` annotations
- **Partitioning**: `SalesDataPartitioner` distributes ID ranges proportionally, handles gaps in sequences
- **`@SpringBatchTest`**: `JobScopeTestExecutionListener` intercepts methods returning `JobExecution` — keep polling loops inline
- **Configuration**: Use records for `@ConfigurationProperties` (Spring Boot 4.x supports records as property holders)
- **Optimistic locking**: `@Version` field on all entities; batch processor creates new entities without updating originals
- **Batch inserts**: `hibernate.jdbc.batch_size=50`, `order_inserts=true`, `order_updates=true`, `batch_versioned_data=true`
- **Feature toggles**: `@ConditionalOnProperty` with `batch.features.*` prefix

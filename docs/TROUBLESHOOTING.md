# Troubleshooting Guide

## Build Issues

### Maven build fails with dependency resolution errors

Ensure you have an internet connection for Maven to download dependencies. If behind a proxy, configure Maven's `settings.xml`.

### Lombok annotations not recognized (IDE)

Install Lombok plugin in your IDE and enable annotation processing:
- **IntelliJ:** Settings → Build → Compiler → Annotation Processors → Enable annotation processing
- **VS Code:** Install Lombok Annotations Support extension

## Database Issues

### PostgreSQL connection refused

1. Ensure PostgreSQL is running: `docker compose up -d postgres`
2. Verify credentials in `application.yml` match `docker-compose.yml`
3. Check port: PostgreSQL listens on `5433` (host) → `5432` (container)

### Tables not created automatically

Both `application.yml` and test config use `ddl-auto: update`/`create-drop`. If tables are missing, temporarily set:
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: create
```

## Runtime Issues

### Job fails to start

Check the application logs for the root cause. Common issues:
- Database not available
- Invalid data in source tables
- Out of memory (increase heap with `-Xmx`)

### Records are being skipped unexpectedly

Check the Dead Letter Queue endpoint for skipped records:
```bash
curl http://localhost:8080/api/batch/dlq
```

The `BatchSkipPolicy` logs skippable exceptions at WARN level. Enable DEBUG logging for more detail:
```yaml
logging:
  level:
    com.example: DEBUG
```

### Partitioning creates too many/few partitions

Adjust partition settings in `application.yml` or via environment variables:
```bash
export BATCH_PARTITIONING_PARTITIONSIZE=500
export BATCH_PARTITIONING_GRIDSIZE=4
```

## Test Issues

### Tests fail with "No matching arguments found for method"

The `@SpringBatchTest` `JobScopeTestExecutionListener` intercepts methods returning `JobExecution`. Do not define helper methods that return `JobExecution` in test classes. Keep the polling loop inline.

### Tests are slow

`@DirtiesContext(classMode = AFTER_CLASS)` reduces context startups. Tests that need a clean context between scenarios should be split into separate test classes rather than per-test context reloads.

## Docker Issues

### Container exits immediately

Check logs: `docker compose logs app`. Common causes:
- PostgreSQL not healthy when app starts (check `depends_on` conditions)
- Wrong database URL in environment variables

### Port already in use

Change host ports in `docker-compose.yml`:
```yaml
ports:
  - "8081:8080"  # instead of 8080:8080
```

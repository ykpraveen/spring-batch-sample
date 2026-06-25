# API Reference

Base URL: `http://localhost:8080`

## Swagger UI

Open `http://localhost:8080/swagger-ui.html` in a browser for interactive API docs.

## Batch Job Endpoints

### Start a job

```bash
curl -X POST http://localhost:8080/api/batch/start
```

### List all jobs

```bash
curl http://localhost:8080/api/batch/jobs
```

### Get job execution history

```bash
curl http://localhost:8080/api/batch/jobs/{jobName}/executions
```

### Get job details

```bash
curl http://localhost:8080/api/batch/jobs/{jobId}
```

### Get step details

```bash
curl http://localhost:8080/api/batch/jobs/{jobId}/steps/{stepName}
```

### Get job metrics

```bash
curl http://localhost:8080/api/batch/jobs/{jobId}/metrics
```

### Pause a job

```bash
curl -X POST http://localhost:8080/api/batch/jobs/{jobId}/pause
```

### Resume a paused job

```bash
curl -X POST http://localhost:8080/api/batch/jobs/{jobId}/resume
```

## Dead Letter Queue Endpoints

### List all DLQ records

```bash
curl http://localhost:8080/api/batch/dlq
```

### List unresolved records

```bash
curl http://localhost:8080/api/batch/dlq/unresolved
```

### Get DLQ record by ID

```bash
curl http://localhost:8080/api/batch/dlq/{id}
```

### Get records by partition

```bash
curl http://localhost:8080/api/batch/dlq/partition/{partitionId}
```

### Get records by step

```bash
curl http://localhost:8080/api/batch/dlq/step/{stepName}
```

### Mark record as resolved

```bash
curl -X POST http://localhost:8080/api/batch/dlq/{id}/resolve \
  -H "Content-Type: application/json" \
  -d '{"resolutionNotes": "Fixed the data source issue"}'
```

### Get DLQ statistics

```bash
curl http://localhost:8080/api/batch/dlq/stats
```

### Delete a resolved record

```bash
curl -X DELETE http://localhost:8080/api/batch/dlq/{id}
```

## Actuator Endpoints

### Health check

```bash
curl http://localhost:8080/actuator/health
```

### Prometheus metrics

```bash
curl http://localhost:8080/actuator/prometheus
```

### Application metrics

```bash
curl http://localhost:8080/actuator/metrics
```

## Health Check

```bash
curl http://localhost:8080/api/batch/health
```

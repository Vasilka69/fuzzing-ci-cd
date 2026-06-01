# E2E Demo Pipeline (OpenSearch Transport)

This scenario creates a demo pipeline and drives it to `success` by publishing synthetic executor events into OpenSearch.

## Prerequisites

1. Infrastructure is up:
   - PostgreSQL
   - OpenSearch
2. `master-service` is running with OpenSearch event transport:

```bash
set EXECUTOR_EVENTS_TRANSPORT=opensearch
mvn -pl master-service spring-boot:run
```

3. OpenSearch index exists (default: `cicd-executor-events`).

## Run

```powershell
powershell -ExecutionPolicy Bypass -File scripts/e2e/demo-pipeline-opensearch.ps1
```

Optional parameters:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/e2e/demo-pipeline-opensearch.ps1 `
  -ApiBaseUrl "http://localhost:8080/api/v1" `
  -OpenSearchUrl "http://localhost:9200" `
  -OpenSearchIndex "cicd-executor-events" `
  -Login "e2e-demo" -Password "e2e-demo" `
  -TimeoutSeconds 180
```

## What it validates

1. Create pipeline/stages/jobs over REST.
2. Start `pipeline_run`.
3. Apply executor status transitions (`RUNNING -> SUCCESS`) via OpenSearch poller.
4. Verify downstream job scheduling.
5. Verify final run status is `success`.

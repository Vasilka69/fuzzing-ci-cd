# 04. Контракты взаимодействия с executor-сервисами

## Правило сериализации

Внешние JSON payload для Kafka и OpenSearch используют `camelCase`. SQL и внутренние технические поля PostgreSQL могут быть `snake_case`. На границе сериализации master-service обязан конвертировать форматы без изменения семантики.

## Kafka topics

| Topic | Producer | Consumer | Назначение |
| --- | --- | --- | --- |
| `jobs.vcs` | master-service | vcs-service | Checkout/snapshot job. |
| `jobs.storage` | master-service | storage-service | Storage operations. |
| `jobs.build` | master-service | build-service | Build job. |
| `jobs.fuzzing` | master-service | fuzzing-service | Fuzzing job. |
| `jobs.deploy` | master-service | deploy-service | Deployment job. |
| `jobs.script` | master-service | script-service | User script job. |
| `jobs.results` | executor-ы | master-service | Status/result events при Kafka transport. |
| `jobs.cancel` | master-service | executor-ы | Cancel command. |
| `jobs.dead-letter` | executor/common runtime | operator/master | Dead-letter после retries. |

Kafka key для job message, result event и cancel command: `jobExecutionId`.

## JobMessage

```json
{
  "schemaVersion": 1,
  "messageId": "uuid",
  "correlationId": "uuid",
  "pipelineRunId": "uuid",
  "pipelineId": "uuid",
  "stageId": "uuid",
  "jobId": "uuid",
  "jobExecutionId": "uuid",
  "jobType": "build",
  "templatePath": "build/maven",
  "attempt": 1,
  "maxAttempts": 3,
  "timeoutSeconds": 1800,
  "resourceLimits": {
    "cpu": "2",
    "memoryMb": 4096,
    "diskMb": 10240
  },
  "workspacePolicy": {
    "cleanup": "always",
    "preserveOnFailure": false
  },
  "inputs": {
    "sourceSnapshotUri": "storage://..."
  },
  "params": {},
  "secrets": {
    "refs": []
  },
  "createdAt": "2026-05-07T00:00:00Z"
}
```

## ExecutorEventMessage

```json
{
  "schemaVersion": 1,
  "messageId": "uuid",
  "correlationId": "uuid",
  "pipelineRunId": "uuid",
  "pipelineId": "uuid",
  "stageId": "uuid",
  "jobId": "uuid",
  "jobExecutionId": "uuid",
  "jobType": "build",
  "templatePath": "build/maven",
  "eventType": "JOB_FINISHED",
  "status": "SUCCESS",
  "attempt": 1,
  "workerId": "build-worker-1",
  "startedAt": "2026-05-07T00:00:00Z",
  "finishedAt": "2026-05-07T00:05:00Z",
  "durationMs": 300000,
  "artifacts": [],
  "metrics": {},
  "summary": "Job completed successfully",
  "error": null,
  "logs": null,
  "additionalData": {}
}
```

## Event types

| `eventType` | Меняет business status | Master behavior |
| --- | --- | --- |
| `JOB_ACCEPTED` | Нет | Можно сохранить audit/progress. |
| `JOB_RUNNING` | Да | `queued -> running`. |
| `JOB_PROGRESS` | Нет | Обновить progress/metrics, отправить SSE. |
| `JOB_ARTIFACT` | Нет | Зарегистрировать artifact metadata. |
| `JOB_LOG` | Нет | Не применять как status event; читать как лог из OpenSearch. |
| `JOB_FINISHED` | Да | Перевести в `success/failed/timeout/canceled`, запустить scheduler-loop. |
| `JOB_SKIPPED` | Да | Зафиксировать skipped. Обычно генерируется master. |
| `JOB_CANCELED` | Да | `canceling -> canceled`. |
| `JOB_HEARTBEAT` | Нет | Обновить heartbeat/progress. |

## Status mapping

| External `status` | Internal `job_execution.status` |
| --- | --- |
| `QUEUED` | `queued` |
| `RUNNING` | `running` |
| `SUCCESS` | `success` |
| `FAILED` | `failed` |
| `TIMEOUT` | `timeout` |
| `CANCELING` | `canceling` |
| `CANCELED` | `canceled` |
| `RETRYING` | `retrying` |
| `SKIPPED` | `skipped` |
| `WAITING_APPROVAL` | `waiting_approval` |

## Error object

```json
{
  "code": "BUILD_EXIT_CODE_NON_ZERO",
  "type": "user_code_error",
  "retryable": false,
  "message": "Build command finished with non-zero exit code",
  "details": {}
}
```

Master сохраняет `error.type` в `job_execution.error_type`, `error.message` в `error_message`, детали в JSONB.

## ArtifactDescriptor

```json
{
  "artifactId": "uuid-or-null",
  "type": "build_artifact",
  "name": "app.jar",
  "uri": "storage://pipelines/{pipelineId}/runs/{pipelineRunId}/jobs/{jobId}/executions/{jobExecutionId}/app.jar",
  "sizeBytes": 123456,
  "sha256": "hex",
  "metadata": {}
}
```

Artifact URI должен включать `pipelineRunId`, `jobId`, `jobExecutionId`.

## OpenSearch document

Индекс: `cicd-executor-events`.

```json
{
  "documentId": "uuid",
  "ingestedAt": "2026-05-07T00:00:00Z",
  "sourceService": "build-service",
  "eventType": "JOB_LOG",
  "pipelineId": "uuid",
  "jobId": "uuid",
  "jobExecutionId": "uuid",
  "status": "RUNNING",
  "startedAt": "2026-05-07T00:00:00Z",
  "finishedAt": null,
  "durationMs": null,
  "logs": "log chunk",
  "additionalData": {
    "logOnly": true,
    "sourceEventType": "JOB_RUNNING"
  }
}
```

Mapping:

| Field | Type |
| --- | --- |
| `documentId`, `sourceService`, `eventType`, `pipelineId`, `jobId`, `jobExecutionId`, `status` | `keyword` |
| `ingestedAt`, `startedAt`, `finishedAt` | `date` |
| `durationMs` | `long` |
| `logs` | `text` |
| `additionalData` | `object` |

## CancelCommand

```json
{
  "schemaVersion": 1,
  "messageId": "uuid",
  "correlationId": "uuid",
  "pipelineRunId": "uuid",
  "jobExecutionId": "uuid",
  "reason": "user_requested",
  "requestedBy": "uuid",
  "gracePeriodSeconds": 30,
  "requestedAt": "2026-05-07T00:00:00Z"
}
```

## Type-specific inputs/outputs

| Job type | Required params | Expected outputs |
| --- | --- | --- |
| `vcs` | `vcsType`, `repositoryUrl`, `ref`, `credentialsRef` or public flag, `snapshotPolicy` | `sourceSnapshotUri`, `commitHash`, `checksum`, `logsUri` |
| `storage` | `operation`, source URI, destination/retention policy | final URI, checksum, size, metadata |
| `build` | `buildTool`, `sourceSnapshotUri`, command/template, `expectedArtifacts` | `buildArtifacts`, exit code, build metrics |
| `fuzzing` | `targetArtifactUri` or `sourceSnapshotUri`, `targetCommand`, seed corpus, budget, memory/time limits, mode | fuzzing report, crash/hang/corpus artifacts, AFL/LLM stats |
| `deploy` | `deploymentType`, `artifactUri`, target, credentials ref, commands/config, healthcheck, rollback policy | release id, deployment manifest, healthcheck, rollback metadata |
| `script` | `scriptType`, script body/artifact, input artifacts, environment, timeout, outputs | exit code, logs, output artifacts, runtime metadata |

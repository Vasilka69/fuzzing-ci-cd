# 09. Стратегия тестирования и критерии приемки

## Test levels

| Уровень | Что проверять |
| --- | --- |
| Unit | State machine, graph builder, retry policy, params validation, permission service. |
| Contract | JSON Schema для JobMessage, ExecutorEventMessage, CancelCommand, OpenSearch document. |
| Integration | PostgreSQL + Flyway + JPA repositories; Kafka outbox; Kafka result consumer; OpenSearch poller. |
| API tests | REST controllers, permissions, error format, pagination. |
| UI tests | Pipeline designer, run page, SSE updates, logs viewer. |
| Failure injection | Duplicate events, late events, unavailable Kafka/OpenSearch, timeout watchdog, poller restart. |
| Security | No secrets in logs/events/artifacts/audit; denied access; protected deployment. |

## Минимальные сценарии

1. Успешный pipeline: `vcs -> build -> fuzzing -> deploy`.
2. Ошибка build: `JOB_FINISHED/FAILED`, pipeline -> `failed`, downstream `on_success` не запускается.
3. Retry infrastructure error: первая attempt failed retryable, новая `job_execution` с `attempt=2`.
4. Duplicate event: повторный `messageId` не меняет состояние второй раз.
5. Late success после canceled не переводит job в success.
6. OpenSearch logs: `JOB_LOG` виден через `GET /job-executions/{id}/logs`.
7. OpenSearch transport: poller применяет `JOB_FINISHED`, cursor сохраняется.
8. Protected deploy: job ждет approval, Kafka message не публикуется до approve.
9. Cancel running job: status -> `canceling` -> `canceled`.
10. VCS webhook duplicate: один `external_event_id` создает только один run.

## Acceptance criteria master-service

- Миграции применяются на чистую PostgreSQL.
- UI/API позволяют создать pipeline и сохранить его в БД.
- Запуск pipeline создает `pipeline_run` и стартовые `job_execution`.
- Outbox содержит job messages, publisher отправляет их в Kafka topic по `jobType`.
- Master принимает result/status events через Kafka или OpenSearch.
- Master обновляет `job_execution`/`pipeline_run` строго по state machine.
- Logs читаются из OpenSearch по `jobExecutionId`.
- `JOB_FINISHED` без logs payload не ломает UI.
- SSE обновляет frontend по статусам и логам.
- Artifacts metadata сохраняется и отображается.
- RBAC запрещает неразрешенные операции.
- Секреты не попадают в PostgreSQL/Kafka/OpenSearch/UI.

## Contract compatibility checks

- `schemaVersion` integer = 1.
- External JSON fields in camelCase.
- Unknown fields ignored only where safe.
- Required identifiers not null: `messageId`, `correlationId`, `pipelineRunId`, `pipelineId`, `jobId`, `jobExecutionId`, `jobType`.
- `jobExecutionId` is used as Kafka key.
- `error.type` belongs to allowed dictionary.
- Artifact URI contains `pipelineRunId`, `jobId`, `jobExecutionId`.

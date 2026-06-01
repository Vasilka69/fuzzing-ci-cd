# 05. Orchestration, state machine, retry, cancel, approval

## Запуск pipeline

1. Проверить `pipeline.is_active` и permission `run`.
2. Загрузить stage/job/template/dependency graph.
3. Проверить template compatibility: `job.job_type == job_template.job_type`.
4. Проверить циклы и невалидные dependencies.
5. Создать `pipeline_run(status=queued, correlation_id=uuid)`.
6. Вычислить ready jobs.
7. Для каждой ready job создать `job_execution(status=queued, attempt=1)`.
8. Если job — deployment в protected environment, создать `deployment_approval` и оставить `job_execution.status=waiting_approval`.
9. Иначе создать `outbox_event` с `JobMessage`.
10. Outbox publisher публикует Kafka message.

## Построение graph

Правила:

- Stage упорядочены по `stage.position`.
- Если есть явные `job_dependency`, они имеют приоритет над неявным stage order.
- `run_policy=sequential` добавляет зависимости между job по `position` внутри stage.
- `run_policy=parallel` не добавляет зависимости между job внутри stage.
- Stage с большей position неявно зависит от критических job предыдущих stage, если нет явных dependencies.

## Условия запуска dependency

| Condition | Ready when |
| --- | --- |
| `on_success` | upstream `success` |
| `on_failure` | upstream `failed` или `timeout` |
| `always` | upstream в любом финальном состоянии, кроме global cancel |

`continue_on_error=true` не превращает failed job в success. Он только разрешает продолжить независимые ветки и downstream job, если условия зависимостей это позволяют.

## JobExecution state machine

```text
queued -> running -> success
queued -> running -> failed -> retrying -> new queued execution
queued -> running -> timeout -> retrying/new queued или final timeout
queued -> waiting_approval -> queued -> running
running -> canceling -> canceled
queued -> skipped
```

Финальные состояния:

```text
success, failed, timeout, canceled, skipped
```

Запрещено переводить финальное состояние обратно в active. Late event сохраняется в inbox/audit/diagnostic, но не меняет бизнес-статус.

## PipelineRun state machine

| Status | Meaning |
| --- | --- |
| `queued` | Запуск создан, стартовые job еще не выполняются. |
| `running` | Есть active job. |
| `waiting_approval` | Прогресс заблокирован approval. |
| `success` | Все критические ветки успешно завершены или допустимо пропущены. |
| `failed` | Критическая job завершилась failed/timeout без допустимого продолжения. |
| `canceling` | Запрошена отмена active jobs. |
| `canceled` | Запуск отменен. |
| `timeout` | Истек общий timeout run. |

## Retry policy

Retry разрешен по умолчанию для временных инфраструктурных ошибок:

| Error | Retry |
| --- | --- |
| `infrastructure_error` | Да, если попытки не исчерпаны. |
| временная недоступность VCS/storage/Kafka/OpenSearch/Vault/SSH/LLM | Да. |
| `validation_error` | Нет. |
| `user_code_error` | Нет. |
| `security_error` | Нет. |
| `fuzzing_crash_found` | Нет. |
| `timeout` | Только если явно разрешено политикой job. |
| `unknown` | Нет, если `retryable=true` не задано явно. |

Retry создает новую запись `job_execution` с новым UUID и `attempt + 1`. Старое execution не переиспользуется.

## Cancel pipeline/job

Flow:

1. UI вызывает cancel endpoint.
2. Master проверяет `cancel` permission.
3. Master создает `cancellation_request`.
4. Для running job: `job_execution.status=canceling` и outbox event в `jobs.cancel`.
5. Executor завершает дочерние процессы в пределах `gracePeriodSeconds`.
6. Executor публикует `JOB_CANCELED` или `JOB_FINISHED/CANCELED`.
7. Master переводит execution в `canceled` и пересчитывает run.

Cancel pipeline отменяет active job и не удаляет artifacts.

## Protected deployment approval

Если job `deploy` ссылается на protected environment:

1. Master создает `job_execution(status=waiting_approval)`.
2. Master создает `deployment_approval(status=pending)`.
3. Kafka message не публикуется.
4. Пользователь с `approve_deployment` вызывает approve/reject.
5. При approve: execution -> `queued`, создается outbox `JobMessage`.
6. При reject/expire: execution -> `skipped` или `failed` согласно policy.

## Scheduler concurrency

- Один active scheduler-loop на `pipeline_run`.
- Блокировка run row или distributed lock на время пересчета graph.
- Ограничение `max_parallel_runs` на trigger/environment.
- Опциональный лимит active jobs по `jobType`.

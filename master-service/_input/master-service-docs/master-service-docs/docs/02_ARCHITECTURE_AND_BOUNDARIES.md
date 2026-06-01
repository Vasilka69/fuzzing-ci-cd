# 02. Архитектура и границы ответственности

## Компоненты

```text
React UI (Ant Design)
        |
        | REST / SSE
        v
master-service
        | PostgreSQL: config, runs, execution state, RBAC, audit, outbox/inbox
        | Kafka: job dispatch + optional results
        | OpenSearch: logs + optional executor events
        | Storage API: artifact download-url/metadata integration
        v
executor services: vcs, storage, build, fuzzing, deploy, script
```

## Ответственность master-service

- Принимает команды пользователя и trigger-события.
- Проверяет права доступа.
- Валидирует pipeline graph и job params.
- Создает `pipeline_run` и `job_execution`.
- Формирует `JobMessage` и пишет `outbox_event` в той же транзакции.
- Публикует сообщения в Kafka через outbox publisher.
- Принимает executor events из Kafka или OpenSearch.
- Дедуплицирует события и применяет state machine.
- Планирует следующие job.
- Регистрирует artifact metadata.
- Читает логи из OpenSearch.
- Отдает API/SSE в React UI.

## Ответственность executor-сервисов

- Подписываются на свой topic.
- Валидируют `JobMessage`.
- Создают workspace на `jobExecutionId`.
- Выполняют работу своего типа.
- Публикуют `JOB_LOG` в OpenSearch.
- Публикуют служебные events без больших логов.
- Не пишут в таблицы `pipeline_run`, `job_execution`, `artifact` и другие таблицы master-service.

## Типовой flow запуска

1. UI вызывает `POST /api/v1/pipelines/{id}/runs`.
2. Master проверяет `run` permission.
3. Master загружает pipeline/stage/job/template/dependency graph.
4. Master создает `pipeline_run(status=queued, correlation_id=uuid)`.
5. Master вычисляет стартовые job.
6. Для каждой ready job создает `job_execution(status=queued, attempt=1)`.
7. Master создает `outbox_event(topic=jobs.<type>, aggregate_id=jobExecutionId)`.
8. Outbox publisher отправляет Kafka message с key=`jobExecutionId`.
9. Executor публикует `JOB_RUNNING`.
10. Master переводит execution в `running` и шлет SSE `job-event`.
11. Executor публикует `JOB_LOG` documents и `JOB_FINISHED`.
12. Master применяет финальное событие, регистрирует artifacts и запускает scheduler-loop.
13. После отсутствия active/ready jobs master вычисляет финальный статус run.

## Транспорт executor events

| Режим | Как master получает служебные события | Как master получает логи |
| --- | --- | --- |
| `kafka` | Consumer `jobs.results` | OpenSearch `JOB_LOG` по `jobExecutionId` |
| `opensearch` | Poller индекса `cicd-executor-events`, игнорируя `JOB_LOG` | OpenSearch `JOB_LOG` по `jobExecutionId` |

В обоих режимах текстовые логи не должны сохраняться в PostgreSQL как большие поля.

## Связь executor-ов между собой

Executor-ы не вызывают друг друга напрямую. Связь идет через artifact URI и состояние pipeline:

```text
vcs -> source_snapshot_uri -> build -> build_artifact_uri -> fuzzing/deploy/script
```

Master отвечает за передачу нужных `inputs` в следующий job message.

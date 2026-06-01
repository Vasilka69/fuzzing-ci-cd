# 01. PRD master-service

## Цель

Реализовать `master-service` как центральный управляющий компонент CI/CD-системы и React frontend на Ant Design для работы пользователя с pipeline, запусками, логами, артефактами, deployment approvals и состоянием executor-ов.

## Scope master-service

Входит в scope:

- REST API `/api/v1` для frontend и внешних trigger/API runs.
- Полное владение PostgreSQL: pipeline config, runs, job executions, artifacts metadata, RBAC, outbox/inbox, audit, deployment approvals, executor cursor.
- Pipeline orchestration: stage order, DAG dependencies, `run_policy`, `condition`, `continue_on_error`, retry, timeout, cancel, protected deployment approval.
- Transactional outbox для публикации job/cancel messages в Kafka.
- Kafka consumer `jobs.results` при `EXECUTOR_EVENTS_TRANSPORT=kafka`.
- OpenSearch poller при `EXECUTOR_EVENTS_TRANSPORT=opensearch`.
- Чтение логов из OpenSearch по `jobExecutionId` и выдача во frontend через REST/SSE.
- Регистрация artifact metadata из executor events.
- RBAC и resource-level permissions.
- Audit событий пользователя и системных действий.
- Health/metrics для самого master-service.

Не входит в scope master-service:

- Выполнение build/fuzzing/deploy/script/VCS/storage работ.
- Прямое выполнение пользовательских команд.
- Хранение больших бинарных артефактов и больших текстовых логов в PostgreSQL.
- Прямой доступ frontend к PostgreSQL/Kafka/OpenSearch/storage.

## Основные пользователи

| Пользователь | Возможности |
| --- | --- |
| `ADMIN` | Управление пользователями, ролями, подключениями, секретами, окружениями, audit. |
| `DEVELOPER` | Создание/редактирование pipeline, запуск, просмотр логов и артефактов, отмена своих запусков. |
| `VIEWER` | Просмотр разрешенных pipeline, runs, logs, artifacts. |
| `OPERATOR` | Запуск/контроль deployment, approval protected environment. |

## MVP-функции

1. Создание pipeline из UI: folder -> pipeline -> stages -> jobs -> dependencies.
2. Выбор job template и заполнение параметров.
3. Запуск pipeline вручную.
4. Создание `pipeline_run` и `job_execution` в PostgreSQL.
5. Публикация job message в topic по `jobType`.
6. Прием `JOB_RUNNING`/`JOB_FINISHED` через Kafka или OpenSearch.
7. Обновление статусов `job_execution` и `pipeline_run`.
8. Чтение логов из OpenSearch по `jobExecutionId`.
9. Реaltime обновления UI через SSE.
10. Просмотр artifacts metadata и download URL.
11. Отмена pipeline/job.
12. Retry retryable job с новой попыткой и новым `jobExecutionId`.

## Ключевые инварианты

- Master-service — единственный компонент, который пишет бизнес-состояние в PostgreSQL.
- `job_execution.id` передается executor-у как `jobExecutionId`.
- Повторная доставка Kafka message не создает вторую попытку с тем же `jobExecutionId`.
- Новая retry-попытка получает новый `jobExecutionId` и `attempt + 1`.
- Kafka key для job/event/cancel равен `jobExecutionId`.
- `JOB_LOG` хранится в OpenSearch, а `JOB_FINISHED` не содержит больших логов.
- Все секреты передаются только ссылками `secretRef`/`credentialsRef`.

## Нефункциональные требования

- PostgreSQL операции вокруг запуска pipeline и outbox должны быть транзакционными.
- События executor-ов применяются идемпотентно через `inbox_event`/deduplication.
- Late event после финального статуса сохраняется как audit/diagnostic, но не меняет бизнес-статус.
- REST API возвращает единый формат ошибок с `correlationId`.
- SSE должен иметь heartbeat и fallback на polling.
- Настройки transport/logs должны быть externalized через env/application.yml.

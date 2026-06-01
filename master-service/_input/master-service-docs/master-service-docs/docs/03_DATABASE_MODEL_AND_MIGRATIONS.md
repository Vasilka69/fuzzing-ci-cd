# 03. Модель БД и миграции

## Baseline миграции

Использовать предоставленные миграции как baseline:

```text
db/migration/V0001__schema.sql
db/migration/V0002__data.sql
```

`V0001__schema.sql` создает UUID extension, таблицы, constraints и индексы. `V0002__data.sql` наполняет стартовые роли, permissions, deployment environments, job templates, secret refs и external connections.

## Группы таблиц

| Группа | Таблицы | Что хранится |
| --- | --- | --- |
| Пользователи/RBAC | `app_user`, `user_role`, `user_role_assignment`, `permission_assignment` | Учетные записи, роли и ресурсные права. |
| Pipeline config | `folder`, `pipeline`, `stage`, `job_template`, `job`, `job_params`, `job_dependency`, `trigger` | Описание pipeline и templates. |
| Runtime state | `trigger_event`, `pipeline_run`, `job_execution`, `cancellation_request` | Запуски, попытки, trigger deduplication, отмена. |
| Artifacts/storage | `artifact`, `storage_object` | Metadata объектов, URI, checksum, size, retention. |
| Integrations | `secret_ref`, `external_connection` | Ссылки на секреты и внешние системы. |
| Deployment | `deployment_environment`, `deployment_release`, `deployment_approval` | Окружения, releases, approvals. |
| Observability | `executor_heartbeat`, `audit_event`, `outbox_event`, `inbox_event`, `executor_event_cursor` | Heartbeat, audit, надежная публикация/прием, cursor OpenSearch. |

## Master-service ownership

- Все записи в этих таблицах создает/изменяет только master-service.
- Executor-ы возвращают события и metadata, но не вставляют строки в БД.
- Artifact binary/log payload не хранится в PostgreSQL.
- `artifact` содержит только metadata: `artifact_type`, `name`, `uri`, `size_bytes`, `sha256`, `metadata`, связи с `pipeline_run`/`job_execution`.

## Важные constraints для кода

### Job types

```text
vcs, storage, build, fuzzing, deploy, script
```

### Job execution statuses

```text
queued, running, waiting_approval, success, failed, timeout,
canceling, canceled, retrying, skipped
```

### Pipeline run statuses

```text
queued, running, waiting_approval, success, failed, canceling, canceled, timeout
```

### Error types

```text
validation_error, user_code_error, infrastructure_error, timeout,
canceled, security_error, fuzzing_crash_found, cancel_failed, unknown
```

### Permissions

```text
view, edit, run, cancel, approve_deployment,
manage_secrets, manage_connections, admin
```

## Transactional outbox

При запуске job master делает в одной PostgreSQL транзакции:

1. Создает/обновляет `pipeline_run`.
2. Создает `job_execution`.
3. Создает `outbox_event` с payload `JobMessage`.

Outbox publisher отдельно читает `pending` events, публикует в Kafka и переводит их в `published`. При ошибке увеличивает `attempts`, сохраняет `last_error`, может перевести в `failed` после лимита.

## Inbox / deduplication

Для входящих событий master-service должен дедуплицировать:

- Kafka event: по `messageId` + `consumerName`.
- OpenSearch event: по `sourceDocumentId` + `consumerName`.
- Дополнительная защита: не применять повторно статусообразующее событие с тем же `jobExecutionId + eventType + attempt`, если оно уже применено.

## Cursor OpenSearch

`executor_event_cursor` хранит:

- `consumer_name`
- `event_source`
- `index_name`
- `last_ingested_at`
- `last_document_id`
- `metadata`

Poller читает `cicd-executor-events` по `ingestedAt ASC`, `documentId ASC` через `search_after` и после успешной страницы сохраняет cursor.

## Стартовые данные из V0002

### Roles

```text
ADMIN, DEVELOPER, VIEWER, OPERATOR
```

### Deployment environments

- `development` — unprotected.
- `testing` — unprotected.
- `production` — protected, requires operator role/approval, max parallel deployments = 1.

### Job templates

| Type | Templates |
| --- | --- |
| `vcs` | `vcs/git`, `vcs/mercurial` |
| `storage` | `storage/source-snapshot`, `storage/promote-artifact`, `storage/cleanup` |
| `build` | `build/maven`, `build/gradle`, `build/javac`, `build/gcc` |
| `fuzzing` | `fuzzing/afl-llm` |
| `deploy` | `deploy/ssh-bash`, `deploy/windows-cmd`, `deploy/file-copy`, `deploy/docker`, `deploy/docker-compose`, `deploy/systemd` |
| `script` | `script/bash`, `script/cmd` |

## Entity implementation notes

- JSONB поля маппить на `JsonNode` или `Map<String, Object>`.
- Для `timestamptz` использовать `OffsetDateTime`.
- UUID — `java.util.UUID`.
- Не использовать Java enum ordinal; хранить строковые значения.
- Все repository-запросы на scheduler/outbox должны быть транзакционными и учитывать concurrency.

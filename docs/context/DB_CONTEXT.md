# Контекст БД и миграций для executor-разработки

Дата: 2026-05-29

Файлы миграций использованы как источник контрактного контекста master-service. Executor-ы не должны напрямую писать в эти таблицы, но обязаны уважать их поля, статусы и enum-значения.

## Таблицы, важные для контрактов

- `pipeline_run` — запуск pipeline, содержит `correlation_id`, статус и summary.
- `job_execution` — конкретная попытка выполнения job. `id` соответствует внешнему `jobExecutionId`.
- `job_template` — типовые шаблоны executor job.
- `artifact` — metadata артефактов, создаваемых executor-ами.
- `storage_object` — metadata объектов внутреннего хранилища.
- `secret_ref`, `external_connection` — ссылки на секреты и внешние системы.
- `outbox_event`, `inbox_event` — надежная публикация и дедупликация событий на стороне master.
- `executor_event_cursor` — cursor чтения OpenSearch master-service.
- `deployment_environment`, `deployment_release`, `deployment_approval` — deployment-specific модель.

## Job types из миграций

- `vcs`
- `storage`
- `build`
- `fuzzing`
- `deploy`
- `script`

## Job execution statuses

- `queued`
- `running`
- `waiting_approval`
- `success`
- `failed`
- `timeout`
- `canceling`
- `canceled`
- `retrying`
- `skipped`

Executor публикует внешние uppercase statuses, master нормализует их в эти значения.

## Error types

- `validation_error`
- `user_code_error`
- `infrastructure_error`
- `timeout`
- `canceled`
- `security_error`
- `fuzzing_crash_found`
- `cancel_failed`
- `unknown`

## Artifact types

- `source_snapshot`
- `build_artifact`
- `fuzzing_report`
- `crash_case`
- `hang_case`
- `corpus`
- `log`
- `deployment_manifest`
- `script_output`
- `release_package`
- `other`

## Шаблоны из стартовых данных

### VCS

- `vcs/git`
- `vcs/mercurial`

### Storage

- `storage/source-snapshot`
- `storage/promote-artifact`
- `storage/cleanup`

### Build

- `build/maven`
- `build/gradle`
- `build/javac`
- `build/gcc`

### Fuzzing

- `fuzzing/afl-llm`

### Deploy

- `deploy/ssh-bash`
- `deploy/windows-cmd`
- `deploy/file-copy`
- `deploy/docker`
- `deploy/docker-compose`
- `deploy/systemd`

### Script

- `script/bash`
- `script/cmd`

## Правила для executor-ов

- Не добавлять прямую зависимость на Flyway migrations master-service, если задача не про contract tests.
- Не сохранять `job_execution` самостоятельно.
- Не генерировать новый `jobExecutionId`; брать из job message.
- Artifact URI должен включать `pipelineRunId`, `jobId`, `jobExecutionId`.
- В result event возвращать artifact manifest, но не пытаться вставлять строки в `artifact` таблицу.

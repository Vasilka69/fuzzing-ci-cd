# 00. Целевой список файлов проекта master-service + React UI

Документ описывает список файлов, который удобно использовать как основу для генерации проекта. Это не просто дерево директорий: у каждого файла зафиксирована функция, чтобы AI-agent или разработчик понимал, что именно реализовывать.

## 1. Корень репозитория

```text
.
├── README.md
├── AGENTS.md
├── TASKS.md
├── pom.xml
├── mvnw / mvnw.cmd
├── .mvn/wrapper/maven-wrapper.properties
├── .env.example
├── docker-compose.yml
├── master-service/
├── frontend/
├── common/
│   └── cicd-contracts/
├── db/migration/
├── deploy/
│   ├── docker/
│   └── k8s/
└── docs/
```

| Файл | Назначение |
| --- | --- |
| `README.md` | Быстрый старт: инфраструктура, запуск backend/frontend, demo pipeline. |
| `AGENTS.md` | Правила для AI-agent: не ломать контракты, не хранить секреты, master владеет БД. |
| `TASKS.md` | Backlog master-service и UI, не executor-слоя. |
| `pom.xml` | Maven parent для backend-модулей, Java 21, Spring Boot, dependency management. |
| `.env.example` | Переменные окружения без секретов: PostgreSQL, Kafka, OpenSearch, Vault, API URL. |
| `docker-compose.yml` | Локальная инфраструктура: PostgreSQL, Kafka, OpenSearch, optional storage mock, master-service, frontend. |

## 2. Backend: `master-service`

```text
master-service/
├── pom.xml
├── Dockerfile
├── src/main/resources/
│   ├── application.yml
│   ├── application-local.yml
│   └── logback-spring.xml
├── src/main/java/ru/diplom/cicd/master/
│   ├── MasterServiceApplication.java
│   ├── config/
│   ├── domain/
│   │   ├── entity/
│   │   ├── enums/
│   │   └── value/
│   ├── repository/
│   ├── service/
│   ├── orchestration/
│   ├── messaging/
│   │   ├── contract/
│   │   ├── kafka/
│   │   ├── outbox/
│   │   └── inbox/
│   ├── opensearch/
│   ├── api/
│   │   ├── controller/
│   │   ├── dto/
│   │   └── mapper/
│   ├── security/
│   ├── sse/
│   ├── scheduler/
│   ├── audit/
│   └── exception/
└── src/test/java/ru/diplom/cicd/master/
```

### 2.1. Конфигурация

| Файл | Функционал |
| --- | --- |
| `MasterServiceApplication.java` | Entry point Spring Boot. |
| `config/DatabaseConfig.java` | Настройки PostgreSQL, транзакций, JPA/Flyway. |
| `config/KafkaConfig.java` | Producer для job topics, consumer для `jobs.results`, DLQ settings. |
| `config/OpenSearchConfig.java` | Клиент OpenSearch и параметры индекса `cicd-executor-events`. |
| `config/SseConfig.java` | Настройки SSE: heartbeat, timeout, reconnect policy. |
| `config/SecurityConfig.java` | Authentication, authorization, CORS для React UI. |
| `config/AppProperties.java` | Typed configuration: topics, retry, scheduler, transport, limits. |

### 2.2. Domain entity: прямое соответствие миграциям

| Entity | Таблица | Назначение |
| --- | --- | --- |
| `AppUserEntity` | `app_user` | Пользователь системы. |
| `UserRoleEntity` | `user_role` | Роль: `ADMIN`, `DEVELOPER`, `VIEWER`, `OPERATOR`. |
| `UserRoleAssignmentEntity` | `user_role_assignment` | Связь пользователя и ролей. |
| `PermissionAssignmentEntity` | `permission_assignment` | Resource-level RBAC. |
| `FolderEntity` | `folder` | Дерево папок pipeline. |
| `PipelineEntity` | `pipeline` | Шаблон pipeline. |
| `StageEntity` | `stage` | Stage внутри pipeline. |
| `JobTemplateEntity` | `job_template` | Шаблоны `vcs/git`, `build/maven`, `fuzzing/afl-llm` и т.д. |
| `JobEntity` | `job` | Job внутри stage. |
| `JobParamsEntity` | `job_params` | JSONB параметры job. |
| `JobDependencyEntity` | `job_dependency` | DAG-зависимости job. |
| `TriggerEntity` | `trigger` | Manual/VCS/schedule/API trigger config. |
| `TriggerEventEntity` | `trigger_event` | Дедупликация внешних trigger-событий. |
| `PipelineRunEntity` | `pipeline_run` | Конкретный запуск pipeline. |
| `JobExecutionEntity` | `job_execution` | Конкретная попытка job; `id` = `jobExecutionId`. |
| `CancellationRequestEntity` | `cancellation_request` | Запросы отмены pipeline/job. |
| `ArtifactEntity` | `artifact` | Metadata артефактов, не бинарные данные. |
| `StorageObjectEntity` | `storage_object` | Metadata physical storage object. |
| `SecretRefEntity` | `secret_ref` | Ссылка на секрет без значения секрета. |
| `ExternalConnectionEntity` | `external_connection` | VCS, storage, LLM, Vault, deploy target. |
| `DeploymentEnvironmentEntity` | `deployment_environment` | Окружение deployment и protection policy. |
| `DeploymentReleaseEntity` | `deployment_release` | Release metadata, manifest, rollback status. |
| `DeploymentApprovalEntity` | `deployment_approval` | Approval для protected deployment. |
| `ExecutorHeartbeatEntity` | `executor_heartbeat` | Состояние executor instances. |
| `AuditEventEntity` | `audit_event` | Аудит действий пользователя и системы. |
| `OutboxEventEntity` | `outbox_event` | Transactional outbox для публикации job/cancel messages. |
| `InboxEventEntity` | `inbox_event` | Deduplication входящих Kafka/OpenSearch событий. |
| `ExecutorEventCursorEntity` | `executor_event_cursor` | Cursor чтения OpenSearch poller-ом. |

### 2.3. Enum/value классы

| Файл | Значения/функция |
| --- | --- |
| `JobType.java` | `vcs`, `storage`, `build`, `fuzzing`, `deploy`, `script`. |
| `JobExecutionStatus.java` | `queued`, `running`, `waiting_approval`, `success`, `failed`, `timeout`, `canceling`, `canceled`, `retrying`, `skipped`. |
| `PipelineRunStatus.java` | `queued`, `running`, `waiting_approval`, `success`, `failed`, `canceling`, `canceled`, `timeout`. |
| `ExecutorEventType.java` | `JOB_ACCEPTED`, `JOB_RUNNING`, `JOB_PROGRESS`, `JOB_ARTIFACT`, `JOB_LOG`, `JOB_FINISHED`, `JOB_SKIPPED`, `JOB_CANCELED`, `JOB_HEARTBEAT`. |
| `ExecutorStatus.java` | `QUEUED`, `RUNNING`, `SUCCESS`, `FAILED`, `TIMEOUT`, `CANCELING`, `CANCELED`, `RETRYING`, `SKIPPED`, `WAITING_APPROVAL`. |
| `ErrorType.java` | `validation_error`, `user_code_error`, `infrastructure_error`, `timeout`, `canceled`, `security_error`, `fuzzing_crash_found`, `cancel_failed`, `unknown`. |
| `Permission.java` | `view`, `edit`, `run`, `cancel`, `approve_deployment`, `manage_secrets`, `manage_connections`, `admin`. |
| `ArtifactType.java` | `source_snapshot`, `build_artifact`, `fuzzing_report`, `crash_case`, `hang_case`, `corpus`, `log`, `deployment_manifest`, `script_output`, `release_package`, `other`. |

### 2.4. Repository слой

| Файл | Функционал |
| --- | --- |
| `PipelineRepository.java` | CRUD pipeline, загрузка pipeline со stage/job graph. |
| `StageRepository.java` | CRUD stage и reorder. |
| `JobRepository.java` | CRUD job, поиск по stage/pipeline. |
| `JobTemplateRepository.java` | Поиск активных templates, валидация `templatePath/jobType`. |
| `JobDependencyRepository.java` | Чтение/запись DAG edges. |
| `PipelineRunRepository.java` | Список запусков, active runs, status filters. |
| `JobExecutionRepository.java` | Попытки job, active executions, latest attempt. |
| `OutboxEventRepository.java` | Захват pending outbox events с блокировками. |
| `InboxEventRepository.java` | Дедупликация по messageId/sourceDocumentId/consumer. |
| `ArtifactRepository.java` | Metadata artifacts по run/execution. |
| `ExecutorEventCursorRepository.java` | Cursor OpenSearch poller. |
| `PermissionAssignmentRepository.java` | Effective permissions. |

### 2.5. Service слой

| Файл | Функционал |
| --- | --- |
| `PipelineService.java` | CRUD pipeline, stage, job, params, dependencies. |
| `PipelineValidationService.java` | Проверка graph, циклов, template compatibility, required params. |
| `PipelineRunService.java` | Создание запуска и первичный расчет ready jobs. |
| `JobExecutionService.java` | Создание попытки, обновление статуса, retry/cancel. |
| `JobTemplateService.java` | Выдача templates в UI и валидация параметров. |
| `ArtifactService.java` | Регистрация artifact metadata из events. |
| `TriggerService.java` | Manual/API/VCS/schedule trigger flow и idempotency. |
| `DeploymentApprovalService.java` | Protected environment approval/reject/expire. |
| `ExecutorMonitoringService.java` | Состояние executor instances и heartbeat. |
| `PermissionService.java` | RBAC и resource checks. |
| `AuditService.java` | Запись audit events. |

### 2.6. Orchestration

| Файл | Функционал |
| --- | --- |
| `PipelineOrchestrator.java` | Главный scheduler-loop: после старта и после status event вычисляет готовые job. |
| `ExecutionGraphBuilder.java` | Построение DAG из stage order, `run_policy`, `job_dependency`. |
| `ExecutionGraphValidator.java` | Циклы, ссылки на удаленные job, future stage warning. |
| `ReadyJobResolver.java` | Правила `on_success`, `on_failure`, `always`, `continue_on_error`. |
| `PipelineStateReducer.java` | Вычисление финального статуса `pipeline_run`. |
| `JobStateMachine.java` | Допустимые переходы `job_execution`. |
| `RetryPolicyService.java` | `error.retryable`, `maxAttempts`, exponential backoff, новая `job_execution`. |
| `TimeoutWatchdog.java` | Поиск зависших `running/canceling` job и перевод в timeout/canceled. |
| `ApprovalGate.java` | Перевод deploy job в `waiting_approval` до подтверждения. |

### 2.7. Messaging / Kafka / Outbox / Inbox

| Файл | Функционал |
| --- | --- |
| `contract/JobMessage.java` | DTO внешнего job message, JSON camelCase. |
| `contract/ExecutorEventMessage.java` | DTO события executor-а. |
| `contract/CancelCommand.java` | DTO команды отмены. |
| `contract/ArtifactDescriptor.java` | URI, type, checksum, size, metadata. |
| `contract/ExecutorError.java` | `code`, `type`, `retryable`, `message`, `details`. |
| `JobMessageFactory.java` | Сборка job message из `job_execution`, `job`, template, params, inputs. |
| `KafkaTopicResolver.java` | `vcs -> jobs.vcs`, `build -> jobs.build` и т.д. |
| `OutboxService.java` | Создает outbox в одной транзакции с `pipeline_run/job_execution`. |
| `OutboxPublisher.java` | Публикует pending outbox events в Kafka, key=`jobExecutionId`. |
| `ExecutorResultConsumer.java` | Kafka consumer `jobs.results` при `transport=kafka`. |
| `InboxDeduplicationService.java` | Проверяет `messageId`/`jobExecutionId+eventType+attempt`. |
| `ExecutorEventService.java` | Применяет event к БД, artifact, SSE, orchestrator. |
| `DeadLetterService.java` | Учет DLQ и диагностика ошибочных сообщений. |

### 2.8. OpenSearch и SSE

| Файл | Функционал |
| --- | --- |
| `opensearch/ExecutorEventDocument.java` | Модель документа индекса `cicd-executor-events`. |
| `opensearch/OpenSearchExecutorEventPoller.java` | Читает служебные events через `search_after`, игнорирует `JOB_LOG`. |
| `opensearch/OpenSearchHistoryLogService.java` | Читает `JOB_LOG` по `jobExecutionId`, объединяет chunks. |
| `opensearch/OpenSearchLogQueryService.java` | Cursor/tail queries для UI. |
| `sse/SseSessionRegistry.java` | Хранит подписки frontend. |
| `sse/JobEventSsePublisher.java` | Публикует `job-event` и `job-log`. |
| `sse/SseHeartbeatScheduler.java` | Heartbeat для удержания соединений. |

### 2.9. REST controllers

| Файл | Основные endpoints |
| --- | --- |
| `AuthController.java` | `POST /api/v1/auth/login`, текущий user/permissions. |
| `FolderController.java` | CRUD folders. |
| `PipelineController.java` | CRUD pipeline, stage/job graph. |
| `PipelineStructureController.java` | Stage/job/dependency editing. |
| `JobTemplateController.java` | Templates и validate params. |
| `PipelineRunController.java` | Run/list/details/graph/cancel/retry. |
| `JobExecutionController.java` | Execution details/cancel/retry/logs/artifacts. |
| `LogStreamController.java` | `GET /api/v1/logs/stream`. |
| `ArtifactController.java` | Artifact metadata/download-url. |
| `TriggerController.java` | VCS/API trigger endpoints. |
| `DeploymentController.java` | Environments, releases, approvals. |
| `ConnectionController.java` | External connections. |
| `SecretRefController.java` | Secret refs без значений. |
| `ExecutorController.java` | Executor monitoring. |
| `AuditController.java` | Audit events. |
| `PermissionController.java` | Resource-level permissions. |

## 3. Frontend: React + Ant Design

```text
frontend/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── .env.example
├── src/
│   ├── main.tsx
│   ├── App.tsx
│   ├── routes.tsx
│   ├── api/
│   ├── app/
│   ├── features/
│   │   ├── auth/
│   │   ├── folders/
│   │   ├── pipelines/
│   │   ├── pipelineRuns/
│   │   ├── jobTemplates/
│   │   ├── logs/
│   │   ├── artifacts/
│   │   ├── deployments/
│   │   ├── executors/
│   │   └── settings/
│   ├── shared/
│   │   ├── components/
│   │   ├── hooks/
│   │   ├── types/
│   │   └── utils/
│   └── styles/
└── tests/
```

| Файл/папка | Функционал |
| --- | --- |
| `api/client.ts` | Axios/fetch client, auth token, base URL, error normalization. |
| `api/sse.ts` | EventSource client, reconnect/backoff, `Last-Event-ID`. |
| `features/pipelines/PipelineListPage.tsx` | Список pipeline, фильтры, folders, create/run actions. |
| `features/pipelines/PipelineDesignerPage.tsx` | Конструктор stage/job/dependency graph на AntD forms. |
| `features/jobTemplates/JobTemplateForm.tsx` | Dynamic form по `paramsTemplate`. |
| `features/pipelineRuns/PipelineRunPage.tsx` | Graph/status, active job, logs/artifacts tabs. |
| `features/logs/LogViewer.tsx` | Tail logs через REST + realtime SSE append. |
| `features/artifacts/ArtifactTable.tsx` | Список artifacts и download-url. |
| `features/deployments/ApprovalPanel.tsx` | Approve/reject protected deployment. |
| `features/executors/ExecutorStatusPage.tsx` | Состояние executor instances. |
| `shared/components/StatusTag.tsx` | Единый AntD Tag для статусов. |
| `shared/components/ErrorState.tsx` | Единый показ ошибок API. |

## 4. DB migrations

```text
db/migration/
├── V0001__schema.sql
└── V0002__data.sql
```

Использовать предоставленные миграции как baseline. При необходимости переименовать файлы без суффиксов копий и подключить Flyway из `master-service`.

## 5. Tests

```text
master-service/src/test/java/.../
├── unit/
├── contract/
├── integration/
└── e2e/
frontend/tests/
├── unit/
└── e2e/
```

Минимальные тесты: state machine, graph builder, outbox transaction, inbox deduplication, Kafka DTO serialization, OpenSearch poller cursor, REST permissions, SSE publish, React pipeline run page.

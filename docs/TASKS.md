# TASKS: Верхнеуровневый план реализации

## Правила выполнения задач агентом

Каждая задача должна завершаться проверяемым результатом. Предпочтительный формат выполнения: маленький vertical slice, который можно собрать и протестировать.

Статусы:

- `[ ]` not started
- `[~]` in progress
- `[x]` done
- `[!]` blocked/risk

## Milestone 0. Repository foundation

### SYS-001. Создать Maven multi-module skeleton

Цель: подготовить структуру Maven проекта.

Готово, когда:

- есть root `pom.xml`;
- перечислены common modules и service modules;
- `mvn validate` проходит;
- dependency/plugin management централизован.

Проверки:

```bash
mvn -q validate
```

### SYS-002. Создать common-contracts

Цель: единый набор DTO для job/result envelope.

Состав:

- `JobMessageEnvelope`
- `JobResultEvent`
- `ArtifactDescriptor`
- `ResourceLimits`
- `WorkspacePolicy`
- `StructuredError`
- enums: `JobType`, `EventType`, `JobStatus`, `ErrorType`

Готово, когда DTO используются минимум двумя сервисами и покрыты serialization tests.

### SYS-003. Создать common-kafka

Цель: переиспользуемая Kafka инфраструктура executor'ов.

Состав:

- consumer configuration;
- producer configuration;
- result publisher;
- dead-letter publisher;
- message validation;
- idempotency hook interface.

Готово, когда тестовый executor может принять message и отправить result.

### SYS-004. Создать common-storage-client

Цель: общий интерфейс storage URI upload/download.

Состав:

- `StorageClient`;
- local filesystem implementation for dev/test;
- checksum calculation;
- metadata model.

### SYS-005. Создать common-observability

Цель: единый logging/tracing context.

Состав:

- MDC keys: `correlation_id`, `job_execution_id`, `worker_id`, `job_type`;
- log redaction utilities;
- metrics names convention.

### SYS-006. Создать common-testing

Цель: test utilities для contract/integration tests.

Состав:

- sample job envelope factory;
- embedded/local storage fixture;
- Kafka test fixture;
- JSON schema assertions.

## Milestone 1. Executor framework

### SYS-010. Реализовать базовый executor lifecycle

Цель: единая orchestration-обертка для всех сервисов.

Готово, когда сервису остается реализовать только `validateSpecificParams` и `executeJob`.

### SYS-011. Реализовать workspace manager

Состав:

- workspace per `job_execution_id`;
- cleanup policies;
- preserve-on-failure;
- max workspace size guard.

### SYS-012. Реализовать timeout/process runner abstraction

Состав:

- запуск process без shell injection;
- stdout/stderr capture;
- timeout kill tree;
- exit code mapping.

### SYS-013. Реализовать secret resolver abstraction

Состав:

- env provider;
- file provider;
- k8s-secret placeholder/adapter;
- redaction registration.

## Milestone 2. Service MVPs

### SYS-020. VCS service MVP

Ссылка: `docs/services/vcs-service/TASKS.md`.

Приемка: Git checkout по ref, source snapshot artifact, masked credentials.

### SYS-021. Storage service MVP

Ссылка: `docs/services/storage-service/TASKS.md`.

Приемка: save/copy/promote/cleanup operations через storage URI.

### SYS-022. Build service MVP

Ссылка: `docs/services/build-service/TASKS.md`.

Приемка: Maven, Gradle, Javac, GCC через tool+args, artifacts upload.

### SYS-023. Fuzzing service MVP

Ссылка: `docs/services/fuzzing-service/TASKS.md`.

Приемка: adapter к готовому fuzzing engine, fake mode, real engine smoke test, report artifacts.

### SYS-024. Deploy service MVP

Ссылка: `docs/services/deploy-service/TASKS.md`.

Приемка: release_id, manifest, dry-run/local mode, один реальный deployment mode.

### SYS-025. Script service MVP

Ссылка: `docs/services/script-service/TASKS.md`.

Приемка: Bash/cmd execution в sandbox с artifacts/logs.

## Milestone 3. Docker and Kubernetes

### SYS-030. Dockerfile для каждого сервиса

Готово, когда каждый service image собирается из корня с module-specific Dockerfile.

### SYS-031. Общий docker-compose для локального запуска

Состав:

- Kafka;
- PostgreSQL если нужен для test/dev;
- MinIO/local storage;
- все executor services по профилю.

### SYS-032. Kubernetes manifests

Состав:

- namespace;
- configmaps;
- deployments;
- services where needed;
- resource requests/limits;
- probes;
- securityContext;
- secrets references.

Проверка:

```bash
kubectl apply --dry-run=client -f deploy/k8s
```

## Milestone 4. Cross-service validation

### SYS-040. Contract test matrix

Покрыть:

- unknown schema version;
- invalid job type;
- missing required fields;
- retryable infrastructure error;
- non-retryable validation/user error;
- timeout;
- artifact descriptor format.

### SYS-041. End-to-end executor chain smoke test

Цель: без master-service вручную опубликовать Kafka messages и проверить result events.

Сценарий:

1. VCS checkout или prepared source snapshot.
2. Build artifact.
3. Fuzzing report.
4. Storage promote.
5. Deploy dry-run.
6. Script post-check.

### SYS-042. Security review checklist

Проверить:

- no secrets in logs;
- no shell injection in build;
- script sandbox limits;
- fuzzing limits;
- deployment idempotency;
- no root container where avoidable;
- dependency review.

## Milestone 5. Documentation for agents

### SYS-050. Обновить README

README должен содержать:

- project overview;
- module map;
- local dev commands;
- test commands;
- Docker/K8s commands;
- scope exclusions.

### SYS-051. Поддерживать PRD/TASKS актуальными

Любая смена контракта, env var, artifact format или lifecycle шага требует обновления соответствующего PRD/TASKS.

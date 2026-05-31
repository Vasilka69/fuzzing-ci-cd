# TASKS — верхнеуровневый backlog executor-слоя

Дата: 2026-05-29

Задачи разделены на уровни. Уровни нужно проходить последовательно: сначала минимальная инфраструктура и MVP, затем дипломно-достаточная функциональность, затем hardening. Задачи, похожие на крупный production-продукт и избыточные для магистерской работы, вынесены в конец.

## Легенда

- `[MVP]` — минимально необходимое для демонстрации работоспособности executor-слоя.
- `[DIPLOMA]` — желательно для полноценной защиты и устойчивой демонстрации.
- `[HARDENING]` — качество, безопасность, надежность, но без разрастания в enterprise-platform.
- `[OPTIONAL/PROD]` — избыточно для магистерской работы; делать только при запасе времени.

## Уровень 0. Bootstrap репозитория

- [x] `BOOT-001 [MVP]` Создать Maven parent project с modules: `common/cicd-contracts`, `common/cicd-executor-core`, `common/cicd-test-support`, `services/*`.
  - Готово, когда: `./mvnw -q -DskipTests package` проходит на пустых модулях.
- [x] `BOOT-002 [MVP]` Добавить Maven Wrapper, Maven Enforcer, dependency/plugin management, Java 21, Spring Boot BOM.
  - Готово, когда: сборка падает на неверной Java/Maven версии с понятным русским сообщением в docs.
- [x] `BOOT-003 [MVP]` Ввести code style: formatter/Spotless, Checkstyle или минимальный ruleset.
- [x] `BOOT-004 [MVP]` Создать общий package naming: `ru.<org>.cicd.<module>`; `<org>` заменить на выбранный namespace проекта.
- [x] `BOOT-005 [MVP]` Создать `docker-compose.yml` для Kafka, OpenSearch, PostgreSQL, storage backend заглушки.
- [x] `BOOT-006 [MVP]` Добавить `.env.example` без секретов.
- [x] `BOOT-007 [MVP]` Добавить PR template и чеклисты из `docs/checklists`.
  - Пропущено: задача временно отменена, PR template и дополнительные чеклисты сейчас не добавляем.
- [x] `BOOT-008 [DIPLOMA]` Добавить CI workflow: build/test, dependency scan, Docker build smoke check.
  - Пропущено: задача временно отменена.

## Уровень 1. Common contracts и executor runtime

- [x] `CORE-001 [MVP]` Реализовать DTO `JobMessage`, `ExecutorEventMessage`, `ArtifactDescriptor`, `ExecutorError`, `ResourceLimits`, `WorkspacePolicy`, `SandboxPolicy`.
- [x] `CORE-002 [MVP]` Зафиксировать enums `JobType`, `EventType`, `ExecutionStatus`, `ErrorType` согласно `AGENTS.md`.
- [x] `CORE-003 [MVP]` Добавить JSON serialization tests: camelCase, `schemaVersion=1`, backward-compatible unknown fields ignored only where безопасно.
- [x] `CORE-004 [MVP]` Реализовать `ExecutorEventPublisher` interface и Kafka implementation.
- [x] `CORE-005 [MVP]` Реализовать `ExecutorLogPublisher` interface и OpenSearch implementation.
- [x] `CORE-006 [MVP]` Реализовать `SecretRedactor` и unit tests на маскирование token/password/private key.
- [x] `CORE-007 [MVP]` Реализовать `WorkspaceManager`: create, resolve paths, cleanup, preserve-on-failure.
- [x] `CORE-008 [MVP]` Реализовать `StorageClient` interface + local/http adapter stub.
- [x] `CORE-009 [MVP]` Реализовать общий `ExecutorJobHandler` pipeline: validate -> running event -> execute -> logs -> artifacts -> finished event -> cleanup.
- [x] `CORE-010 [DIPLOMA]` Добавить idempotency guard по `jobExecutionId` через marker/state file в workspace/artifact namespace.
- [x] `CORE-011 [DIPLOMA]` Добавить `SandboxPolicyValidator`, который запрещает privileged, host network, Docker socket, hostPath.
- [x] `CORE-012 [DIPLOMA]` Добавить process runner с timeout, grace period, stdout/stderr chunking.
- [x] `CORE-013 [DIPLOMA]` Добавить OpenSearch document schema tests.
- [ ] `CORE-014 [HARDENING]` Добавить OpenTelemetry trace context propagation через Kafka headers.

## Уровень 2. Каркас всех executor-сервисов

- [x] `SVC-001 [MVP]` Для каждого сервиса создать Spring Boot module, `application.yml`, health endpoint, Kafka consumer config.
- [x] `SVC-002 [MVP]` Для каждого сервиса добавить local profile и test profile.
- [x] `SVC-003 [MVP]` Для каждого сервиса добавить Dockerfile multi-stage build.
- [x] `SVC-004 [MVP]` Для каждого сервиса добавить Kubernetes Deployment/ConfigMap/ServiceAccount и, где нужен HTTP API, Service.
- [x] `SVC-005 [MVP]` Для каждого сервиса добавить service-scoped `AGENTS.md`.
- [x] `SVC-006 [DIPLOMA]` Для каждого сервиса добавить structured logging с `jobExecutionId`, `correlationId`, `sourceService`.
- [x] `SVC-007 [DIPLOMA]` Для каждого сервиса добавить metrics: job count, duration, failures by errorType, active jobs.
  - Готово: метрики реализованы централизованно в `cicd-executor-core`, endpoint `/actuator/metrics` открыт во всех executor-сервисах.

## Уровень 3. MVP по сервисам

- [x] `VCS-001..` Выполнить MVP задачи из `docs/tasks/TASKS-vcs-service.md`.
  - Готово: все MVP задачи `VCS-001`..`VCS-005` закрыты в `docs/tasks/TASKS-vcs-service.md`.
- [ ] `STOR-001..` Выполнить MVP задачи из `docs/tasks/TASKS-storage-service.md`.
- [ ] `BUILD-001..` Выполнить MVP задачи из `docs/tasks/TASKS-build-service.md`.
- [ ] `FUZZ-001..` Выполнить MVP задачи из `docs/tasks/TASKS-fuzzing-service.md`.
- [ ] `DEPLOY-001..` Выполнить MVP задачи из `docs/tasks/TASKS-deploy-service.md`.
- [ ] `SCRIPT-001..` Выполнить MVP задачи из `docs/tasks/TASKS-script-service.md`.

## Уровень 4. Demo pipeline без UI/master

- [ ] `DEMO-001 [MVP]` Создать mock master publisher, который публикует job messages в topics по заранее заданному demo pipeline.
- [ ] `DEMO-002 [MVP]` Создать demo artifacts/sample repositories для VCS -> Build -> Fuzzing -> Deploy/Script.
- [ ] `DEMO-003 [MVP]` Создать README demo-сценария с командами запуска.
- [ ] `DEMO-004 [DIPLOMA]` Создать docker compose demo, где все executor-ы поднимаются вместе с Kafka/OpenSearch/storage.
- [ ] `DEMO-005 [DIPLOMA]` Сохранить sample executor events/logs и показать, что `JOB_FINISHED.logs = null`, а текст есть в `JOB_LOG`.

## Уровень 5. Дипломно-достаточная полнота

- [ ] `DIP-001 [DIPLOMA]` Поддержать все шаблоны из `V0002__data.sql` хотя бы через validation + documented unsupported mode.
- [ ] `DIP-002 [DIPLOMA]` Реализовать retry handling для transient infrastructure errors.
- [ ] `DIP-003 [DIPLOMA]` Реализовать cancel protocol на уровне executor runtime: stop process/container, publish `JOB_CANCELED`.
- [ ] `DIP-004 [DIPLOMA]` Добавить contract tests на все service result payloads.
- [ ] `DIP-005 [DIPLOMA]` Добавить k8s manifests с securityContext, resources, probes для всех сервисов.
- [ ] `DIP-006 [DIPLOMA]` Обновить docs: architecture overview, runbook, troubleshooting, known limitations.
- [ ] `DIP-007 [DIPLOMA]` Подготовить short demo script для защиты: команды запуска, ожидаемые events, artifacts, logs.

## Уровень 6. Hardening без ухода в enterprise

- [ ] `HARD-001 [HARDENING]` Secret resolver adapter: env/file/Kubernetes secret/Vault stub; без реальных production secrets.
- [ ] `HARD-002 [HARDENING]` Dependency vulnerability scan в CI.
- [ ] `HARD-003 [HARDENING]` SBOM generation для Docker images.
- [ ] `HARD-004 [HARDENING]` Basic load/stress test для log flooding и больших artifacts.
- [ ] `HARD-005 [HARDENING]` Resource limit tests для process runner/container runner.
- [ ] `HARD-006 [HARDENING]` Error catalog на русском: коды ошибок, причины, рекомендации.

## Опциональные production-задачи, избыточные для магистерской работы

Эти задачи не нужны для защиты диплома, если нет отдельного требования кафедры или большого запаса времени.

- [ ] `PROD-001 [OPTIONAL/PROD]` Полноценный master-service scheduler, DAG engine, approvals и SSE.
- [ ] `PROD-002 [OPTIONAL/PROD]` Полноценный React UI.
- [ ] `PROD-003 [OPTIONAL/PROD]` Enterprise RBAC/ABAC, multi-tenancy, audit search UI.
- [ ] `PROD-004 [OPTIONAL/PROD]` High availability Kafka/OpenSearch/PostgreSQL production installation.
- [ ] `PROD-005 [OPTIONAL/PROD]` Horizontal autoscaling по custom metrics и queue lag.
- [ ] `PROD-006 [OPTIONAL/PROD]` GitOps deployment operator для самой платформы.
- [ ] `PROD-007 [OPTIONAL/PROD]` Policy-as-code engine для sandbox/secret/deploy approvals.
- [ ] `PROD-008 [OPTIONAL/PROD]` Marketplace шаблонов job и plugin SDK.
- [ ] `PROD-009 [OPTIONAL/PROD]` Распределенный fuzzing cluster с corpus synchronization.
- [ ] `PROD-010 [OPTIONAL/PROD]` Автоматическая генерация harness/prompt по исходному коду через LLM.
- [ ] `PROD-011 [OPTIONAL/PROD]` SLSA provenance verification gate для всех build artifacts.
- [ ] `PROD-012 [OPTIONAL/PROD]` Полноценный Vault production rollout с dynamic credentials, audit и rotation.

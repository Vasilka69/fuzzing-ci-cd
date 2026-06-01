# Prompts для поэтапной генерации master-service

## 1. Bootstrap backend

```text
Сгенерируй Spring Boot master-service на Java 21 по документации docs/00..10. Используй package ru.diplom.cicd.master. На первом шаге создай структуру проекта, application.yml, pom.xml, Flyway migrations V0001/V0002, health endpoint. Не реализуй executor-ы. Master владеет БД.
```

## 2. Domain model

```text
Реализуй JPA entities и repositories для всех таблиц из docs/03_DATABASE_MODEL_AND_MIGRATIONS.md. Используй UUID, OffsetDateTime, JSONB через JsonNode/Map, строковые enum values. Добавь repository smoke tests на PostgreSQL Testcontainers.
```

## 3. Contracts

```text
Реализуй DTO JobMessage, ExecutorEventMessage, CancelCommand, ArtifactDescriptor, ExecutorError согласно docs/04_EXECUTOR_CONTRACTS.md и schemas/*.json. Внешняя сериализация camelCase, schemaVersion integer = 1. Добавь JSON serialization/validation tests.
```

## 4. Orchestration

```text
Реализуй ExecutionGraphBuilder, ReadyJobResolver, JobStateMachine, PipelineStateReducer и PipelineOrchestrator согласно docs/05_ORCHESTRATION_STATE_MACHINE.md. Покрой unit tests: sequential/parallel stages, explicit dependencies, continue_on_error, retry, skipped, approval.
```

## 5. Outbox/Kafka

```text
Реализуй transactional OutboxService и OutboxPublisher. JobMessage создается в одной транзакции с job_execution. Kafka topic выбирается по jobType, Kafka key = jobExecutionId. Добавь integration test с embedded/testcontainers Kafka или mock producer.
```

## 6. OpenSearch logs and SSE

```text
Реализуй OpenSearchHistoryLogService и OpenSearchExecutorEventPoller. Logs читать только из JOB_LOG documents по jobExecutionId. Служебные события без логов применять через ExecutorEventService. Добавь SSE endpoint /api/v1/logs/stream.
```

## 7. React UI

```text
Сгенерируй React + TypeScript + Vite frontend на Ant Design по docs/07_REACT_ANTD_FRONTEND_SPEC.md. Реализуй AppLayout, PipelineListPage, PipelineDesignerPage, PipelineRunPage, LogViewer с SSE, ArtifactTable, ApprovalPanel. API только через master-service /api/v1.
```

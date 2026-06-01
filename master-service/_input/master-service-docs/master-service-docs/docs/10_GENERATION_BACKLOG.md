# 10. Backlog для генерации master-service и React UI

## Этап 0. Bootstrap

- [ ] Создать Maven parent/root или отдельный backend project.
- [ ] Добавить `master-service` Spring Boot module.
- [ ] Подключить Flyway и перенести миграции как `V0001__schema.sql`, `V0002__data.sql`.
- [ ] Создать `frontend` Vite React TypeScript project.
- [ ] Настроить Docker Compose: PostgreSQL, Kafka, OpenSearch, master, frontend.
- [ ] Добавить `.env.example`.

## Этап 1. Domain + DB

- [ ] Создать JPA entities для всех таблиц миграций.
- [ ] Создать repositories.
- [ ] Создать enum classes и converters/string mapping.
- [ ] Добавить integration test Flyway + repository smoke.

## Этап 2. REST CRUD и RBAC

- [ ] Auth/me/login MVP.
- [ ] Folder/Pipeline/Stage/Job CRUD.
- [ ] JobTemplate API + params validation.
- [ ] PermissionService и enforcement в controllers/services.
- [ ] AuditService.

## Этап 3. Contracts + outbox

- [ ] DTO `JobMessage`, `ExecutorEventMessage`, `CancelCommand`.
- [ ] JSON serialization tests camelCase.
- [ ] JobMessageFactory.
- [ ] KafkaTopicResolver.
- [ ] OutboxService транзакционно с job execution.
- [ ] OutboxPublisher с retry и status update.

## Этап 4. Orchestration

- [ ] ExecutionGraphBuilder.
- [ ] ExecutionGraphValidator.
- [ ] ReadyJobResolver.
- [ ] PipelineOrchestrator.
- [ ] JobStateMachine.
- [ ] PipelineStateReducer.
- [ ] RetryPolicyService.
- [ ] TimeoutWatchdog.

## Этап 5. Executor events

- [ ] Kafka `jobs.results` consumer.
- [ ] InboxDeduplicationService.
- [ ] ExecutorEventService: status update, artifact registration, SSE publish, scheduler trigger.
- [ ] DeadLetterService.
- [ ] Contract tests duplicate/late events.

## Этап 6. OpenSearch logs/events

- [ ] OpenSearch client config.
- [ ] OpenSearchHistoryLogService.
- [ ] `/job-executions/{id}/logs` endpoint.
- [ ] OpenSearchExecutorEventPoller with cursor.
- [ ] Tests for poller restart/search_after/JOB_LOG ignore.

## Этап 7. Cancel/Retry/Approval/Triggers

- [ ] Cancel endpoints + outbox `CancelCommand`.
- [ ] Retry endpoints with new execution.
- [ ] Protected deployment approval endpoints.
- [ ] Manual/API/VCS webhook trigger flow.
- [ ] Schedule trigger minimal scheduler.

## Этап 8. Frontend MVP

- [ ] AppLayout + routing.
- [ ] API client and typed DTOs.
- [ ] PipelineListPage.
- [ ] PipelineDesignerPage.
- [ ] JobTemplate dynamic form.
- [ ] PipelineRunPage with graph/status.
- [ ] LogViewer via REST + SSE.
- [ ] ArtifactTable.
- [ ] ApprovalPanel.
- [ ] ExecutorStatusPage.

## Этап 9. Hardening

- [ ] Unified error format.
- [ ] CorrelationId in logs/API errors.
- [ ] Secret redaction tests.
- [ ] API pagination/cursors.
- [ ] Metrics/Actuator.
- [ ] Security review.
- [ ] E2E demo pipeline.

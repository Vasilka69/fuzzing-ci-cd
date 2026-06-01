# Master-service CI/CD — пакет документации для генерации проекта

Этот пакет фиксирует целевую документацию для дальнейшей генерации/реализации `master-service` и `frontend` CI/CD-инструмента.

Главная граница: `master-service` является источником истины для PostgreSQL, pipeline orchestration, REST API, Kafka job dispatch, приема executor-событий, чтения логов из OpenSearch и публикации SSE в React UI. Executor-сервисы не пишут напрямую в БД master-service и общаются с ним только через Kafka/OpenSearch-контракты и artifact URI.

## Рекомендуемый порядок чтения

1. `docs/00_IMPLEMENTATION_FILE_LIST.md` — целевой список файлов backend/frontend/infra с назначением.
2. `docs/01_MASTER_SERVICE_PRD.md` — требования и scope master-service.
3. `docs/02_ARCHITECTURE_AND_BOUNDARIES.md` — границы ответственности, интеграции и основные потоки.
4. `docs/03_DATABASE_MODEL_AND_MIGRATIONS.md` — модель БД и соответствие миграциям.
5. `docs/04_EXECUTOR_CONTRACTS.md` — Kafka/OpenSearch/cancel/artifact контракты.
6. `docs/05_ORCHESTRATION_STATE_MACHINE.md` — запуск pipeline, DAG, retry, cancel, approval.
7. `docs/06_REST_API_SPEC.md` — REST API master-service.
8. `docs/07_REACT_ANTD_FRONTEND_SPEC.md` — React UI на Ant Design.
9. `docs/08_SECURITY_RBAC_SECRETS.md` — RBAC, секреты, аудит.
10. `docs/09_TESTING_ACCEPTANCE.md` — тестирование и критерии приемки.
11. `docs/10_GENERATION_BACKLOG.md` — удобный backlog для поэтапного vibe-coding.

## Вспомогательные файлы

- `schemas/job-message.schema.json` — JSON Schema задания executor-у.
- `schemas/executor-event-message.schema.json` — JSON Schema события executor-а.
- `schemas/cancel-command.schema.json` — JSON Schema команды отмены.
- `openapi/master-api.openapi.yaml` — черновая OpenAPI-спецификация ключевых endpoints.
- `prompts/MASTER_SERVICE_GENERATION_PROMPTS.md` — готовые prompts для генерации проекта по частям.

## Принятые решения

- Внешний JSON-контракт Kafka/OpenSearch использует `camelCase`.
- Внутренние SQL-поля и JPA-entity используют `snake_case` на уровне таблиц, но Java DTO могут использовать camelCase.
- `jobExecutionId` — единый идентификатор попытки выполнения job; он создается master-service до публикации задания.
- Kafka key для job message и result event равен `jobExecutionId`.
- Текстовые логи не хранятся в PostgreSQL и не передаются крупным payload в служебных event. Они читаются master-service из OpenSearch.
- Frontend не обращается напрямую к PostgreSQL, Kafka, OpenSearch или storage backend.

# AGENTS

Правила реализации:

- Не ломать публичные контракты сообщений executor (`JobMessage`, `ExecutorEventMessage`, `CancelCommand`).
- Не хранить секреты в открытом виде в PostgreSQL, Kafka payload, OpenSearch логах и audit payload.
- `master-service` владеет транзакционным состоянием в БД (runs, executions, artifacts metadata, outbox/inbox).
- Executor-сервисы интегрируются через Kafka/OpenSearch и artifact URI, без прямого доступа к БД master-service.

# ADR-0002 — Единые контракты executor job/events/logs

Дата: 2026-05-29
Статус: принято.

## Контекст

Master-service и executor-ы должны обмениваться заданиями и результатами через Kafka/OpenSearch. Нельзя допустить, чтобы каждый сервис придумал свой формат событий, статусов, ошибок и логов.

## Решение

Создать общий модуль `common/cicd-contracts` с:

- `JobMessage`;
- `ExecutorEventMessage`;
- `ExecutorEventDocument`;
- `ExecutorError`;
- `ArtifactDescriptor`;
- enums для job types, event types, statuses, error types;
- JSON schema/serialization tests.

Внешний JSON использует `camelCase`. Версия контракта — integer `schemaVersion=1`.

## Инварианты

- `jobExecutionId` обязателен.
- `JOB_LOG` содержит `logs`; служебные события не содержат больших logs.
- `additionalData` не содержит секреты.
- `error.type` берется из единого словаря.
- Kafka key равен `jobExecutionId`.

## Последствия

- Любое изменение контракта требует тестов и ADR.
- Executor-ы получают меньше свободы, но master/UI проще интегрировать.

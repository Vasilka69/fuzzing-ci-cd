# ADR-0005 — Jackson для сериализации OpenSearch log documents

Дата: 2026-05-30
Статус: принято.

## Контекст

`CORE-005` добавляет `ExecutorLogPublisher` и OpenSearch implementation в `common/cicd-executor-core`.
Publisher должен формировать JSON-документы `JOB_LOG` с корректным escaping, `camelCase` полями и enum wire values.

Проект уже использует Jackson для executor contracts, а версии Jackson управляются Spring Boot BOM.

## Решение

Подключить `com.fasterxml.jackson.core:jackson-databind` как production dependency модуля
`common/cicd-executor-core` и сериализовать OpenSearch log document через `ObjectMapper`.

Документ представлен внутренним типизированным record `OpenSearchLogDocument`.
`ObjectNode` не используется, потому что состав `JOB_LOG` документа фиксирован и лучше проверяется компилятором.

## Альтернативы

Ручная сборка JSON без зависимости уменьшает dependency surface, но увеличивает риск ошибок escaping,
расхождения enum wire values и будущих несовместимостей при расширении log document.

## Последствия

- Общий core-модуль получает production dependency на Jackson Databind.
- Сериализация лог-документов использует тот же JSON stack, что и contract tests проекта.
- Изменение не меняет внешний contract `ExecutorEventMessage`; OpenSearch schema tests остаются отдельной задачей `CORE-013`.

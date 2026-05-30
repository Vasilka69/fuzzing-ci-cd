# ADR-0004 — Spring Kafka для публикации executor events

Дата: 2026-05-30
Статус: принято.

## Контекст

`CORE-004` требует общий `ExecutorEventPublisher` и Kafka implementation в `common/cicd-executor-core`.
Проект уже выбирает Spring Kafka / Apache Kafka как messaging stack, а executor-сервисы будут Spring Boot приложениями.

## Решение

Подключить `org.springframework.kafka:spring-kafka` в `common/cicd-executor-core` и реализовать Kafka publisher
через `KafkaTemplate<String, ExecutorEventMessage>`.

Kafka key для события равен `jobExecutionId`, topic по умолчанию — `jobs.results`.
Publisher не меняет JSON contract `ExecutorEventMessage` и не публикует большие текстовые логи в служебных событиях.

## Альтернативы

Нативный Apache Kafka producer без Spring Kafka дал бы меньше зависимости от Spring runtime, но потребовал бы ручной
конфигурации producer-а в каждом executor-е и хуже совпал бы с выбранным стеком проекта.

## Последствия

- Общий core-модуль получает production dependency на Spring Kafka.
- Сервисный wiring сможет переиспользовать стандартный `KafkaTemplate`.
- Интеграционные тесты с реальным Kafka broker остаются отдельной задачей для сервисного уровня или demo pipeline.

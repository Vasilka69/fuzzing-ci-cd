# ADR-0007 — отдельный Maven-модуль для demo mock master publisher

Дата: 2026-06-01
Статус: принято.

## Контекст

`DEMO-001` требует mock master publisher, который публикует `JobMessage` в Kafka topics executor-ов по
заранее заданному demo pipeline. В репозитории нет master-service, а executor-ы не должны получать зависимость
на master/ui или содержать демонстрационную orchestration-логику.

## Решение

Добавить отдельный Maven-модуль `demo/mock-master-publisher`.

Модуль реализован как Spring Boot CLI-приложение без HTTP API. Он формирует воспроизводимый набор сообщений
для цепочки VCS -> Build -> Fuzzing -> Deploy/Script и публикует их через `KafkaTemplate` в topics:
`jobs.vcs`, `jobs.build`, `jobs.fuzzing`, `jobs.deploy`, `jobs.script`.

Kafka key каждого сообщения равен `jobExecutionId`. UUID сообщений и job execution строятся от настраиваемого
`run-id`, чтобы повторная публикация одного demo была совместима с idempotency executor-ов, а новый прогон можно
было получить через другой `run-id`.

## Альтернативы

Разместить publisher в `common/cicd-test-support` проще по числу модулей, но этот модуль является тестовой
библиотекой, а не запускаемым demo-инструментом. Такой вариант смешивает test support и runtime CLI.

Публиковать JSON через shell-скрипт и `kafka-console-producer` уменьшает Java-код, но хуже проверяется тестами,
дублирует контракт вручную и усложняет безопасное сопровождение `JobMessage`.

## Последствия

- Maven layout расширен новым разделом `demo/`.
- Kafka/OpenSearch JSON contracts не меняются.
- Новых production dependency versions не добавлено: Spring Boot, Spring Kafka и Apache Commons Lang управляются
  existing BOM/dependency management.
- `DEMO-002` остается отдельной задачей: реальные sample repositories и artifacts еще не добавлены.

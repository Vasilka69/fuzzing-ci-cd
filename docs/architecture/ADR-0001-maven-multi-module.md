# ADR-0001 — Многомодульный Maven repository

Дата: 2026-05-29
Статус: принято.

## Контекст

Нужно реализовать несколько исполняющих микросервисов с общими контрактами, логикой executor runtime, тестовой инфраструктурой, Dockerfile и Kubernetes manifests. Каждый микросервис должен быть отдельным модулем, но сборка и версии зависимостей должны управляться централизованно.

## Решение

Использовать Maven multi-module project:

- parent POM в корне;
- `common/cicd-contracts` для DTO/enums/schema;
- `common/cicd-executor-core` для общих publisher/runner/workspace/sandbox components;
- `common/cicd-test-support` для Testcontainers fixtures, sample job messages, assertions;
- отдельный Spring Boot module на каждый executor service.

## Почему так

- Единая сборка и dependency management.
- Повторное использование контрактов без копирования DTO.
- Удобная сборка отдельного сервиса через `-pl <module> -am`.
- Возможность service-scoped `AGENTS.md`, PRD и TASKS.

## Альтернативы

1. Polyrepo: лучше изоляция, но сложнее синхронизация контрактов для дипломного проекта.
2. Монолитный Spring Boot проект: быстрее старт, но хуже демонстрирует микросервисную архитектуру.
3. Gradle multi-project: подходит, но пользователь явно планирует Maven.

## Последствия

- Требуется дисциплина зависимостей между модулями.
- Общие изменения контрактов затрагивают несколько сервисов, поэтому нужны contract tests.

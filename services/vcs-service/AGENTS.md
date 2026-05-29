# AGENTS.md — VCS-сервис (vcs-service)

Этот файл сужает контекст для работы в `services/vcs-service`. Сначала всегда прочитай корневой `/AGENTS.md`.

## Релевантные документы

- `/docs/prd/PRD-vcs-service.md`
- `/docs/tasks/TASKS-vcs-service.md`
- `/docs/context/PROJECT_CONTEXT.md` — только при необходимости общего потока pipeline
- `/docs/context/DB_CONTEXT.md` — только если нужны enum/status/template значения

## Scope сервиса

- Kafka topic: `jobs.vcs`
- Job templates: `vcs/git`, `vcs/mercurial`
- Назначение: получает исходный код из VCS, фиксирует commit/revision и готовит воспроизводимый source snapshot для следующих этапов pipeline.

## Что нельзя делать из этого модуля

- Не реализовывать UI или master-service.
- Не писать напрямую в таблицы master-service.
- Не менять общие контракты без ADR и contract tests.
- Не добавлять новые внешние зависимости без объяснения trade-offs.
- Не ослаблять sandbox/security policy.

## Перед кодом

Сначала объясни пользователю:

1. Как понял задачу именно для `vcs-service`.
2. Какие контракты затрагиваются.
3. Основной подход и альтернативы.
4. Риски.
5. План проверки.

Код писать только после подтверждения подхода.

## Минимальная проверка

```bash
./mvnw -pl services/vcs-service -am test
./mvnw -pl services/vcs-service -am verify
```

## Особое внимание

Риски сервиса:
- утечка токена в URL/логах
- очень большой repository
- невоспроизводимый checkout по branch без commit hash
- повторная доставка Kafka-сообщения

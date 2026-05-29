# AGENTS.md — Deploy-сервис (deploy-service)

Этот файл сужает контекст для работы в `services/deploy-service`. Сначала всегда прочитай корневой `/AGENTS.md`.

## Релевантные документы

- `/docs/prd/PRD-deploy-service.md`
- `/docs/tasks/TASKS-deploy-service.md`
- `/docs/context/PROJECT_CONTEXT.md` — только при необходимости общего потока pipeline
- `/docs/context/DB_CONTEXT.md` — только если нужны enum/status/template значения

## Scope сервиса

- Kafka topic: `jobs.deploy`
- Job templates: `deploy/ssh-bash`, `deploy/windows-cmd`, `deploy/file-copy`, `deploy/docker`, `deploy/docker-compose`, `deploy/systemd`
- Назначение: доставляет release artifact в целевую среду, фиксирует release_id, deployment manifest, healthcheck и rollback metadata.

## Что нельзя делать из этого модуля

- Не реализовывать UI или master-service.
- Не писать напрямую в таблицы master-service.
- Не менять общие контракты без ADR и contract tests.
- Не добавлять новые внешние зависимости без объяснения trade-offs.
- Не ослаблять sandbox/security policy.

## Перед кодом

Сначала объясни пользователю:

1. Как понял задачу именно для `deploy-service`.
2. Какие контракты затрагиваются.
3. Основной подход и альтернативы.
4. Риски.
5. План проверки.

Код писать только после подтверждения подхода.

## Минимальная проверка

```bash
./mvnw -pl services/deploy-service -am test
./mvnw -pl services/deploy-service -am verify
```

## Особое внимание

Риски сервиса:
- неидемпотентные внешние побочные эффекты
- rollback вместо cancel
- утечка SSH key
- команды deployment без audit trail
- повторная доставка job после успешного deploy

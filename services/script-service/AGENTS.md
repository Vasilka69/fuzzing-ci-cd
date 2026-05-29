# AGENTS.md — Script-сервис (script-service)

Этот файл сужает контекст для работы в `services/script-service`. Сначала всегда прочитай корневой `/AGENTS.md`.

## Релевантные документы

- `/docs/prd/PRD-script-service.md`
- `/docs/tasks/TASKS-script-service.md`
- `/docs/context/PROJECT_CONTEXT.md` — только при необходимости общего потока pipeline
- `/docs/context/DB_CONTEXT.md` — только если нужны enum/status/template значения

## Scope сервиса

- Kafka topic: `jobs.script`
- Job templates: `script/bash`, `script/cmd`
- Назначение: выполняет пользовательские Bash/cmd сценарии для нестандартных этапов pipeline в ограниченном sandbox-окружении.

## Что нельзя делать из этого модуля

- Не реализовывать UI или master-service.
- Не писать напрямую в таблицы master-service.
- Не менять общие контракты без ADR и contract tests.
- Не добавлять новые внешние зависимости без объяснения trade-offs.
- Не ослаблять sandbox/security policy.

## Перед кодом

Сначала объясни пользователю:

1. Как понял задачу именно для `script-service`.
2. Какие контракты затрагиваются.
3. Основной подход и альтернативы.
4. Риски.
5. План проверки.

Код писать только после подтверждения подхода.

## Минимальная проверка

```bash
./mvnw -pl services/script-service -am test
./mvnw -pl services/script-service -am verify
```

## Особое внимание

Риски сервиса:
- запуск непроверенного пользовательского кода
- privileged/host network/hostPath/Docker socket
- fork bomb/log flooding
- секреты в stdout/stderr

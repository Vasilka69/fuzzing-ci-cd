# AGENTS.md — Build-сервис (build-service)

Этот файл сужает контекст для работы в `services/build-service`. Сначала всегда прочитай корневой `/AGENTS.md`.

## Релевантные документы

- `/docs/prd/PRD-build-service.md`
- `/docs/tasks/TASKS-build-service.md`
- `/docs/context/PROJECT_CONTEXT.md` — только при необходимости общего потока pipeline
- `/docs/context/DB_CONTEXT.md` — только если нужны enum/status/template значения

## Scope сервиса

- Kafka topic: `jobs.build`
- Job templates: `build/maven`, `build/gradle`, `build/javac`, `build/gcc`
- Назначение: выполняет сборку проектов из source snapshot с контролируемым entrypoint инструмента и публикует build artifacts.

## Что нельзя делать из этого модуля

- Не реализовывать UI или master-service.
- Не писать напрямую в таблицы master-service.
- Не менять общие контракты без ADR и contract tests.
- Не добавлять новые внешние зависимости без объяснения trade-offs.
- Не ослаблять sandbox/security policy.

## Перед кодом

Сначала объясни пользователю:

1. Как понял задачу именно для `build-service`.
2. Какие контракты затрагиваются.
3. Основной подход и альтернативы.
4. Риски.
5. План проверки.

Код писать только после подтверждения подхода.

## Минимальная проверка

```bash
./mvnw -pl services/build-service -am test
./mvnw -pl services/build-service -am verify
```

## Особое внимание

Риски сервиса:
- запуск произвольной команды вместо whitelist entrypoint
- сетевой доступ вне allowlist
- переполнение stdout/stderr
- неочищенный workspace

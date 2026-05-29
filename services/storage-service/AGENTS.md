# AGENTS.md — Storage-сервис (storage-service)

Этот файл сужает контекст для работы в `services/storage-service`. Сначала всегда прочитай корневой `/AGENTS.md`.

## Релевантные документы

- `/docs/prd/PRD-storage-service.md`
- `/docs/tasks/TASKS-storage-service.md`
- `/docs/context/PROJECT_CONTEXT.md` — только при необходимости общего потока pipeline
- `/docs/context/DB_CONTEXT.md` — только если нужны enum/status/template значения

## Scope сервиса

- Kafka topic: `jobs.storage`
- Job templates: `storage/source-snapshot`, `storage/promote-artifact`, `storage/cleanup`
- Назначение: служит API-слоем над физическим хранилищем артефактов, source snapshot, отчетов, corpus/crash cases и release packages.

## Что нельзя делать из этого модуля

- Не реализовывать UI или master-service.
- Не писать напрямую в таблицы master-service.
- Не менять общие контракты без ADR и contract tests.
- Не добавлять новые внешние зависимости без объяснения trade-offs.
- Не ослаблять sandbox/security policy.

## Перед кодом

Сначала объясни пользователю:

1. Как понял задачу именно для `storage-service`.
2. Какие контракты затрагиваются.
3. Основной подход и альтернативы.
4. Риски.
5. План проверки.

Код писать только после подтверждения подхода.

## Минимальная проверка

```bash
./mvnw -pl services/storage-service -am test
./mvnw -pl services/storage-service -am verify
```

## Особое внимание

Риски сервиса:
- передача больших файлов через Kafka вместо URI
- несогласованный namespace URI
- перезапись чужих артефактов
- рост диска без retention

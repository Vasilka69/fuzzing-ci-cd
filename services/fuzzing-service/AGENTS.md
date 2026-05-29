# AGENTS.md — Fuzzing-сервис (fuzzing-service)

Этот файл сужает контекст для работы в `services/fuzzing-service`. Сначала всегда прочитай корневой `/AGENTS.md`.

## Релевантные документы

- `/docs/prd/PRD-fuzzing-service.md`
- `/docs/tasks/TASKS-fuzzing-service.md`
- `/docs/context/PROJECT_CONTEXT.md` — только при необходимости общего потока pipeline
- `/docs/context/DB_CONTEXT.md` — только если нужны enum/status/template значения

## Scope сервиса

- Kafka topic: `jobs.fuzzing`
- Job templates: `fuzzing/afl-llm`
- Назначение: запускает AFL++ fuzzing target-ов и интегрирует готовое fuzzing-ядро с LLM-assisted генерацией структурных входов через worker/custom mutator.

## Что нельзя делать из этого модуля

- Не реализовывать UI или master-service.
- Не писать напрямую в таблицы master-service.
- Не менять общие контракты без ADR и contract tests.
- Не добавлять новые внешние зависимости без объяснения trade-offs.
- Не ослаблять sandbox/security policy.

## Перед кодом

Сначала объясни пользователю:

1. Как понял задачу именно для `fuzzing-service`.
2. Какие контракты затрагиваются.
3. Основной подход и альтернативы.
4. Риски.
5. План проверки.

Код писать только после подтверждения подхода.

## Минимальная проверка

```bash
./mvnw -pl services/fuzzing-service -am test
./mvnw -pl services/fuzzing-service -am verify
```

## Особое внимание

Риски сервиса:
- LLM HTTP call in AFL hot path
- нераспознанный crash как infrastructure error
- неограниченный target process
- утечка prompt/secret в logs
- нестабильная воспроизводимость без artifact capture

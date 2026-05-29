# Библиотека промптов для разработки

## Планирование фичи

```text
Изучи AGENTS.md, docs/prd/PRD-<service>.md и docs/tasks/TASKS-<service>.md.
Нужно реализовать <задача>.
Сначала не меняй файлы. Объясни, как понял задачу, предложи основной подход и 1-2 альтернативы, укажи затрагиваемые файлы, риски, план тестирования и Definition of Done.
Код писать только после моего подтверждения.
```

## Debug root cause

```text
Вот команда, ошибка и ожидаемое поведение: ...
Сначала найди root cause. Не подавляй ошибку и не отключай тест. Предложи минимальный fix и тест, который докажет исправление. Код не меняй до подтверждения.
```

## Security review

```text
Проведи security review текущего diff. Особое внимание: secrets, sandbox, command injection, log injection, dependency risk, Kafka/OpenSearch payload leaks. Верни findings с severity, evidence, impact, recommended fix.
```

## Contract review

```text
Проверь, что изменения не ломают внешний contract executor-а: JobMessage, ExecutorEventMessage, OpenSearch JOB_LOG, status/error enums, jobExecutionId. Укажи несовместимости и предложи миграционный путь.
```

## Тесты

```text
Добавь тесты для happy path, invalid input, timeout/resource limit, idempotency повторной доставки и security negative case. Не мокай то, что важно проверить интеграционно.
```

## Fuzzing adapter

```text
Работай только с fuzzing-service. Готовое fuzzing-ядро не переписывай. Сначала предложи adapter boundary: config files, process/container запуск, result collector, mapping stats/errors/artifacts. Отдельно укажи, как исключить LLM HTTP calls из AFL hot path.
```

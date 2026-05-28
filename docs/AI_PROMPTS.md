# AI Prompt Library for this project

## 1. Планирование задачи

```text
Цель: <что нужно реализовать>.
Контекст: используй AGENTS.md, docs/PRD.md и docs/services/<service>/PRD.md. Не загружай нерелевантные service docs.
Ограничения: не трогай UI и master-service; не дублируй DTO; не логируй секреты; не добавляй зависимости без обоснования.
Готово, когда: есть код, тесты, Docker/K8s updates при необходимости, и проверки проходят.
Сначала изучи релевантные файлы и предложи план. Код пока не меняй.
```

## 2. Реализация vertical slice

```text
Реализуй минимальный vertical slice для <service>/<feature>:
1. contract/DTO или adapter;
2. service logic;
3. unit tests;
4. integration/contract test;
5. Docker/K8s config если изменились runtime требования.
Не расширяй scope. После изменений запусти релевантные Maven checks и кратко опиши риски.
```

## 3. Security review

```text
Проведи adversarial security review текущего diff.
Проверь: secrets, command injection, path traversal, unsafe deserialization, Kafka contract misuse, artifact leakage, privileged containers, missing resource limits, retry/idempotency bugs.
Не исправляй сразу. Сначала верни findings с severity, evidence, impact, recommended fix.
```

## 4. Contract review

```text
Проверь, что изменения не нарушают Kafka contract:
- job envelope fields;
- result event fields;
- event_type/status mapping;
- structured error model;
- artifact descriptors;
- idempotency by job_execution_id.
Укажи несовместимые изменения и предложи backward-compatible fix.
```

## 5. Debug root cause

```text
Вот команда и ошибка: <paste>.
Сначала объясни вероятную root cause и 2-3 гипотезы. Не отключай тесты и не подавляй exception. Затем предложи минимальный fix и проверку.
```

## 6. Update docs after implementation

```text
Обнови только релевантные документы: docs/services/<service>/PRD.md или TASKS.md, если изменился контракт, env var, artifact format, Docker/K8s behavior или acceptance criteria. Не переписывай весь документ.
```

# PRD — Fuzzing-сервис (Fuzzing service)

Дата: 2026-05-29
Модуль: `services/fuzzing-service`
Kafka topic: `jobs.fuzzing`
Job templates: `fuzzing/afl-llm`

## 1. Назначение

Fuzzing-сервис запускает AFL++ fuzzing target-ов и интегрирует готовое fuzzing-ядро с LLM-assisted генерацией структурных входов через worker/custom mutator.

## 2. Scope

### In scope для MVP

- адаптер к готовому fuzzing-ядру без переписывания core
- fake LLM worker/local grammar mode
- демонстрационный target DSL
- AFL++ run with budget
- crash report and artifacts

### Дипломно-достаточный scope

- real OpenAI-compatible LLM endpoint mode
- feedback loop: interesting inputs -> worker
- custom mutator queue metrics hit/miss/fallback
- policy fail_on_crash/fail_on_hang
- baseline AFL++ comparison metrics

### Опционально / production-level, не обязательно для магистерской работы

- multi-target fuzzing campaign orchestration
- coverage visualization UI
- automatic harness generation
- large-scale corpus minimization service
- clustered fuzzing across workers

## 3. Входные параметры job

- `target_artifact_uri или source_snapshot_uri`
- `target_command`
- `seed_corpus_uri`
- `dictionary_uri`
- `budget_seconds`
- `memory/time limits`
- `mode: fake/real`
- `prompt_uri или generation rules`
- `policy: fail_on_crash/fail_on_hang`

## 4. Результат job

- `fuzzing_report`
- `crash artifacts`
- `hang artifacts`
- `corpus artifacts`
- `AFL++ stats`
- `LLM worker stats`
- `summary`
- `error_type=fuzzing_crash_found при fail_on_crash`

Для MVP crash/hang/corpus и `fuzzer_stats` публикуются одним storage artifact
`storage://fuzzing-reports/<jobExecutionId>/fuzzing-report.tar.gz`; внутри архива находится
`fuzzing-report.json`. Kafka events содержат только artifact descriptor и компактную metadata.

## 5. Общие требования executor-а

- Получает сообщения только из `jobs.fuzzing`.
- Проверяет `jobType`, `templatePath`, `schemaVersion`, `jobExecutionId`.
- Публикует `JOB_RUNNING`, `JOB_LOG`, итоговый `JOB_FINISHED`.
- Все события и логи содержат `jobExecutionId`.
- Большие payload не кладутся в Kafka.
- Секреты маскируются до записи в stdout/stderr/OpenSearch.
- Повторная доставка по тому же `jobExecutionId` идемпотентна.
- Workspace создается отдельно для каждой попытки и очищается согласно `workspacePolicy`.
- Ошибки мапятся в единый словарь `error.type`.

## 6. Архитектура модуля

Рекомендуемая внутренняя структура:

```text
services/fuzzing-service/
├── AGENTS.md
├── Dockerfile
├── pom.xml
└── src/
    ├── main/java/.../
    │   ├── fuzzingservice/
    │   │   ├── config/
    │   │   ├── consumer/
    │   │   ├── handler/
    │   │   ├── validation/
    │   │   ├── runner/
    │   │   └── result/
    │   └── Application.java
    └── test/java/...
```

## 7. Риски

- LLM HTTP call in AFL hot path
- нераспознанный crash как infrastructure error
- неограниченный target process
- утечка prompt/secret в logs
- нестабильная воспроизводимость без artifact capture

## 8. Тестирование

- unit tests policy interpretation
- adapter tests against fake kernel process
- integration test demo target reaches hidden crash
- metrics parser tests
- timeout/cancel tests
- no-network mode test for fake worker

## 9. Acceptance criteria

- [ ] Сервис стартует локально в test/local profile.
- [ ] Сервис обрабатывает валидное job message своего типа.
- [ ] Невалидные параметры дают `validation_error`, а не crash сервиса.
- [ ] При успешной обработке есть `JOB_RUNNING`, `JOB_LOG`, `JOB_FINISHED/SUCCESS`.
- [ ] При ошибке есть `JOB_FINISHED/FAILED` с корректным `error.type`.
- [ ] Повторная доставка того же `jobExecutionId` не создает конфликтующие артефакты или побочные эффекты.
- [ ] Dockerfile собирает image.
- [ ] Kubernetes manifests содержат securityContext/resources/probes.
- [ ] Документация и Javadoc обновлены для сложной логики.

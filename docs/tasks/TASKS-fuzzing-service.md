# TASKS — Fuzzing-сервис (fuzzing-service)

Дата: 2026-05-29
Модуль: `services/fuzzing-service`
Topic: `jobs.fuzzing`

## Перед началом

Агент должен прочитать:

1. `/AGENTS.md`
2. `/services/fuzzing-service/AGENTS.md`
3. `/docs/prd/PRD-fuzzing-service.md`
4. этот файл
5. `/docs/context/PROJECT_CONTEXT.md` только если требуется общий поток pipeline

## Уровень 1. MVP

- [x] `FUZZING-001 [MVP]` адаптер к готовому fuzzing-ядру без переписывания core.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.
  - Готово: добавлен process adapter boundary к `fuzzing-engine/afl-llm-engine`, запуск через общий `ProcessRunner`, unit tests и JSON contract assertion итогового `JOB_FINISHED`.
- [x] `FUZZING-002 [MVP]` fake LLM worker/local grammar mode.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.
  - Готово: `mode=fake` запускает готовое ядро в local grammar mode без внешнего LLM API; executor задает workspace-local IPC socket, DSL prompt/seeds и параметры fake worker через environment, результат покрыт unit tests и JSON assertion итогового `JOB_FINISHED`.
- [ ] `FUZZING-003 [MVP]` демонстрационный target DSL.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.
- [ ] `FUZZING-004 [MVP]` AFL++ run with budget.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.
- [ ] `FUZZING-005 [MVP]` crash report and artifacts.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.

## Уровень 2. Дипломно-достаточная полнота

- [ ] `FUZZING-101 [DIPLOMA]` real OpenAI-compatible LLM endpoint mode.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.
- [ ] `FUZZING-102 [DIPLOMA]` feedback loop: interesting inputs -> worker.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.
- [ ] `FUZZING-103 [DIPLOMA]` custom mutator queue metrics hit/miss/fallback.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.
- [ ] `FUZZING-104 [DIPLOMA]` policy fail_on_crash/fail_on_hang.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.
- [ ] `FUZZING-105 [DIPLOMA]` baseline AFL++ comparison metrics.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.

## Уровень 3. Общие hardening-задачи сервиса

- [x] `FUZZING-701 [HARDENING]` Добавить structured metrics: active jobs, duration, success/failure count by errorType.
  - Готово централизованно через `cicd-executor-core`; `/actuator/metrics` открыт в сервисе.
- [ ] `FUZZING-702 [HARDENING]` Добавить graceful shutdown: consumer перестает брать новые сообщения, активная job корректно завершается/отменяется.
- [ ] `FUZZING-703 [HARDENING]` Добавить negative tests на отсутствие секретов в logs/events/artifacts.
- [ ] `FUZZING-704 [HARDENING]` Добавить Kubernetes resource tuning и documented defaults.
- [ ] `FUZZING-705 [HARDENING]` Добавить troubleshooting раздел: типовые ошибки и действия оператора.

## Уровень 4. Опционально / production-level

- [ ] `FUZZING-901 [OPTIONAL/PROD]` multi-target fuzzing campaign orchestration.
  - Делать только после завершения MVP и дипломно-достаточного уровня.
- [ ] `FUZZING-902 [OPTIONAL/PROD]` coverage visualization UI.
  - Делать только после завершения MVP и дипломно-достаточного уровня.
- [ ] `FUZZING-903 [OPTIONAL/PROD]` automatic harness generation.
  - Делать только после завершения MVP и дипломно-достаточного уровня.
- [ ] `FUZZING-904 [OPTIONAL/PROD]` large-scale corpus minimization service.
  - Делать только после завершения MVP и дипломно-достаточного уровня.
- [ ] `FUZZING-905 [OPTIONAL/PROD]` clustered fuzzing across workers.
  - Делать только после завершения MVP и дипломно-достаточного уровня.

## Definition of Done для сервиса

- [ ] Все MVP задачи закрыты.
- [ ] Сервис покрыт unit и integration/contract tests.
- [ ] Dockerfile и Kubernetes manifests есть.
- [ ] Сервис не зависит от master-service/ui.
- [ ] `jobExecutionId` используется во всех logs/events/artifacts.
- [ ] Секреты не попадают в логи.
- [ ] README сервиса объясняет local run и ограничения.

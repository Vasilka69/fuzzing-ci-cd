# TASKS — Build-сервис (build-service)

Дата: 2026-05-29
Модуль: `services/build-service`
Topic: `jobs.build`

## Перед началом

Агент должен прочитать:

1. `/AGENTS.md`
2. `/services/build-service/AGENTS.md`
3. `/docs/prd/PRD-build-service.md`
4. этот файл
5. `/docs/context/PROJECT_CONTEXT.md` только если требуется общий поток pipeline

## Уровень 1. MVP

- [ ] `BUILD-001 [MVP]` Maven и Gradle сборка в изолированном workspace.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.
- [ ] `BUILD-002 [MVP]` скачивание source snapshot из storage.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.
- [ ] `BUILD-003 [MVP]` expected_artifacts glob resolver.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.
- [ ] `BUILD-004 [MVP]` сбор stdout/stderr с ограничением размера.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.
- [ ] `BUILD-005 [MVP]` публикация artifacts.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.

## Уровень 2. Дипломно-достаточная полнота

- [ ] `BUILD-101 [DIPLOMA]` Javac и GCC.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.
- [ ] `BUILD-102 [DIPLOMA]` dependency cache с безопасным namespace.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.
- [ ] `BUILD-103 [DIPLOMA]` resource limits и timeout.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.
- [ ] `BUILD-104 [DIPLOMA]` mapping error types: user_code_error/infrastructure_error/timeout.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.
- [ ] `BUILD-105 [DIPLOMA]` контрактные тесты событий.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.

## Уровень 3. Общие hardening-задачи сервиса

- [x] `BUILD-701 [HARDENING]` Добавить structured metrics: active jobs, duration, success/failure count by errorType.
  - Готово централизованно через `cicd-executor-core`; `/actuator/metrics` открыт в сервисе.
- [ ] `BUILD-702 [HARDENING]` Добавить graceful shutdown: consumer перестает брать новые сообщения, активная job корректно завершается/отменяется.
- [ ] `BUILD-703 [HARDENING]` Добавить negative tests на отсутствие секретов в logs/events/artifacts.
- [ ] `BUILD-704 [HARDENING]` Добавить Kubernetes resource tuning и documented defaults.
- [ ] `BUILD-705 [HARDENING]` Добавить troubleshooting раздел: типовые ошибки и действия оператора.

## Уровень 4. Опционально / production-level

- [ ] `BUILD-901 [OPTIONAL/PROD]` Bazel/CMake/NPM build adapters.
  - Делать только после завершения MVP и дипломно-достаточного уровня.
- [ ] `BUILD-902 [OPTIONAL/PROD]` distributed build cache.
  - Делать только после завершения MVP и дипломно-достаточного уровня.
- [ ] `BUILD-903 [OPTIONAL/PROD]` parallel build farm scheduling.
  - Делать только после завершения MVP и дипломно-достаточного уровня.
- [ ] `BUILD-904 [OPTIONAL/PROD]` SLSA provenance attestation.
  - Делать только после завершения MVP и дипломно-достаточного уровня.

## Definition of Done для сервиса

- [ ] Все MVP задачи закрыты.
- [ ] Сервис покрыт unit и integration/contract tests.
- [ ] Dockerfile и Kubernetes manifests есть.
- [ ] Сервис не зависит от master-service/ui.
- [ ] `jobExecutionId` используется во всех logs/events/artifacts.
- [ ] Секреты не попадают в логи.
- [ ] README сервиса объясняет local run и ограничения.

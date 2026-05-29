# TASKS — Script-сервис (script-service)

Дата: 2026-05-29
Модуль: `services/script-service`
Topic: `jobs.script`

## Перед началом

Агент должен прочитать:

1. `/AGENTS.md`
2. `/services/script-service/AGENTS.md`
3. `/docs/prd/PRD-script-service.md`
4. этот файл
5. `/docs/context/PROJECT_CONTEXT.md` только если требуется общий поток pipeline

## Уровень 1. MVP

- [ ] `SCRIPT-001 [MVP]` Bash script в контейнерном sandbox.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.
- [ ] `SCRIPT-002 [MVP]` input artifacts download.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.
- [ ] `SCRIPT-003 [MVP]` expected outputs upload.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.
- [ ] `SCRIPT-004 [MVP]` stdout/stderr chunking.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.
- [ ] `SCRIPT-005 [MVP]` network none by default.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.

## Уровень 2. Дипломно-достаточная полнота

- [ ] `SCRIPT-101 [DIPLOMA]` cmd mode через Windows-compatible runner or documented limitation.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.
- [ ] `SCRIPT-102 [DIPLOMA]` image whitelist.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.
- [ ] `SCRIPT-103 [DIPLOMA]` secret redaction.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.
- [ ] `SCRIPT-104 [DIPLOMA]` resource limits pids/stdout/disk.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.
- [ ] `SCRIPT-105 [DIPLOMA]` cancel handling.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.

## Уровень 3. Общие hardening-задачи сервиса

- [ ] `SCRIPT-701 [HARDENING]` Добавить structured metrics: active jobs, duration, success/failure count by errorType.
- [ ] `SCRIPT-702 [HARDENING]` Добавить graceful shutdown: consumer перестает брать новые сообщения, активная job корректно завершается/отменяется.
- [ ] `SCRIPT-703 [HARDENING]` Добавить negative tests на отсутствие секретов в logs/events/artifacts.
- [ ] `SCRIPT-704 [HARDENING]` Добавить Kubernetes resource tuning и documented defaults.
- [ ] `SCRIPT-705 [HARDENING]` Добавить troubleshooting раздел: типовые ошибки и действия оператора.

## Уровень 4. Опционально / production-level

- [ ] `SCRIPT-901 [OPTIONAL/PROD]` PowerShell, Python, Node runners.
  - Делать только после завершения MVP и дипломно-достаточного уровня.
- [ ] `SCRIPT-902 [OPTIONAL/PROD]` interactive debugging workspace.
  - Делать только после завершения MVP и дипломно-достаточного уровня.
- [ ] `SCRIPT-903 [OPTIONAL/PROD]` marketplace of script templates.
  - Делать только после завершения MVP и дипломно-достаточного уровня.
- [ ] `SCRIPT-904 [OPTIONAL/PROD]` remote cache for dependencies.
  - Делать только после завершения MVP и дипломно-достаточного уровня.

## Definition of Done для сервиса

- [ ] Все MVP задачи закрыты.
- [ ] Сервис покрыт unit и integration/contract tests.
- [ ] Dockerfile и Kubernetes manifests есть.
- [ ] Сервис не зависит от master-service/ui.
- [ ] `jobExecutionId` используется во всех logs/events/artifacts.
- [ ] Секреты не попадают в логи.
- [ ] README сервиса объясняет local run и ограничения.

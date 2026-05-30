# TASKS — Storage-сервис (storage-service)

Дата: 2026-05-29
Модуль: `services/storage-service`
Topic: `jobs.storage`

## Перед началом

Агент должен прочитать:

1. `/AGENTS.md`
2. `/services/storage-service/AGENTS.md`
3. `/docs/prd/PRD-storage-service.md`
4. этот файл
5. `/docs/context/PROJECT_CONTEXT.md` только если требуется общий поток pipeline

## Уровень 1. MVP

- [ ] `STORAGE-001 [MVP]` локальный filesystem backend.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.
- [ ] `STORAGE-002 [MVP]` storage:// URI namespace.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.
- [ ] `STORAGE-003 [MVP]` upload/download REST API для executor-ов.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.
- [ ] `STORAGE-004 [MVP]` sha256 verification.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.
- [ ] `STORAGE-005 [MVP]` cleanup temporary artifacts.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.

## Уровень 2. Дипломно-достаточная полнота

- [ ] `STORAGE-101 [DIPLOMA]` S3-compatible/MinIO adapter.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.
- [ ] `STORAGE-102 [DIPLOMA]` promote release artifact.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.
- [ ] `STORAGE-103 [DIPLOMA]` retention policy.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.
- [ ] `STORAGE-104 [DIPLOMA]` artifact manifest generation.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.
- [ ] `STORAGE-105 [DIPLOMA]` streaming upload/download without loading full file into memory.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.

## Уровень 3. Общие hardening-задачи сервиса

- [x] `STORAGE-701 [HARDENING]` Добавить structured metrics: active jobs, duration, success/failure count by errorType.
  - Готово централизованно через `cicd-executor-core`; `/actuator/metrics` открыт в сервисе.
- [ ] `STORAGE-702 [HARDENING]` Добавить graceful shutdown: consumer перестает брать новые сообщения, активная job корректно завершается/отменяется.
- [ ] `STORAGE-703 [HARDENING]` Добавить negative tests на отсутствие секретов в logs/events/artifacts.
- [ ] `STORAGE-704 [HARDENING]` Добавить Kubernetes resource tuning и documented defaults.
- [ ] `STORAGE-705 [HARDENING]` Добавить troubleshooting раздел: типовые ошибки и действия оператора.

## Уровень 4. Опционально / production-level

- [ ] `STORAGE-901 [OPTIONAL/PROD]` репликация между backend-ами.
  - Делать только после завершения MVP и дипломно-достаточного уровня.
- [ ] `STORAGE-902 [OPTIONAL/PROD]` content-addressable storage.
  - Делать только после завершения MVP и дипломно-достаточного уровня.
- [ ] `STORAGE-903 [OPTIONAL/PROD]` lifecycle policies через объектное хранилище.
  - Делать только после завершения MVP и дипломно-достаточного уровня.
- [ ] `STORAGE-904 [OPTIONAL/PROD]` deduplication по hash.
  - Делать только после завершения MVP и дипломно-достаточного уровня.

## Definition of Done для сервиса

- [ ] Все MVP задачи закрыты.
- [ ] Сервис покрыт unit и integration/contract tests.
- [ ] Dockerfile и Kubernetes manifests есть.
- [ ] Сервис не зависит от master-service/ui.
- [ ] `jobExecutionId` используется во всех logs/events/artifacts.
- [ ] Секреты не попадают в логи.
- [ ] README сервиса объясняет local run и ограничения.

# TASKS — VCS-сервис (vcs-service)

Дата: 2026-05-29
Модуль: `services/vcs-service`
Topic: `jobs.vcs`

## Перед началом

Агент должен прочитать:

1. `/AGENTS.md`
2. `/services/vcs-service/AGENTS.md`
3. `/docs/prd/PRD-vcs-service.md`
4. этот файл
5. `/docs/context/PROJECT_CONTEXT.md` только если требуется общий поток pipeline

## Уровень 1. MVP

- [ ] `VCS-001 [MVP]` Git checkout shallow clone.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.
- [ ] `VCS-002 [MVP]` маскирование credentials в логах.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.
- [ ] `VCS-003 [MVP]` архивация snapshot в tar.gz.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.
- [ ] `VCS-004 [MVP]` upload snapshot через storage-client.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.
- [ ] `VCS-005 [MVP]` публикация JOB_RUNNING/JOB_FINISHED/JOB_LOG.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.

## Уровень 2. Дипломно-достаточная полнота

- [ ] `VCS-101 [DIPLOMA]` Mercurial checkout.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.
- [ ] `VCS-102 [DIPLOMA]` submodules policy.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.
- [ ] `VCS-103 [DIPLOMA]` лимиты размера snapshot.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.
- [ ] `VCS-104 [DIPLOMA]` retry для временной недоступности VCS.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.
- [ ] `VCS-105 [DIPLOMA]` идемпотентность по jobExecutionId.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.

## Уровень 3. Общие hardening-задачи сервиса

- [ ] `VCS-701 [HARDENING]` Добавить structured metrics: active jobs, duration, success/failure count by errorType.
- [ ] `VCS-702 [HARDENING]` Добавить graceful shutdown: consumer перестает брать новые сообщения, активная job корректно завершается/отменяется.
- [ ] `VCS-703 [HARDENING]` Добавить negative tests на отсутствие секретов в logs/events/artifacts.
- [ ] `VCS-704 [HARDENING]` Добавить Kubernetes resource tuning и documented defaults.
- [ ] `VCS-705 [HARDENING]` Добавить troubleshooting раздел: типовые ошибки и действия оператора.

## Уровень 4. Опционально / production-level

- [ ] `VCS-901 [OPTIONAL/PROD]` поддержка SVN.
  - Делать только после завершения MVP и дипломно-достаточного уровня.
- [ ] `VCS-902 [OPTIONAL/PROD]` зеркалирование репозиториев.
  - Делать только после завершения MVP и дипломно-достаточного уровня.
- [ ] `VCS-903 [OPTIONAL/PROD]` глобальный VCS cache с eviction policy.
  - Делать только после завершения MVP и дипломно-достаточного уровня.
- [ ] `VCS-904 [OPTIONAL/PROD]` policy-as-code для ref/branch restrictions.
  - Делать только после завершения MVP и дипломно-достаточного уровня.

## Definition of Done для сервиса

- [ ] Все MVP задачи закрыты.
- [ ] Сервис покрыт unit и integration/contract tests.
- [ ] Dockerfile и Kubernetes manifests есть.
- [ ] Сервис не зависит от master-service/ui.
- [ ] `jobExecutionId` используется во всех logs/events/artifacts.
- [ ] Секреты не попадают в логи.
- [ ] README сервиса объясняет local run и ограничения.

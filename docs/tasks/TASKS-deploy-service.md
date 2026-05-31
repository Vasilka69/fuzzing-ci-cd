# TASKS — Deploy-сервис (deploy-service)

Дата: 2026-05-29
Модуль: `services/deploy-service`
Topic: `jobs.deploy`

## Перед началом

Агент должен прочитать:

1. `/AGENTS.md`
2. `/services/deploy-service/AGENTS.md`
3. `/docs/prd/PRD-deploy-service.md`
4. этот файл
5. `/docs/context/PROJECT_CONTEXT.md` только если требуется общий поток pipeline

## Уровень 1. MVP

- [x] `DEPLOY-001 [MVP]` file-copy deployment.
  - Готово: реализован локальный file-copy из `storage://` artifact в configured target root; добавлены unit/integration tests и contract assertion `JOB_FINISHED`.
- [ ] `DEPLOY-002 [MVP]` ssh-bash deployment для Linux target.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.
- [ ] `DEPLOY-003 [MVP]` release_id generation/validation.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.
- [ ] `DEPLOY-004 [MVP]` manifest generation.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.
- [ ] `DEPLOY-005 [MVP]` basic healthcheck.
  - Готово, когда: есть реализация, unit/integration test и событие результата покрыто contract assertion.

## Уровень 2. Дипломно-достаточная полнота

- [ ] `DEPLOY-101 [DIPLOMA]` Docker, Docker Compose, systemd modes.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.
- [ ] `DEPLOY-102 [DIPLOMA]` rollback policy restore_previous_artifact.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.
- [ ] `DEPLOY-103 [DIPLOMA]` protected environment approval boundary handled by master contract.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.
- [ ] `DEPLOY-104 [DIPLOMA]` idempotency check_existing_release.
  - Готово, когда: поведение описано в README сервиса и покрыто тестом или demo-сценарием.

## Уровень 3. Общие hardening-задачи сервиса

- [x] `DEPLOY-701 [HARDENING]` Добавить structured metrics: active jobs, duration, success/failure count by errorType.
  - Готово централизованно через `cicd-executor-core`; `/actuator/metrics` открыт в сервисе.
- [ ] `DEPLOY-702 [HARDENING]` Добавить graceful shutdown: consumer перестает брать новые сообщения, активная job корректно завершается/отменяется.
- [ ] `DEPLOY-703 [HARDENING]` Добавить negative tests на отсутствие секретов в logs/events/artifacts.
- [ ] `DEPLOY-704 [HARDENING]` Добавить Kubernetes resource tuning и documented defaults.
- [ ] `DEPLOY-705 [HARDENING]` Добавить troubleshooting раздел: типовые ошибки и действия оператора.

## Уровень 4. Опционально / production-level

- [ ] `DEPLOY-901 [OPTIONAL/PROD]` progressive/canary deployments.
  - Делать только после завершения MVP и дипломно-достаточного уровня.
- [ ] `DEPLOY-902 [OPTIONAL/PROD]` Kubernetes deployment target management.
  - Делать только после завершения MVP и дипломно-достаточного уровня.
- [ ] `DEPLOY-903 [OPTIONAL/PROD]` blue-green orchestration.
  - Делать только после завершения MVP и дипломно-достаточного уровня.
- [ ] `DEPLOY-904 [OPTIONAL/PROD]` multi-region rollout.
  - Делать только после завершения MVP и дипломно-достаточного уровня.
- [ ] `DEPLOY-905 [OPTIONAL/PROD]` policy engine for approvals.
  - Делать только после завершения MVP и дипломно-достаточного уровня.

## Definition of Done для сервиса

- [ ] Все MVP задачи закрыты.
- [ ] Сервис покрыт unit и integration/contract tests.
- [ ] Dockerfile и Kubernetes manifests есть.
- [ ] Сервис не зависит от master-service/ui.
- [ ] `jobExecutionId` используется во всех logs/events/artifacts.
- [ ] Секреты не попадают в логи.
- [ ] README сервиса объясняет local run и ограничения.

# PRD — Deploy-сервис (Deployment service)

Дата: 2026-05-29
Модуль: `services/deploy-service`
Kafka topic: `jobs.deploy`
Job templates: `deploy/ssh-bash`, `deploy/windows-cmd`, `deploy/file-copy`, `deploy/docker`, `deploy/docker-compose`, `deploy/systemd`

## 1. Назначение

Deploy-сервис доставляет release artifact в целевую среду, фиксирует release_id, deployment manifest, healthcheck и rollback metadata.

## 2. Scope

### In scope для MVP

- file-copy deployment
- ssh-bash deployment для Linux target
- release_id generation/validation
- manifest generation
- basic healthcheck

### Дипломно-достаточный scope

- Docker, Docker Compose, systemd modes
- rollback policy restore_previous_artifact
- protected environment approval boundary handled by master contract
- idempotency check_existing_release

### Опционально / production-level, не обязательно для магистерской работы

- progressive/canary deployments
- Kubernetes deployment target management
- blue-green orchestration
- multi-region rollout
- policy engine for approvals

## 3. Входные параметры job

- `deployment_type`
- `release_id` — опциональный; если не передан, сервис генерирует стабильный
  `release-<jobExecutionId>`
- `artifact_uri`
- `environment`
- `target connection/credentials_ref`
- `commands/compose/systemd config`
- `healthcheck` — опциональный объект; для MVP поддерживается `enabled`, по умолчанию `true`
- `rollback policy`
- `idempotency policy`

Для MVP `deploy/file-copy` поддерживает локальный target root executor-а. `target.destination_path`
должен быть относительным путем внутри этого root; absolute path и `../` запрещены.

## 4. Результат job

- `release identifier`
- `deployment_manifest_uri`
- `healthcheck result`
- `rollback result/metadata`
- `deployed artifact checksum`
- `logs_uri или JOB_LOG документы`

Для MVP `deploy/file-copy` итоговое событие содержит `deploymentType=file_copy`,
`artifactUri`, `environment`, `destinationPath`, `relativeDestinationPath`, `bytesCopied`,
`deployedArtifactChecksum`, `checksumVerified`, `healthcheck`, `releaseId` и, если передан,
`connectionRef`. Basic healthcheck проверяет наличие deployed artifact, размер и SHA-256 на
локальном target root.

Для MVP `deploy/ssh-bash` итоговое событие содержит `deploymentType=ssh_bash`,
`artifactUri`, `environment`, `targetHost`, `targetPort`, `targetUser`, `destinationPath`,
`backupExisting`, `bytesCopied`, `deployedArtifactChecksum`, `checksumVerified=false`,
`commandCount`, `healthcheck`, `releaseId` и, если передан, `credentialsRef`. Basic healthcheck
после копирования и пользовательских команд выполняет SSH-проверку наличия файла в
`copy.destination_path`. Сервис использует системные `ssh`/`scp` через общий process runner, не
принимает значения SSH-секретов в params и фиксирует только `credentials_ref` для будущего
SecretResolver. `copy.destination_path` должен быть absolute path без `..`, whitespace и control
characters.

MVP `healthcheck` в `JOB_FINISHED.additionalData` и `deployment-manifest.json` содержит
`enabled`, `type`, `status`, `passed`, `durationMs`, `details`. Для `deploy/file-copy` type равен
`file_exists_checksum`, для `deploy/ssh-bash` — `ssh_file_exists`. Провал healthcheck завершает job
со статусом `FAILED` и `error.type=infrastructure_error`.

Для MVP оба поддержанных шаблона публикуют deployment manifest как artifact типа
`deployment_manifest` с именем `deployment-manifest.json` и `contentType=application/json`.
Итоговое событие содержит `deploymentManifestUri`, `deploymentManifestArtifactId`,
`deploymentManifestSizeBytes` и `deploymentManifestChecksumSha256`; сам JSON manifest хранится
в internal storage и не передается inline через Kafka.

## 5. Общие требования executor-а

- Получает сообщения только из `jobs.deploy`.
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
services/deploy-service/
├── AGENTS.md
├── Dockerfile
├── pom.xml
└── src/
    ├── main/java/.../
    │   ├── deployservice/
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

- неидемпотентные внешние побочные эффекты
- rollback вместо cancel
- утечка SSH key
- команды deployment без audit trail
- повторная доставка job после успешного deploy

## 8. Тестирование

- unit tests release_id/idempotency
- integration test local ssh container
- manifest schema contract test
- healthcheck success/failure tests
- rollback policy tests

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

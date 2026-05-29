# PRD — VCS-сервис (VCS integration service)

Дата: 2026-05-29
Модуль: `services/vcs-service`
Kafka topic: `jobs.vcs`
Job templates: `vcs/git`, `vcs/mercurial`

## 1. Назначение

VCS-сервис получает исходный код из VCS, фиксирует commit/revision и готовит воспроизводимый source snapshot для следующих этапов pipeline.

## 2. Scope

### In scope для MVP

- Git checkout shallow clone
- маскирование credentials в логах
- архивация snapshot в tar.gz
- upload snapshot через storage-client
- публикация JOB_RUNNING/JOB_FINISHED/JOB_LOG

### Дипломно-достаточный scope

- Mercurial checkout
- submodules policy
- лимиты размера snapshot
- retry для временной недоступности VCS
- идемпотентность по jobExecutionId

### Опционально / production-level, не обязательно для магистерской работы

- поддержка SVN
- зеркалирование репозиториев
- глобальный VCS cache с eviction policy
- policy-as-code для ref/branch restrictions

## 3. Входные параметры job

- `vcs_type`
- `repository_url`
- `ref`
- `ref_type`
- `checkout_depth`
- `submodules`
- `credentials_ref или признак публичного repository`
- `snapshot_policy`

## 4. Результат job

- `source_snapshot_uri`
- `commit_hash или revision_id`
- `repository metadata без секретов`
- `checksum`
- `size_bytes`
- `logs_uri или JOB_LOG документы`

## 5. Общие требования executor-а

- Получает сообщения только из `jobs.vcs`.
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
services/vcs-service/
├── AGENTS.md
├── Dockerfile
├── pom.xml
└── src/
    ├── main/java/.../
    │   ├── vcsservice/
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

- утечка токена в URL/логах
- очень большой repository
- невоспроизводимый checkout по branch без commit hash
- повторная доставка Kafka-сообщения

## 8. Тестирование

- unit tests для маскирования URL/секретов
- contract tests для job params/result
- integration test с локальным Git repository
- idempotency test: повторная доставка не создает конфликтующий snapshot

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

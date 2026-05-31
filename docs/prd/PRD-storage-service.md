# PRD — Storage-сервис (Internal storage service)

Дата: 2026-05-29
Модуль: `services/storage-service`
Kafka topic: `jobs.storage`
Job templates: `storage/source-snapshot`, `storage/promote-artifact`, `storage/cleanup`

## 1. Назначение

Storage-сервис служит API-слоем над физическим хранилищем артефактов, source snapshot, отчетов, corpus/crash cases и release packages.

## 2. Scope

### In scope для MVP

- локальный filesystem backend
- storage:// URI namespace
- upload/download REST API для executor-ов
- sha256 verification
- cleanup temporary artifacts

### Дипломно-достаточный scope

- S3-compatible/MinIO adapter
- promote release artifact
- retention policy
- artifact manifest generation
- streaming upload/download without loading full file into memory

### Опционально / production-level, не обязательно для магистерской работы

- репликация между backend-ами
- content-addressable storage
- lifecycle policies через объектное хранилище
- deduplication по hash

## 3. Входные параметры job

- `operation: save/copy/promote/cleanup`
- `source_uri`
- `destination_policy`
- `retention_policy`
- `checksum policy`
- `metadata`

## 4. Результат job

- `storage_uri`
- `checksum`
- `size_bytes`
- `content_type`
- `metadata`
- `retention/expires_at`
- `status`

## 4.1. REST API для executor-ов

MVP REST API использует тот же `storage://` namespace, что и job results. Большие файлы передаются через HTTP body, а в Kafka events/jobs остаются только URI и metadata.

- `PUT /artifacts/{namespacePath}` — сохраняет файл в local filesystem backend. Headers: `Content-Type`, `X-CICD-Artifact-Type`, `X-CICD-Artifact-Name`. Response: `ArtifactDescriptor` в JSON с `uri=storage://{namespacePath}`, `sizeBytes`, `checksumSha256`.
- `GET /artifacts/{namespacePath}` — возвращает файл из local filesystem backend. Если artifact отсутствует, возвращает `404` с JSON-ошибкой.

`namespacePath` обязан быть относительным путем без traversal, query/fragment и недопустимых символов. Повторный `PUT` в тот же namespace идемпотентен только при совпадающем содержимом; конфликт содержимого возвращает `409`.

## 5. Общие требования executor-а

- Получает сообщения только из `jobs.storage`.
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
services/storage-service/
├── AGENTS.md
├── Dockerfile
├── pom.xml
└── src/
    ├── main/java/.../
    │   ├── storageservice/
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

- передача больших файлов через Kafka вместо URI
- несогласованный namespace URI
- перезапись чужих артефактов
- рост диска без retention

## 8. Тестирование

- unit tests для URI и checksum
- integration tests upload/download large file
- retention cleanup test
- contract test для storage job result

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

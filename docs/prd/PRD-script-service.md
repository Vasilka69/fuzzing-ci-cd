# PRD — Script-сервис (Script execution service)

Дата: 2026-05-29
Модуль: `services/script-service`
Kafka topic: `jobs.script`
Job templates: `script/bash`, `script/cmd`

## 1. Назначение

Script-сервис выполняет пользовательские Bash/cmd сценарии для нестандартных этапов pipeline в ограниченном sandbox-окружении.

## 2. Scope

### In scope для MVP

- Bash script в контейнерном sandbox
- input artifacts download
- expected outputs upload
- stdout/stderr chunking
- network none by default

### Дипломно-достаточный scope

- cmd mode через Windows-compatible runner or documented limitation
- image whitelist
- secret redaction
- resource limits pids/stdout/disk
- cancel handling

### Опционально / production-level, не обязательно для магистерской работы

- PowerShell, Python, Node runners
- interactive debugging workspace
- marketplace of script templates
- remote cache for dependencies

## 3. Входные параметры job

- `script_type`
- `script или script_artifact_uri`
- `input_artifacts`
- `environment`
- `working_directory`
- `timeout`
- `resource_limits`
- `expected_outputs`
- `sandbox_policy`

## 4. Результат job

- `exit_code`
- `logs`
- `output_artifacts`
- `runtime metadata`
- `timeout/resource-limit flags`
- `summary`

## 5. Общие требования executor-а

- Получает сообщения только из `jobs.script`.
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
services/script-service/
├── AGENTS.md
├── Dockerfile
├── pom.xml
└── src/
    ├── main/java/.../
    │   ├── scriptservice/
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

- запуск непроверенного пользовательского кода
- privileged/host network/hostPath/Docker socket
- fork bomb/log flooding
- секреты в stdout/stderr

## 8. Тестирование

- sandbox policy validator tests
- bash happy path integration test
- network denied test
- stdout limit test
- expected output artifact test
- secret redaction test

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

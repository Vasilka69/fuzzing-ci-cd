# PRD — Build-сервис (Build service)

Дата: 2026-05-29
Модуль: `services/build-service`
Kafka topic: `jobs.build`
Job templates: `build/maven`, `build/gradle`, `build/javac`, `build/gcc`

## 1. Назначение

Build-сервис выполняет сборку проектов из source snapshot с контролируемым entrypoint инструмента и публикует build artifacts.

## 2. Scope

### In scope для MVP

- Maven и Gradle сборка в изолированном workspace
- скачивание source snapshot из storage
- expected_artifacts glob resolver
- сбор stdout/stderr с ограничением размера
- публикация build artifacts одним archive bundle

### Дипломно-достаточный scope

- Javac и GCC
- dependency cache с безопасным namespace
- resource limits и timeout
- mapping error types: user_code_error/infrastructure_error/timeout
- контрактные тесты событий

### Опционально / production-level, не обязательно для магистерской работы

- Bazel/CMake/NPM build adapters
- distributed build cache
- parallel build farm scheduling
- SLSA provenance attestation

## 3. Входные параметры job

- `build_tool`
- `source_snapshot_uri`
- `working_directory`
- `entrypoint`
- `args`
- `expected_artifacts`
- `toolchain/runtime versions`
- `environment`
- `sandbox_policy`

## 4. Результат job

- `build_artifacts` — один `build-artifacts.tar.gz` artifact bundle в internal storage
- `exit_code`
- `duration_ms`
- `build metrics`
- `logs_uri или JOB_LOG документы`
- `artifact manifest` — `artifact-manifest.json` внутри bundle с путями файлов, patterns и размерами

Для MVP build-service публикует найденные `expected_artifacts` одним архивом:

- storage namespace: `build-artifacts/<jobExecutionId>/build-artifacts.tar.gz`;
- `artifactType`: `build_artifacts`;
- `contentType`: `application/gzip`;
- внутри архива: каталог `artifacts/` с сохранением относительных путей и `artifact-manifest.json`.

## 5. Общие требования executor-а

- Получает сообщения только из `jobs.build`.
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
services/build-service/
├── AGENTS.md
├── Dockerfile
├── pom.xml
└── src/
    ├── main/java/.../
    │   ├── buildservice/
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

- запуск произвольной команды вместо whitelist entrypoint
- сетевой доступ вне allowlist
- переполнение stdout/stderr
- неочищенный workspace

## 8. Тестирование

- unit tests для command builder whitelist
- integration test Maven sample project
- integration test Gradle sample project
- timeout/resource-limit behavior test
- artifact glob negative case

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

# PRD — Executor-слой микросервисной CI/CD-системы

Дата: 2026-05-29
Статус: рабочая спецификация для agentic/vibe coding разработки.

## 1. Цель

Реализовать многомодульный Maven-проект с исполняющими микросервисами CI/CD-системы. Каждый микросервис является отдельным Spring Boot модулем, готовым к контейнеризации и развертыванию в Kubernetes. UI и master-service реализуются отдельно другим участником; текущий workstream отвечает за executor-слой, общие контракты и runtime-инфраструктуру executor-ов.

## 2. Проблема

В системе есть master-service, который управляет pipeline/stage/job, но ресурсоемкие операции не должны выполняться внутри master. Для сборки, фаззинга, деплоя, пользовательских сценариев, VCS checkout и хранения артефактов нужны независимые executor-ы, масштабируемые по типу нагрузки.

Дополнительная особенность проекта — встроенный fuzzing service с AFL++ и LLM-assisted генерацией входных данных. Для диплома важно показать не только CI/CD-процесс, но и security testing как штатный этап pipeline.

## 3. Scope

### In scope

- Многомодульный Maven parent project.
- Общие модули контрактов и executor runtime.
- `vcs-service`.
- `storage-service`.
- `build-service`.
- `fuzzing-service` с интеграцией готового fuzzing-ядра.
- `deploy-service`.
- `script-service`.
- Dockerfile для каждого исполняемого сервиса.
- Kubernetes manifests/templates для каждого сервиса.
- Локальная dev-инфраструктура: Kafka, OpenSearch, PostgreSQL как contract dependency, storage backend.
- Contract tests для Kafka/OpenSearch/job params/results.
- Документация, Javadocs для сложной логики, эксплуатационные инструкции.

### Out of scope

- Полноценный UI.
- Полная реализация master-service orchestration.
- Полноценная RBAC/admin-панель.
- Production-grade HA deployment всей платформы.
- Enterprise multi-tenancy.
- Собственный LLM provider.

## 4. Пользователи и заинтересованные стороны

- Разработчик pipeline: хочет автоматически получать код, собирать проект, запускать fuzzing/script/deploy job и видеть артефакты.
- Оператор/администратор: хочет контролировать deployment targets, секреты, логи и статусы executor-ов.
- Исследователь/автор диплома: хочет продемонстрировать микросервисную архитектуру, асинхронные контракты и LLM-assisted fuzzing.
- Master-service/UI разработчик: нуждается в стабильном контракте executor-ов.

## 5. Архитектурные принципы

1. Executor-ы stateless по бизнес-состоянию pipeline.
2. Master-service — источник истины для `pipeline_run`, `job_execution`, статусов и orchestration.
3. Executor получает job message из Kafka, выполняет работу, публикует events/results и artifact metadata.
4. Большие данные передаются через storage URI, а не через Kafka.
5. Текстовые логи пишутся в OpenSearch как `JOB_LOG` документы.
6. `jobExecutionId` — главный идентификатор идемпотентности и связи logs/events/artifacts.
7. Sandbox и least privilege — обязательны для build/fuzzing/script.
8. Русский язык — для пользовательских сообщений, логов и документации.

## 6. Сервисы

| Сервис | Topic | MVP назначение | Дипломно-достаточное расширение |
| --- | --- | --- | --- |
| `vcs-service` | `jobs.vcs` | получает исходный код из VCS, фиксирует commit/revision и готовит воспроизводимый source snapshot для следующих этапов pipeline | `vcs/git`, `vcs/mercurial` |
| `storage-service` | `jobs.storage` | служит API-слоем над физическим хранилищем артефактов, source snapshot, отчетов, corpus/crash cases и release packages | `storage/source-snapshot`, `storage/promote-artifact`, `storage/cleanup` |
| `build-service` | `jobs.build` | выполняет сборку проектов из source snapshot с контролируемым entrypoint инструмента и публикует build artifacts | `build/maven`, `build/gradle`, `build/javac`, `build/gcc` |
| `fuzzing-service` | `jobs.fuzzing` | запускает AFL++ fuzzing target-ов и интегрирует готовое fuzzing-ядро с LLM-assisted генерацией структурных входов через worker/custom mutator | `fuzzing/afl-llm` |
| `deploy-service` | `jobs.deploy` | доставляет release artifact в целевую среду, фиксирует release_id, deployment manifest, healthcheck и rollback metadata | `deploy/ssh-bash`, `deploy/windows-cmd`, `deploy/file-copy`, `deploy/docker`, `deploy/docker-compose`, `deploy/systemd` |
| `script-service` | `jobs.script` | выполняет пользовательские Bash/cmd сценарии для нестандартных этапов pipeline в ограниченном sandbox-окружении | `script/bash`, `script/cmd` |

## 7. Общие требования к executor-ам

Каждый executor должен:

- подписываться на свой Kafka topic;
- валидировать `schemaVersion`, `jobType`, `templatePath`, обязательные параметры и `jobExecutionId`;
- создавать workspace на каждый `jobExecutionId`;
- публиковать `JOB_RUNNING` при начале обработки;
- соблюдать `timeoutSeconds`, `resourceLimits`, `workspacePolicy`;
- маскировать секреты до публикации логов;
- сохранять output artifacts через storage service/client;
- публиковать `JOB_LOG` и итоговый `JOB_FINISHED`;
- различать `validation_error`, `user_code_error`, `infrastructure_error`, `timeout`, `security_error`;
- быть идемпотентным при повторной доставке задания;
- очищать workspace, если `cleanup=always`;
- иметь `/actuator/health`, `/actuator/prometheus` или эквивалентные endpoints.

## 8. Контракты интеграции

### Kafka topics

| Topic | Producer | Consumer | Назначение |
| --- | --- | --- | --- |
| `jobs.vcs` | master-service | vcs-service | Checkout/snapshot job. |
| `jobs.storage` | master-service | storage-service | Storage operations. |
| `jobs.build` | master-service | build-service | Build job. |
| `jobs.fuzzing` | master-service | fuzzing-service | Fuzzing job. |
| `jobs.deploy` | master-service | deploy-service | Deployment job. |
| `jobs.script` | master-service | script-service | User script job. |
| `jobs.results` | executor-ы | master-service | Status/result events при Kafka transport. |
| `jobs.dead-letter` | executor-ы/common runtime | operator/master | Dead-letter после retries. |

### OpenSearch

Индекс по умолчанию: `cicd-executor-events`.

- `JOB_LOG` документы содержат `logs`.
- Служебные event-документы не содержат больших логов.
- Обязательные поля: `documentId`, `ingestedAt`, `sourceService`, `eventType`, `pipelineId`, `jobId`, `jobExecutionId`, `status`, `additionalData`.

### Artifact URI

Рекомендуемый namespace:

```text
storage://pipelines/{pipelineId}/runs/{pipelineRunId}/jobs/{jobId}/executions/{jobExecutionId}/...
```

## 9. Требования к fuzzing-service

Fuzzing service должен интегрировать готовое fuzzing-ядро, не переписывая его без необходимости. Java/Spring Boot сервис выступает orchestrator/adaptor:

- принимает job;
- подготавливает target, seed corpus, config;
- запускает готовое ядро/AFL++ runner;
- управляет LLM worker или fake worker;
- собирает stats/crashes/hangs/corpus;
- интерпретирует policy `fail_on_crash`/`fail_on_hang`;
- публикует отчет и artifacts.

`afl_custom_fuzz()` не должен выполнять сетевые LLM-запросы в hot path. Candidate generation выполняется заранее отдельным worker-ом, а custom mutator быстро берет готовые inputs из очереди; при miss используется локальная fallback-мутация.

## 10. Нефункциональные требования

- Масштабирование каждого executor-а независимо.
- Запуск в контейнере без root/privileged режима по умолчанию.
- Kubernetes resource requests/limits для CPU, memory, ephemeral storage.
- Graceful shutdown: consumer перестает брать новые job, активные job завершаются или получают cancel/timeout по политике.
- Structured logs с `correlationId`, `jobExecutionId`, `jobType`, `sourceService`.
- Никаких секретов в logs/events/artifacts.
- Contract compatibility: `schemaVersion=1` не ломается без ADR.
- Минимальный runtime должен подниматься локально через docker compose.

## 11. Acceptance criteria MVP

MVP считается выполненным, если:

1. Maven multi-module project собирается одной командой `./mvnw clean verify`.
2. У каждого сервиса есть Spring Boot приложение, Kafka consumer, Dockerfile, Kubernetes manifest.
3. Common contracts покрыты serialization/contract tests.
4. VCS service делает Git checkout и сохраняет snapshot.
5. Storage service сохраняет/отдает artifacts через `storage://` URI.
6. Build service собирает Maven/Gradle sample project и публикует artifact.
7. Fuzzing service запускает готовое fuzzing ядро в fake/local worker mode на demo target и публикует report/crash artifacts.
8. Deploy service выполняет file-copy или ssh-bash deployment в тестовый target/container.
9. Script service выполняет Bash script в sandbox и публикует output artifacts.
10. Logs/events имеют `jobExecutionId`; большие логи не попадают в итоговое служебное событие.
11. Все сервисы работают без прямого доступа к БД master-service.

## 12. Acceptance criteria дипломно-достаточного уровня

- Поддержаны все job templates из миграции `V0002__data.sql` хотя бы на уровне валидации и documented limitations.
- Реализованы retry/timeout/error typing/idempotency для каждого сервиса.
- OpenSearch log publishing работает для всех executor-ов.
- Есть integration tests с Testcontainers для Kafka/OpenSearch/storage where practical.
- Kubernetes manifests включают probes, env, securityContext, resources.
- Fuzzing service поддерживает fake и real OpenAI-compatible endpoint mode.
- Есть демонстрационный end-to-end сценарий pipeline: VCS -> Storage -> Build -> Fuzzing -> Storage -> Deploy/Script.

## 13. Метрики успеха

- Время поднятия локальной dev-среды.
- Процент задач, закрытых с тестами.
- Доля контрактов, покрытых tests/schemas.
- Количество ручных шагов для запуска demo pipeline.
- Воспроизводимость fuzzing demo на clean environment.
- Отсутствие секретов в логах и artifacts.

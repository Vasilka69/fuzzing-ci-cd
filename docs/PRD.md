# PRD: Исполняющий слой микросервисной CI/CD-системы

## 1. Назначение

Документ описывает продуктовые и инженерные требования к реализации исполняющего слоя CI/CD-системы. Управляющий сервис и UI считаются внешними потребителями/поставщиками контрактов и реализуются отдельно.

## 2. Цель

Реализовать multi-module Maven проект, в котором каждый исполняющий микросервис является отдельным модулем и может независимо собираться, тестироваться, контейнеризоваться и разворачиваться в Kubernetes.

## 3. Scope

В scope входят:

- общие контракты Kafka job/result events;
- общий executor framework;
- VCS executor;
- internal storage executor/API layer;
- build executor;
- fuzzing executor с интеграцией готового AFL++/LLM fuzzing ядра;
- deployment executor;
- script executor;
- Dockerfile для каждого executable service;
- Kubernetes manifests для всех executable services;
- локальная dev-инфраструктура для Kafka/PostgreSQL/storage при необходимости;
- unit, integration и contract tests.

Вне scope:

- UI;
- master-service;
- полноценный pipeline scheduler;
- пользовательская авторизация;
- production secret manager implementation, кроме интерфейса и env/k8s-secret adapter;
- полноценная observability platform, кроме structured logs/metrics/tracing hooks.

## 4. Пользователи и интеграции

Основной пользователь исполняющего слоя — master-service. Он публикует задания в Kafka topics и получает status/result events. Executor'ы не должны напрямую менять состояние pipeline в БД master-service.

Интеграции:

- Kafka;
- object/internal storage;
- VCS endpoints;
- repository manager;
- Docker registry;
- target deployment hosts;
- LLM endpoint;
- Kubernetes Secret/env/file providers.

## 5. Архитектурные решения

### 5.1 Maven multi-module

Корневой проект должен собирать все сервисы и общие библиотеки. Каждый сервис остается независимым executable Spring Boot application.

Минимальные modules:

- `common/common-contracts`
- `common/common-kafka`
- `common/common-storage-client`
- `common/common-observability`
- `common/common-testing`
- `services/vcs-service`
- `services/storage-service`
- `services/build-service`
- `services/fuzzing-service`
- `services/deploy-service`
- `services/script-service`
- `fuzzing-engine/afl-llm-engine` или adapter к готовому ядру

### 5.2 Kafka message model

Executor'ы получают задания из dedicated topics и публикуют результаты в общий `jobs.results`.

Входные topics:

- `jobs.vcs`
- `jobs.storage`
- `jobs.build`
- `jobs.fuzzing`
- `jobs.deploy`
- `jobs.script`

Общие topics:

- `jobs.results`
- `jobs.dead-letter`

### 5.3 Artifact transfer

Kafka используется только для metadata. Source snapshots, build artifacts, fuzzing reports, crash cases, logs и deployment manifests передаются через storage URI.

### 5.4 Executor statelessness

Executor'ы stateless относительно бизнес-состояния pipeline. Локально допускается только временный workspace и идемпотентный cache/result marker по `job_execution_id`.

### 5.5 Контейнеризация и Kubernetes

Каждый сервис обязан иметь Dockerfile. Kubernetes manifests должны включать:

- Deployment;
- Service, если нужен HTTP endpoint;
- ConfigMap/Secret references;
- resource requests/limits;
- readiness/liveness/startup probes, если сервис имеет HTTP actuator;
- env vars для Kafka topics, storage, timeouts;
- securityContext без privileged mode по умолчанию.

## 6. Общие функциональные требования

| ID | Требование |
| --- | --- |
| FR-SYS-01 | Каждый executor читает только свой Kafka topic. |
| FR-SYS-02 | Каждый executor публикует `accepted`/`running` и итоговый `completed`/`failed`/`timeout` event. |
| FR-SYS-03 | Все result events используют единый schema version. |
| FR-SYS-04 | Все executor'ы поддерживают timeout, resource limits и workspace cleanup. |
| FR-SYS-05 | Все executor'ы маскируют секреты в логах. |
| FR-SYS-06 | Все executor'ы возвращают structured error object. |
| FR-SYS-07 | Все executor'ы сохраняют logs как artifact или storage object. |
| FR-SYS-08 | Повторная доставка сообщения по тому же `job_execution_id` не создает конфликтующие artifacts. |
| FR-SYS-09 | Dead-letter используется для невалидных сообщений или исчерпанных retry. |
| FR-SYS-10 | Все сервисы готовы к Docker/Kubernetes deployment. |

## 7. Нефункциональные требования

| ID | Требование |
| --- | --- |
| NFR-SYS-01 | Java 17+ и Spring Boot 3.x. |
| NFR-SYS-02 | Maven build должен проходить из корня. |
| NFR-SYS-03 | Contract DTO не дублируются между сервисами. |
| NFR-SYS-04 | Integration tests используют Testcontainers или эквивалентный isolated setup. |
| NFR-SYS-05 | Logs структурированы и содержат `correlation_id`, `job_execution_id`, `worker_id`. |
| NFR-SYS-06 | Сервисы имеют externalized configuration через env vars. |
| NFR-SYS-07 | Container images не запускаются от root, кроме технически обоснованных случаев. |
| NFR-SYS-08 | Fuzzing и script execution ограничиваются CPU/memory/disk/time. |

## 8. Критерии приемки

Система считается готовой на уровне executor MVP, если:

- `mvn verify` проходит из корня;
- каждый сервис имеет executable Spring Boot module;
- каждый сервис имеет Dockerfile;
- Kubernetes manifests проходят dry-run validation;
- contract tests подтверждают чтение job envelope и публикацию result envelope;
- build service выполняет Maven/Gradle/Javac/GCC job на тестовом snapshot;
- fuzzing service запускает готовое fuzzing ядро и сохраняет report/crash artifacts;
- deploy service выполняет как минимум idempotent dry-run/local deployment mode и один реальный SSH/Docker сценарий в test environment;
- script service выполняет Bash/cmd сценарий в ограниченном workspace;
- storage service сохраняет и выдает artifacts по URI;
- секреты не появляются в stdout/stderr/log artifacts.

## 9. Риски

| Риск | Митигирующее действие |
| --- | --- |
| Контекст агента переполняется всей системой | Разделять PRD/TASKS по сервисам и использовать локальные документы. |
| DTO расползутся по сервисам | Вынести контракты в `common-contracts`. |
| Fuzzing ядро сложно интегрировать | Сначала сделать adapter interface и fake engine test, затем подключать реальное ядро. |
| Script/deploy небезопасны | Sandbox, no privileged mode, whitelist images, redaction, resource limits. |
| K8s manifests отстают от env vars | Добавить task checklist: изменение config требует изменения deployment manifests. |

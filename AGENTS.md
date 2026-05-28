# AGENTS.md

## Назначение

Этот файл задает постоянные правила для AI coding agents, работающих над репозиторием дипломного проекта: микросервисной CI/CD-системой с исполняющим слоем. Цель инструкций — уменьшить шум в контекстном окне, не смешивать ответственность микросервисов и получать маленькие проверяемые изменения вместо больших неуправляемых переписываний.

## Главный принцип работы агента

Работай как инженерный агент, а не как генератор кода. Для любой нетривиальной задачи сначала изучи релевантные файлы, затем предложи короткий план, риски и проверки. Код меняй только после понимания границ задачи. Не делай большие rewrite без прямого требования.

## Текущий scope реализации

Реализуются только исполняющие микросервисы и инфраструктура их запуска:

- `vcs-service`
- `storage-service`
- `build-service`
- `fuzzing-service`
- `deploy-service`
- `script-service`
- общие библиотеки/контракты для executor'ов
- Dockerfile для каждого исполняющего сервиса
- Kubernetes manifests / Helm/Kustomize для развертывания executor'ов
- Maven multi-module структура

Не реализуются в этой ветке, если задача явно не говорит обратное:

- UI
- master-service / управляющий сервис
- пользовательская авторизация UI
- REST API master-service
- полноценная бизнес-логика pipeline scheduler

Можно создавать заглушки контрактов master-service только если они нужны для contract tests executor'ов.

## Целевая архитектура репозитория

Рекомендуемая структура:

```text
.
├── pom.xml
├── AGENTS.md
├── docs/
│   ├── PRD.md
│   ├── TASKS.md
│   └── services/
│       ├── vcs-service/
│       │   ├── PRD.md
│       │   └── TASKS.md
│       ├── storage-service/
│       ├── build-service/
│       ├── fuzzing-service/
│       ├── deploy-service/
│       └── script-service/
├── common/
│   ├── common-contracts/
│   ├── common-kafka/
│   ├── common-storage-client/
│   ├── common-observability/
│   └── common-testing/
├── services/
│   ├── vcs-service/
│   ├── storage-service/
│   ├── build-service/
│   ├── fuzzing-service/
│   ├── deploy-service/
│   └── script-service/
├── fuzzing-engine/
│   └── afl-llm-engine/
├── deploy/
│   ├── docker-compose/
│   └── k8s/
└── scripts/
```

Если реальная структура уже отличается, не переписывай ее целиком. Сначала предложи миграционный план.

## Maven правила

- Проект должен быть multi-module Maven project.
- Корневой `pom.xml` — parent/aggregator.
- Каждый исполняющий микросервис — отдельный Maven module.
- Общая логика executor lifecycle, Kafka envelope, DTO, error model, logging, idempotency helpers и test utilities должна выноситься в `common-*` modules.
- Не дублируй DTO Kafka-сообщений между сервисами.
- Не добавляй новую зависимость без объяснения причины и альтернатив.
- Предпочитай Spring Boot для сервисов и Testcontainers для интеграционных тестов Kafka/PostgreSQL/MinIO, если это применимо.

## Единый executor lifecycle

Каждый executor должен следовать циклу:

1. Получить job message из своего Kafka topic.
2. Провалидировать `schema_version`, `message_id`, `job_execution_id`, `job_type`, `template_path`, `attempt`, `timeout_seconds`, `resource_limits`.
3. Проверить идемпотентность по `job_execution_id`.
4. Опубликовать `running`/`accepted` event в `jobs.results`.
5. Создать изолированный workspace.
6. Скачать входные artifacts/snapshots по URI.
7. Разрешить секреты только через runtime secret provider, не логировать значения.
8. Выполнить работу с timeout/resource limits.
9. Собрать stdout/stderr, exit code, metrics, artifacts.
10. Загрузить artifacts/logs в storage.
11. Опубликовать итоговый event в `jobs.results`.
12. Очистить workspace согласно `workspace_policy`.

## Kafka contract

Входные topics:

- `jobs.vcs`
- `jobs.storage`
- `jobs.build`
- `jobs.fuzzing`
- `jobs.deploy`
- `jobs.script`

Выходные topics:

- `jobs.results`
- `jobs.dead-letter`

Ключ сообщения — `job_execution_id`. Большие файлы через Kafka не передавать. В Kafka передавать только metadata и URI.

## Security rules

- Секреты никогда не записывать в исходники, логи, Kafka payload, PostgreSQL, artifacts и crash reports.
- Shell-команды не собирать конкатенацией строк из пользовательского ввода.
- Build service запускает tool + args, а не произвольную shell-строку.
- Script service выполняет пользовательский код только в sandbox/container с лимитами.
- Fuzzing service и script service по умолчанию без privileged mode.
- Network access для script/fuzzing job должен быть запрещен по умолчанию или явно включен параметром.
- Не отключай тесты, static analysis или security checks ради зеленого результата.

## Контекстные правила для микросервисов

Перед работой над конкретным сервисом открой только его локальные документы:

- `docs/services/<service>/PRD.md`
- `docs/services/<service>/TASKS.md`
- релевантный module в `services/<service>`
- общие контракты в `common/common-contracts`

Не загружай все PRD/TASKS без необходимости. Верхнеуровневые документы нужны для системных решений и cross-service задач.

## Правила работы с задачами

Для каждой задачи используй формат:

```text
Цель:
Контекст:
Ограничения:
Готово, когда:
Проверки:
```

Если задача большая — разбей на вертикальные slices, которые дают проверяемый результат: contract → service handler → tests → Docker → k8s.

## Definition of Done

Изменение считается готовым, если:

- код компилируется;
- добавлены или обновлены тесты;
- проверены негативные кейсы;
- не нарушены Kafka contracts;
- не добавлены секреты;
- Docker image собирается для затронутого сервиса;
- Kubernetes manifest/values обновлены, если изменились env vars, ports, probes или resources;
- README/PRD/TASKS обновлены, если изменилось поведение;
- итоговое сообщение агента содержит: что изменено, какие проверки выполнены, какие риски остались.

## Команды проверки

Предпочтительные команды, если структура проекта уже создана:

```bash
mvn -q -DskipTests=false test
mvn -q -DskipTests package
mvn -q verify
```

Для отдельного модуля:

```bash
mvn -q -pl services/<service-name> -am test
mvn -q -pl services/<service-name> -am package
```

Docker:

```bash
docker build -f services/<service-name>/Dockerfile -t <service-name>:local .
```

Kubernetes manifests:

```bash
kubectl apply --dry-run=client -f deploy/k8s
```

Если команда недоступна в среде агента, не придумывай результат. Напиши, что не смог выполнить, и почему.

## Запрещенные паттерны

- Реализовывать master-service/UI в задачах executor scope.
- Дублировать Kafka DTO в каждом сервисе.
- Передавать бинарные artifacts через Kafka.
- Логировать секреты или credentials-bearing URLs.
- Игнорировать `timeout_seconds` и `resource_limits`.
- Treat найденный fuzzing crash как infrastructure failure: это результат тестирования, если target запустился корректно.
- Делать deployment retry без идемпотентного `release_id`/manifest.
- Добавлять зависимости без review.
- Удалять или отключать тесты.

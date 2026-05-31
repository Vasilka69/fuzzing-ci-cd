# Demo pipeline без UI/master

Demo показывает executor-слой без master-service и UI: mock master публикует заранее
заданный pipeline в Kafka, executor-ы обрабатывают jobs, кладут artifacts в общий
local storage volume, публикуют служебные events в `jobs.results`, а текстовые логи
пишут отдельными `JOB_LOG` документами в OpenSearch.

## Что входит

- `mock-master-publisher` публикует jobs для `vcs/git`, `build/maven`,
  `fuzzing/afl-llm`, `deploy/file-copy`, `script/bash`.
- `sample-repositories/demo-app` превращается compose-init контейнером в локальный
  Git repository `file:///demo/repositories/demo-app`.
- `docker-compose.demo.yml` поднимает все executor-сервисы вместе с Kafka,
  OpenSearch и общим local storage volume.
- `samples/executor-events.jsonl` и `samples/opensearch-log-documents.jsonl`
  фиксируют ожидаемую форму events/logs: в `JOB_FINISHED` поле `logs` равно `null`,
  текст находится в отдельном `JOB_LOG` документе.

## Запуск

```bash
docker compose -f docker-compose.yml -f docker-compose.demo.yml up --build
```

Для нового idempotency run можно задать другой `runId`:

```bash
CICD_DEMO_RUN_ID=demo-pipeline-2 docker compose -f docker-compose.yml -f docker-compose.demo.yml up --build
```

Mock publisher публикует стадии с паузой `CICD_DEMO_STAGE_DELAY`, по умолчанию `4s`.
Это нужно только для demo без master-service: реальные зависимости между стадиями
в production должен контролировать master/scheduler.

## Проверка

Kafka UI доступен на `http://127.0.0.1:8080`.

Ожидаемые topics:

- `jobs.vcs`, `jobs.build`, `jobs.fuzzing`, `jobs.deploy`, `jobs.script` — входные jobs;
- `jobs.results` — `JOB_RUNNING`, `JOB_ARTIFACT`, `JOB_FINISHED`;
- OpenSearch index `cicd-executor-events` — документы `JOB_LOG`.

Проверка через Kafka CLI:

```bash
docker compose -f docker-compose.yml -f docker-compose.demo.yml exec kafka \
  /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:19092 \
  --topic jobs.results \
  --from-beginning \
  --timeout-ms 10000
```

Проверка логов в OpenSearch:

```bash
curl 'http://127.0.0.1:9200/cicd-executor-events/_search?pretty'
```

Остановка demo:

```bash
docker compose -f docker-compose.yml -f docker-compose.demo.yml down
```

Очистка demo volumes:

```bash
docker compose -f docker-compose.yml -f docker-compose.demo.yml down -v
```

## Ограничения demo

- Demo не заменяет master-service: порядок стадий здесь задается задержкой публикации.
- `sample-repositories/demo-app/mvnw` является легким demo entrypoint-ом и не скачивает
  Maven из сети.
- Fuzzing stage использует `kernel_command`, который создает компактный AFL-like output
  для демонстрации artifact/report flow без запуска тяжелого AFL++ процесса.

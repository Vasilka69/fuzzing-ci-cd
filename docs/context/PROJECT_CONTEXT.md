# Контекст проекта для агентов

Дата: 2026-05-29

## Назначение системы

Система автоматизирует CI/CD pipeline и включает master-service, UI, Kafka, OpenSearch, PostgreSQL, internal storage и исполняющие микросервисы. Текущий репозиторий покрывает только executor-слой.

## Исполняющий слой

| Сервис | Topic | Назначение |
| --- | --- | --- |
| `vcs-service` | `jobs.vcs` | получает исходный код из VCS, фиксирует commit/revision и готовит воспроизводимый source snapshot для следующих этапов pipeline | `vcs/git`, `vcs/mercurial` |
| `storage-service` | `jobs.storage` | служит API-слоем над физическим хранилищем артефактов, source snapshot, отчетов, corpus/crash cases и release packages | `storage/source-snapshot`, `storage/promote-artifact`, `storage/cleanup` |
| `build-service` | `jobs.build` | выполняет сборку проектов из source snapshot с контролируемым entrypoint инструмента и публикует build artifacts | `build/maven`, `build/gradle`, `build/javac`, `build/gcc` |
| `fuzzing-service` | `jobs.fuzzing` | запускает AFL++ fuzzing target-ов и интегрирует готовое fuzzing-ядро с LLM-assisted генерацией структурных входов через worker/custom mutator | `fuzzing/afl-llm` |
| `deploy-service` | `jobs.deploy` | доставляет release artifact в целевую среду, фиксирует release_id, deployment manifest, healthcheck и rollback metadata | `deploy/ssh-bash`, `deploy/windows-cmd`, `deploy/file-copy`, `deploy/docker`, `deploy/docker-compose`, `deploy/systemd` |
| `script-service` | `jobs.script` | выполняет пользовательские Bash/cmd сценарии для нестандартных этапов pipeline в ограниченном sandbox-окружении | `script/bash`, `script/cmd` |

## Типовой поток данных

```text
VCS checkout
  -> Source snapshot
  -> Build
  -> Unit tests или Script checks
  -> Fuzzing testing
  -> Publish artifacts
  -> Deploy
  -> Post-deploy script или healthcheck
```

Сервисы не вызывают друг друга напрямую по бизнес-логике. Связь между ними идет через artifacts/storage URI и состояние pipeline, которым управляет master-service.

## Граница с master-service

Master-service:

- создает `pipeline_run` и `job_execution`;
- публикует job messages в Kafka;
- принимает executor events;
- обновляет статусы;
- читает логи из OpenSearch;
- предоставляет UI API.

Executor-service:

- получает job;
- выполняет конкретный тип работы;
- публикует events/logs/artifacts;
- не меняет master DB напрямую.

## Главные инварианты

- `jobExecutionId` должен присутствовать во всех событиях, логах и artifact URI.
- Большие данные не передаются через Kafka.
- Секреты передаются только ссылками.
- Логи пишутся в OpenSearch как `JOB_LOG`.
- Итоговый `JOB_FINISHED` не содержит больших logs payload.
- Каждый executor должен быть готов к Docker/Kubernetes runtime.

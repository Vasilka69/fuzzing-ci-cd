# Implementation Notes

## Почему PRD/TASKS разделены

Верхнеуровневые `docs/PRD.md` и `docs/TASKS.md` фиксируют системные решения: Maven multi-module, общие contracts, Kafka topics, lifecycle, Docker/K8s и cross-service acceptance. Локальные документы в `docs/services/<service>` нужны для работы агента с минимальным контекстом: при реализации build service агенту не нужно держать в окне детали deployment или VCS, кроме общих contracts.

## Рекомендуемый порядок разработки

1. Repository skeleton.
2. Common contracts.
3. Common Kafka/result publisher.
4. Common storage client with local backend.
5. Executor lifecycle framework.
6. Service MVPs по одному.
7. Dockerfile для каждого сервиса.
8. Kubernetes manifests.
9. End-to-end smoke test через Kafka messages без master-service.

## Принципы декомпозиции задач

- Каждая задача должна иметь проверяемый результат.
- Сначала общий contract, потом service implementation.
- Сначала fake/local adapters, потом реальные внешние integrations.
- Для fuzzing service сначала adapter к готовому ядру и fake engine tests, затем реальный AFL++ smoke.
- Для deployment сначала dry-run/local mode, затем SSH/Docker modes.

## Минимальные env vars для всех executor'ов

```text
SERVICE_NAME
WORKER_ID
KAFKA_BOOTSTRAP_SERVERS
KAFKA_GROUP_ID
JOBS_TOPIC
RESULTS_TOPIC
DEAD_LETTER_TOPIC
STORAGE_BASE_URI
DEFAULT_TIMEOUT_SECONDS
MAX_WORKSPACE_SIZE_MB
SECRET_PROVIDER
```

## Рекомендуемые package boundaries

```text
common-contracts:
  ru.<project>.contracts.job
  ru.<project>.contracts.result
  ru.<project>.contracts.artifact
  ru.<project>.contracts.error

common-kafka:
  ru.<project>.kafka.consumer
  ru.<project>.kafka.publisher
  ru.<project>.kafka.validation

common-storage-client:
  ru.<project>.storage

common-observability:
  ru.<project>.observability.logging
  ru.<project>.observability.metrics

services/<service>:
  ru.<project>.<service>.config
  ru.<project>.<service>.executor
  ru.<project>.<service>.validation
  ru.<project>.<service>.adapter
```

## Минимальный PR checklist

- [ ] Scope соответствует AGENTS.md.
- [ ] Изменения маленькие и reviewable.
- [ ] DTO не продублированы.
- [ ] Тесты добавлены/обновлены.
- [ ] Maven checks пройдены или честно указано, почему не запускались.
- [ ] Docker/K8s обновлены при изменении runtime config.
- [ ] Секреты не попали в код/логи/tests.
- [ ] PRD/TASKS обновлены при изменении поведения.

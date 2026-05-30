# Требования к Docker и Kubernetes для executor-сервисов

## Dockerfile

У каждого сервиса должен быть собственный `Dockerfile`.

Минимальные требования:

- multi-stage build;
- runtime image без build tools, кроме случаев build/fuzzing service, где tools нужны осознанно;
- non-root user;
- explicit `ENTRYPOINT`;
- healthcheck или actuator endpoint;
- no secrets baked into image;
- labels: service name, version, source revision if available.

## Kubernetes manifests

Для каждого сервиса:

- Deployment;
- ConfigMap;
- ServiceAccount;
- Service, если нужен HTTP/actuator доступ;
- Resource requests/limits;
- Probes: readiness/liveness/startup where needed;
- SecurityContext.

MVP-манифесты executor-сервисов находятся в `k8s/executors/`. Для каждого сервиса
описаны `Deployment`, `ConfigMap`, `ServiceAccount` и `Service` для actuator/health
доступа внутри кластера.

## Базовый securityContext

```yaml
securityContext:
  runAsNonRoot: true
  allowPrivilegeEscalation: false
  readOnlyRootFilesystem: true
  capabilities:
    drop: ["ALL"]
  seccompProfile:
    type: RuntimeDefault
```

Writable paths выносить в `emptyDir` или отдельный workspace volume.

## Resources

Указывать requests и limits:

```yaml
resources:
  requests:
    cpu: "250m"
    memory: "512Mi"
    ephemeral-storage: "1Gi"
  limits:
    cpu: "1"
    memory: "1Gi"
    ephemeral-storage: "4Gi"
```

Для `build-service` и `fuzzing-service` лимиты будут выше и задаются отдельно.

## Переменные окружения

Общие:

- `SERVICE_NAME`
- `KAFKA_BOOTSTRAP_SERVERS`
- `EXECUTOR_EVENTS_TRANSPORT`
- `OPENSEARCH_LOGS_ENABLED`
- `OPENSEARCH_ENDPOINT`
- `OPENSEARCH_EVENTS_INDEX`
- `STORAGE_ENDPOINT`
- `WORKSPACE_ROOT`
- `WORKER_ID`

Секреты передаются только через Kubernetes Secret/Vault/SecretResolver, не через ConfigMap.

## Развертывание fuzzing-service

`fuzzing-service` может требовать:

- отдельный image с AFL++;
- повышенные CPU/memory/ephemeral-storage;
- `emptyDir` для corpus/workspace;
- network disabled для fake mode;
- egress allowlist к LLM endpoint только для real mode.

Privileged mode не разрешается по умолчанию. Если AFL++/sanitizer требует особых capabilities, это оформляется отдельным ADR и не входит в MVP.

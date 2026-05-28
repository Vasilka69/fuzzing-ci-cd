# TASKS: VCS Integration Service

## VCS-001. Создать service module

- [ ] Добавить `services/vcs-service` в root Maven modules.
- [ ] Создать Spring Boot application.
- [ ] Подключить `common-contracts`, `common-kafka`, `common-storage-client`, `common-observability`.

Проверка: `mvn -q -pl services/vcs-service -am test`.

## VCS-002. Реализовать params validation

- [ ] `vcs_type` required.
- [ ] `repository_url` required and redacted in logs.
- [ ] `ref` required.
- [ ] `checkout_depth > 0` if provided.
- [ ] `snapshot_policy.max_size_mb > 0`.

## VCS-003. Реализовать Git checkout adapter

- [ ] Branch checkout.
- [ ] Tag checkout.
- [ ] Commit checkout.
- [ ] Shallow clone.
- [ ] Optional submodules.
- [ ] Resolve exact commit hash.

## VCS-004. Реализовать snapshot builder

- [ ] Archive workspace without `.git` by default.
- [ ] Enforce max size.
- [ ] Calculate SHA-256.
- [ ] Upload to storage.

## VCS-005. Result event

- [ ] Publish `running`.
- [ ] Publish `completed` with outputs.
- [ ] Publish `failed` with structured error.
- [ ] Upload logs.

## VCS-006. Idempotency

- [ ] Artifact path includes `pipeline_run_id/job_execution_id/attempt`.
- [ ] Duplicate message returns existing metadata or safely reuses same output.

## VCS-007. Docker and Kubernetes

- [ ] Add Dockerfile.
- [ ] Add k8s deployment.
- [ ] Add env vars: `JOBS_TOPIC=jobs.vcs`, `RESULTS_TOPIC`, `STORAGE_BASE_URI`.
- [ ] Add probes/resources/securityContext.

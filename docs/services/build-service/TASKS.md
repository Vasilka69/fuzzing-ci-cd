# TASKS: Build Service

## BUILD-001. Создать service module

- [ ] Maven module.
- [ ] Spring Boot application.
- [ ] Common dependencies.

## BUILD-002. Build params validation

- [ ] Validate `build_tool` in allowed set.
- [ ] Validate `source_snapshot_uri`.
- [ ] Validate `args` is array, not shell string.
- [ ] Validate `expected_artifacts`.

## BUILD-003. Snapshot handling

- [ ] Download source snapshot.
- [ ] Verify checksum if provided.
- [ ] Extract safely without zip-slip/tar path traversal.

## BUILD-004. Tool adapters

- [ ] Maven adapter.
- [ ] Gradle adapter.
- [ ] Javac adapter.
- [ ] GCC adapter.
- [ ] Tool version capture.

## BUILD-005. Process execution

- [ ] Use process runner abstraction.
- [ ] Enforce timeout.
- [ ] Capture stdout/stderr.
- [ ] Map exit code to structured error.

## BUILD-006. Artifact collection

- [ ] Glob expected artifacts.
- [ ] Calculate SHA-256.
- [ ] Upload artifacts and logs.
- [ ] Return artifact descriptors.

## BUILD-007. Tests

- [ ] Unit tests for validation.
- [ ] Integration test with tiny Maven project.
- [ ] Integration test with tiny GCC project.
- [ ] Negative test for missing artifact.

## BUILD-008. Docker and Kubernetes

- [ ] Dockerfile with required build tools.
- [ ] k8s deployment with CPU/memory/disk limits.
- [ ] Env vars for topics/storage/default timeout.

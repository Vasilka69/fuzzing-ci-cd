# TASKS: Internal Storage Service

## STOR-001. Создать service module

- [ ] Maven module.
- [ ] Spring Boot application.
- [ ] Common dependencies.

## STOR-002. Storage backend abstraction

- [ ] `StorageBackend` interface.
- [ ] Local filesystem implementation.
- [ ] Path traversal protection.
- [ ] SHA-256 utilities.

## STOR-003. Operations

- [ ] `save`.
- [ ] `copy`.
- [ ] `promote`.
- [ ] `cleanup`.
- [ ] retention metadata placeholder.

## STOR-004. Kafka executor integration

- [ ] Read `jobs.storage`.
- [ ] Publish running/completed/failed.
- [ ] Structured errors.

## STOR-005. Optional HTTP API

- [ ] Upload endpoint for service-to-service use.
- [ ] Download temporary URL endpoint or local equivalent.
- [ ] Health endpoint.

## STOR-006. Tests

- [ ] Unit tests for URI parsing.
- [ ] Unit tests for checksum.
- [ ] Integration tests for save/copy/promote/cleanup.

## STOR-007. Docker and Kubernetes

- [ ] Dockerfile.
- [ ] Persistent volume option for local backend.
- [ ] Env vars for base path/backend type.

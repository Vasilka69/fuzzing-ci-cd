# TASKS: Deployment Service

## DEPLOY-001. Создать service module

- [ ] Maven module.
- [ ] Spring Boot application.
- [ ] Common dependencies.

## DEPLOY-002. Params validation

- [ ] Validate deployment type.
- [ ] Validate artifact URI.
- [ ] Validate target and credentials ref.
- [ ] Validate commands/compose/systemd config.
- [ ] Validate healthcheck.

## DEPLOY-003. Release model

- [ ] Generate deterministic or traceable `release_id`.
- [ ] Include artifact checksum.
- [ ] Include target environment summary.
- [ ] Include command/config hash.

## DEPLOY-004. Deployment manifest

- [ ] Create manifest before execution.
- [ ] Update manifest after execution.
- [ ] Upload manifest to storage.

## DEPLOY-005. Dry-run/local mode

- [ ] Validate full flow without external host.
- [ ] Produce manifest/logs.

## DEPLOY-006. SSH Bash mode

- [ ] Resolve credentials securely.
- [ ] Copy artifact to temp location.
- [ ] Backup existing artifact if enabled.
- [ ] Execute commands.
- [ ] Redact secrets.

## DEPLOY-007. Healthcheck and rollback

- [ ] HTTP healthcheck.
- [ ] Command healthcheck.
- [ ] Rollback placeholder.
- [ ] Restore previous artifact where supported.

## DEPLOY-008. Tests

- [ ] Unit tests for release_id.
- [ ] Unit tests for manifest.
- [ ] Dry-run integration test.
- [ ] Negative test for failed healthcheck.

## DEPLOY-009. Docker and Kubernetes

- [ ] Dockerfile with SSH client if needed.
- [ ] k8s deployment with secret mounts/env refs.
- [ ] Resource limits and probes.

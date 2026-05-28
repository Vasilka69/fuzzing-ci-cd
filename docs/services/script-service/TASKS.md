# TASKS: Script Execution Service

## SCRIPT-001. Создать service module

- [ ] Maven module.
- [ ] Spring Boot application.
- [ ] Common dependencies.

## SCRIPT-002. Params validation

- [ ] Validate script type.
- [ ] Validate script body or script artifact URI.
- [ ] Validate input artifacts.
- [ ] Validate expected outputs.
- [ ] Validate environment variable names.

## SCRIPT-003. Sandbox/workspace

- [ ] Workspace per job_execution.
- [ ] No privileged mode by default.
- [ ] Network disabled by default where runtime supports it.
- [ ] CPU/memory/disk/time limits.

## SCRIPT-004. Runtime execution

- [ ] Bash runner.
- [ ] cmd runner placeholder or implementation.
- [ ] Process tree kill on timeout.
- [ ] stdout/stderr capture with max size.

## SCRIPT-005. Artifact handling

- [ ] Download input artifacts.
- [ ] Collect expected outputs.
- [ ] Upload outputs and logs.

## SCRIPT-006. Tests

- [ ] Happy path script.
- [ ] Non-zero exit code.
- [ ] Timeout.
- [ ] Output artifact collection.
- [ ] Secret redaction.

## SCRIPT-007. Docker and Kubernetes

- [ ] Dockerfile with minimal shell runtime.
- [ ] Non-root user.
- [ ] Restricted securityContext.
- [ ] Resource limits.

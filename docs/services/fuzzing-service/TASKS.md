# TASKS: Fuzzing Service

## FUZZ-001. Создать service module

- [ ] Maven module.
- [ ] Spring Boot application.
- [ ] Common dependencies.

## FUZZ-002. Создать fuzzing engine adapter

- [ ] `FuzzingEngine` interface.
- [ ] `FuzzingEngineRequest`.
- [ ] `FuzzingEngineResult`.
- [ ] Fake implementation for tests.
- [ ] Adapter to ready AFL++/LLM engine.

## FUZZ-003. Params validation

- [ ] Validate target artifact/source snapshot.
- [ ] Validate `target_command`.
- [ ] Validate budget and memory limit.
- [ ] Validate mode: `fake`, `real_llm`.
- [ ] Validate policy.

## FUZZ-004. Workspace preparation

- [ ] Download target artifact or source snapshot.
- [ ] Download seeds/dictionary/prompt if provided.
- [ ] Prepare AFL++ directories: input, output, crashes, hangs, queue.
- [ ] Enforce max artifact sizes.

## FUZZ-005. Engine execution

- [ ] Launch engine process or library adapter.
- [ ] Apply timeout.
- [ ] Capture stdout/stderr.
- [ ] Capture engine exit status.
- [ ] Preserve partial data on timeout/failure.

## FUZZ-006. Result collector

- [ ] Parse AFL++ stats.
- [ ] Collect crash cases.
- [ ] Collect hangs.
- [ ] Collect corpus if enabled.
- [ ] Collect LLM worker/mutator stats.
- [ ] Generate summary report JSON/Markdown.

## FUZZ-007. Policy mapping

- [ ] `fail_on_crash=true` maps crash to failed job.
- [ ] `fail_on_hang=true` maps hang to failed job.
- [ ] Engine startup errors map to infrastructure/user config errors.
- [ ] LLM endpoint errors apply retry/fallback policy.

## FUZZ-008. Tests

- [ ] Unit tests for policy mapping.
- [ ] Fake engine integration test with no crash.
- [ ] Fake engine integration test with crash.
- [ ] Timeout test.
- [ ] Artifact upload test.

## FUZZ-009. Docker and Kubernetes

- [ ] Dockerfile includes runtime requirements for ready fuzzing engine.
- [ ] Non-root user where possible.
- [ ] CPU/memory/ephemeral storage limits.
- [ ] Optional volume for corpus cache.
- [ ] Env vars for LLM endpoint refs and topics.

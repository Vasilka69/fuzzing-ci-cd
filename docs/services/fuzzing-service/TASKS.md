# TASKS: Fuzzing Service

## FUZZ-001. Создать service module

- [x] Maven module.
- [x] Spring Boot application.
- [x] Common dependencies.

## FUZZ-002. Создать fuzzing engine adapter

- [x] `FuzzingEngine` interface.
- [x] `FuzzingEngineRequest`.
- [x] `FuzzingEngineResult`.
- [x] Fake implementation for tests.
- [ ] Adapter to ready AFL++/LLM engine.

## FUZZ-003. Params validation

- [x] Validate target artifact/source snapshot.
- [x] Validate `target_command`.
- [x] Validate budget and memory limit.
- [x] Validate mode: `fake`, `real_llm`.
- [x] Validate policy.

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

- [x] `fail_on_crash=true` maps crash to failed job.
- [x] `fail_on_hang=true` maps hang to failed job.
- [x] Engine startup errors map to infrastructure/user config errors.
- [ ] LLM endpoint errors apply retry/fallback policy.

## FUZZ-008. Tests

- [x] Unit tests for policy mapping.
- [x] Fake engine integration test with no crash.
- [x] Fake engine integration test with crash.
- [x] Timeout test.
- [ ] Artifact upload test.

## FUZZ-009. Docker and Kubernetes

- [ ] Dockerfile includes runtime requirements for ready fuzzing engine.
- [ ] Non-root user where possible.
- [ ] CPU/memory/ephemeral storage limits.
- [ ] Optional volume for corpus cache.
- [ ] Env vars for LLM endpoint refs and topics.

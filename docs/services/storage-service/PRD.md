# PRD: Internal Storage Service

## Назначение

Storage service предоставляет единый API/исполнительный слой для сохранения, копирования, продвижения и очистки source snapshots, build artifacts, fuzzing reports, logs, deployment manifests и script outputs. Kafka передает только URI и metadata, не бинарные файлы.

## Scope

Входит:

- local filesystem backend for dev/test;
- S3/MinIO-compatible adapter as extension;
- operations: `save`, `copy`, `promote`, `cleanup`;
- checksum verification;
- artifact metadata response;
- retention policy placeholder;
- HTTP API if required by other services.

Не входит:

- полноценная UI download permission model;
- repository manager production integration beyond adapter interface.

## Kafka

- input topic: `jobs.storage`
- output topic: `jobs.results`

## Входные параметры

```json
{
  "operation": "promote",
  "source_uri": "storage://tmp/run/job/artifact.jar",
  "destination_policy": {
    "namespace": "artifacts",
    "name": "application.jar",
    "overwrite": false
  },
  "retention": "default",
  "expected_sha256": "..."
}
```

## Выходы

- `uri`
- `sha256`
- `size_bytes`
- `content_type`
- `metadata`

## Acceptance criteria

- Service validates operation and URI.
- Save/copy/promote/cleanup work on local backend.
- Checksum mismatch returns non-retryable validation error.
- Large payload is never sent through Kafka.
- Artifacts are addressed by stable `storage://` URI.

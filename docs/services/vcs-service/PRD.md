# PRD: VCS Integration Service

## Назначение

VCS service получает исходный код из внешней системы контроля версий, фиксирует точный commit/revision и публикует source snapshot во внутреннее хранилище. Build service и другие executor'ы должны работать со snapshot, а не напрямую с внешним repository.

## Scope

Входит:

- Git checkout по branch/tag/commit;
- опционально Mercurial adapter как extension point;
- shallow checkout;
- submodules flag;
- snapshot archive creation;
- checksum calculation;
- upload snapshot/logs to storage;
- structured result event.

Не входит:

- UI для настройки repository;
- хранение credentials;
- webhook trigger processing master-service.

## Kafka

- input topic: `jobs.vcs`
- output topic: `jobs.results`
- dead-letter: `jobs.dead-letter`

## Входные параметры

```json
{
  "vcs_type": "git",
  "repository_url": "https://example.local/project/repo.git",
  "ref": "main",
  "ref_type": "branch",
  "checkout_depth": 1,
  "submodules": false,
  "credentials_ref": "secret://vcs/project-token",
  "snapshot_policy": {
    "include_git_dir": false,
    "max_size_mb": 512
  }
}
```

## Выходы

- `source_snapshot_uri`
- `commit_hash` / `revision_id`
- `vcs_type`
- `ref`
- `checksum`
- `size_bytes`
- `logs_uri`

## Ошибки

| Код | Тип | Retry |
| --- | --- | --- |
| `VCS_REPOSITORY_UNAVAILABLE` | `infrastructure_error` | yes |
| `VCS_AUTH_FAILED` | `validation_error` or `security_error` | no |
| `VCS_REF_NOT_FOUND` | `validation_error` | no |
| `VCS_SNAPSHOT_TOO_LARGE` | `validation_error` | no |
| `VCS_TIMEOUT` | `timeout` | configurable |
| `VCS_STORAGE_UPLOAD_FAILED` | `infrastructure_error` | yes |

## Acceptance criteria

- Service reads `jobs.vcs`.
- Service validates required params.
- Git checkout works for branch and commit.
- Credentials are masked in logs.
- Snapshot is uploaded to storage and has SHA-256.
- Duplicate `job_execution_id` does not create conflicting snapshot names.
- Unit tests cover validation and URL redaction.
- Integration test covers checkout from local test repository.

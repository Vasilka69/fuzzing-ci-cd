# PRD: Deployment Service

## Назначение

Deployment service доставляет build artifacts на целевые среды и выполняет deployment сценарии: SSH/Bash/cmd, file copy, Docker, Docker Compose, systemd. Сервис должен быть идемпотентным через `release_id` и deployment manifest.

## Scope

Входит:

- artifact download;
- release_id generation;
- deployment manifest;
- dry-run/local mode;
- SSH Bash mode;
- Docker/Docker Compose mode as extension;
- healthcheck;
- rollback policy placeholder/restore previous artifact where supported.

Не входит:

- UI управления окружениями;
- production-grade secrets vault;
- arbitrary privileged host operations without explicit config.

## Входные параметры

```json
{
  "deployment_type": "ssh_bash",
  "artifact_uri": "storage://artifacts/application.jar",
  "target": {
    "host": "10.0.0.10",
    "port": 22,
    "user": "deploy",
    "credentials_ref": "secret://deploy/server-key"
  },
  "copy": {
    "destination_path": "/opt/app/application.jar",
    "backup_existing": true
  },
  "commands": [
    "sudo systemctl stop app.service",
    "sudo cp /tmp/application.jar /opt/app/application.jar",
    "sudo systemctl start app.service"
  ],
  "healthcheck": {
    "type": "http",
    "url": "http://10.0.0.10:8080/actuator/health",
    "timeout_seconds": 60
  },
  "rollback": {
    "enabled": true,
    "strategy": "restore_previous_artifact"
  }
}
```

## Выходы

- `release_id`
- `deployment_manifest_uri`
- `deployment_type`
- `target_summary`
- `artifact_checksum`
- `healthcheck_result`
- `rollback_status`
- `logs_uri`

## Acceptance criteria

- Duplicate release can be detected and treated idempotently.
- Manifest is always saved.
- Secrets are never logged.
- Partial deployment failure produces structured error and rollback attempt if configured.
- Dry-run mode works without real target host.

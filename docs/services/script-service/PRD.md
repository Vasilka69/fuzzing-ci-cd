# PRD: Script Execution Service

## Назначение

Script service выполняет пользовательские Bash/cmd сценарии для нестандартных этапов pipeline. Это наиболее опасный executor, поэтому он должен запускать код в ограниченном workspace/container и по умолчанию с минимальными правами.

## Scope

Входит:

- Bash script execution;
- cmd mode placeholder or implementation depending on runtime;
- input artifacts download;
- environment preparation;
- network disabled by default policy flag;
- stdout/stderr capture;
- expected outputs collection;
- resource/time limits;
- logs/artifacts upload.

Не входит:

- build logic, которая должна быть в build service;
- deployment side effects, которые должны быть в deploy service;
- privileged host access by default.

## Входные параметры

```json
{
  "script_type": "bash",
  "script": "echo Building report && ./scripts/check.sh",
  "working_directory": ".",
  "input_artifacts": ["storage://artifacts/application.jar"],
  "environment": {
    "APP_ENV": "test"
  },
  "allowed_network": false,
  "expected_outputs": ["reports/*.xml"]
}
```

## Выходы

- `exit_code`
- `runtime`
- `logs_uri`
- `output_artifacts[]`
- `duration_ms`
- `timeout_exceeded`

## Acceptance criteria

- Bash script executes in isolated workspace.
- Timeout kills script and child processes.
- Expected outputs are uploaded.
- Network/privilege defaults are safe.
- Secrets are masked.

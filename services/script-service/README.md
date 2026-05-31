# script-service

Executor-сервис для `script` job из topic `jobs.script`.

## MVP scope

- Поддерживается `templatePath=script/bash`.
- Сценарий задается ровно одним параметром: inline `script` или `script_artifact_uri`.
- `input_artifacts[]` скачиваются из `storage://` в workspace по относительным путям.
- `expected_outputs[]` ищутся как относительные glob-паттерны внутри `working_directory` и публикуются как `script_output` artifacts.
- stdout/stderr проходят через общий `ProcessRunner` chunking/limit; текст публикуется отдельным `JOB_LOG`, а `JOB_FINISHED.logs` остается `null`.
- Если `sandbox_policy.networkPolicy` не задан, effective policy фиксируется как `none`.

## Пример params

```json
{
  "script_type": "bash",
  "script": "set -euo pipefail\nmkdir -p out\ncp inputs/input.txt out/result.txt",
  "input_artifacts": [
    {"uri": "storage://script-inputs/job-1/input.txt", "path": "inputs/input.txt"}
  ],
  "environment": {"MODE": "demo"},
  "working_directory": ".",
  "expected_outputs": ["out/*.txt"]
}
```

## Local run

Минимальная проверка сервиса:

```bash
./mvnw -pl services/script-service -am test
./mvnw -pl services/script-service -am verify
```

Локальный `StorageClient` использует root:

```text
${java.io.tmpdir}/fuzzing-ci-cd/storage
```

## Ограничения

- `script/cmd` не реализован в MVP; это отдельная задача `SCRIPT-101`.
- Сервис не запускает вложенный Docker/Pod на каждую job. Sandbox задается securityContext executor container/pod и общим `SandboxPolicyValidator`.
- Network isolation в local JVM-режиме не может физически отключить сеть процесса; в Kubernetes/Docker окружении для MVP ожидается `networkPolicy=none` и ограниченный pod/container sandbox.
- Секреты не передаются значениями в params; stdout/stderr перед публикацией редактируются общим `SecretRedactor`.

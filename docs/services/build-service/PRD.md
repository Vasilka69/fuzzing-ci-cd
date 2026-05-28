# PRD: Build Service

## Назначение

Build service получает source snapshot, запускает выбранный build tool с контролируемым entrypoint и пользовательскими аргументами, собирает artifacts/logs и публикует structured result.

## Поддерживаемые tools

- Maven
- Gradle
- Javac
- GCC

## Главный security принцип

Команда сборки представляется как `tool` + `args`, а не как shell string. Произвольная shell-логика должна выполняться в script service, не в build service.

## Входные параметры

```json
{
  "build_tool": "maven",
  "source_snapshot_uri": "storage://sources/project/source.tar.gz",
  "working_directory": ".",
  "toolchain": {
    "jdk_version": "17",
    "maven_version": "3.9"
  },
  "args": ["clean", "package", "-DskipTests=false"],
  "expected_artifacts": ["target/*.jar"]
}
```

## Выходы

- `build_tool`
- `tool_version`
- `args`
- `exit_code`
- `build_artifacts[]`
- `logs_uri`
- `duration_ms`

## Ошибки

| Код | Тип | Retry |
| --- | --- | --- |
| `BUILD_SOURCE_UNAVAILABLE` | `infrastructure_error` | yes |
| `BUILD_UNSUPPORTED_TOOL` | `validation_error` | no |
| `BUILD_FILE_NOT_FOUND` | `user_code_error` | no |
| `BUILD_EXIT_CODE_NON_ZERO` | `user_code_error` | no |
| `BUILD_EXPECTED_ARTIFACT_NOT_FOUND` | `user_code_error` | no |
| `BUILD_TIMEOUT` | `timeout` | no by default |

## Acceptance criteria

- Maven/Gradle/Javac/GCC can be invoked with args array.
- Source snapshot is downloaded and unpacked into isolated workspace.
- Expected artifacts are globbed, checksummed and uploaded.
- Non-zero exit code maps to user code error.
- No shell injection via args.

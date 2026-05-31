# Fuzzing-service

`fuzzing-service` обрабатывает job template `fuzzing/afl-llm`: готовит AFL++/LLM kernel,
запускает `afl-fuzz` с бюджетом времени и публикует один `fuzzing-report.tar.gz` artifact.
Большие crash/hang/corpus payload не попадают в Kafka events.

## Real LLM mode

`mode=real` использует тот же IPC worker `llm_mutator_server.py`, что и fake mode, но включает
OpenAI-compatible HTTP endpoint формата `chat/completions` через `LLM_API_URL`.

Минимальные `params` job:

```json
{
  "mode": "real",
  "budget_seconds": 30,
  "local_grammar": "dsl",
  "llm_api_url": "http://127.0.0.1:11434/v1/chat/completions",
  "llm_model": "local-code-model",
  "llm_api_timeout_seconds": 20
}
```

`llm_api_url` можно не указывать в job, если executor запущен с
`CICD_FUZZING_LLM_API_URL`. Для endpoint-а без bearer token этого достаточно.

Bearer token нельзя передавать через `params`, `inputs`, Kafka payload или artifact metadata.
Если endpoint требует ключ, задайте его только через доверенную runtime-инъекцию секрета:

```bash
CICD_FUZZING_LLM_API_KEY=...
```

Сервис передает ключ только в окружение дочернего worker process как `LLM_API_KEY` и не добавляет
его в `JOB_FINISHED.additionalData`, `JOB_LOG` или artifact metadata.

## Полезные переменные окружения

| Переменная | Назначение |
| --- | --- |
| `CICD_FUZZING_KERNEL_ROOT` | Путь к `fuzzing-engine/afl-llm-engine`. |
| `CICD_FUZZING_AFLPP_ROOT` | Путь к AFL++ checkout с `afl-fuzz`. |
| `CICD_FUZZING_START_FAKE_WORKER` | Управляет запуском локального IPC worker-а для fake/real mode. |
| `CICD_FUZZING_LLM_API_URL` | Default OpenAI-compatible endpoint для `mode=real`. |
| `CICD_FUZZING_LLM_API_KEY` | Bearer token для real endpoint; не задавайте в job params. |
| `CICD_FUZZING_LLM_MODEL` | Default model name, если `llm_model` не задан в job. |

## Проверка

```bash
./mvnw -pl services/fuzzing-service -am test
./mvnw -pl services/fuzzing-service -am verify
```

Unit/contract tests не делают реальный HTTP-вызов к LLM endpoint. Они проверяют routing,
переменные окружения worker-а и отсутствие секретов в событиях/log payload.

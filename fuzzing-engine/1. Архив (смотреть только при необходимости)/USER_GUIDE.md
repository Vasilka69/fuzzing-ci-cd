# USER GUIDE: запуск и проверка проекта

Дата актуализации: 2026-05-06

Этот гайд описывает, как с нуля собрать и проверить проект `LLM + AFL++`, а затем подключить локальную LLM через OpenAI-compatible endpoint.

Активная директория проекта:

```bash
cd /opt/diplom/fuzzing-ci-cd/afl-llm-engine
```

## 1. Что запускаем

Проект демонстрирует гибридный fuzzing pipeline:

- AFL++ запускает target, собирает coverage, управляет corpus и triage crash cases.
- Custom mutator получает готовые структурные inputs через локальный IPC.
- Python worker заранее генерирует candidates.
- В fake mode worker работает без внешней LLM.
- В real mode worker ходит в OpenAI-compatible `chat/completions` endpoint.
- Новые интересные inputs из AFL++ возвращаются worker как feedback.

Рекомендуемый порядок проверки:

1. Собрать AFL++.
2. Собрать проект.
3. Проверить target без AFL++.
4. Проверить IPC без AFL++.
5. Запустить полный fake pipeline без LLM.
6. Поставить локальную LLM.
7. Запустить real LLM pipeline.

## 2. Сборка AFL++

Перейдите в vendored AFL++ checkout:

```bash
cd /opt/diplom/fuzzing-ci-cd/AFLplusplus
make source-only
```

Если LLVM не подхватывается автоматически, укажите `llvm-config` явно, например:

```bash
make LLVM_CONFIG=llvm-config-18 source-only
```

После сборки должны существовать исполняемые файлы:

```bash
ls -l afl-fuzz afl-clang-fast
```

## 3. Сборка проекта

```bash
cd /opt/diplom/fuzzing-ci-cd/afl-llm-engine
make all
```

Ожидаемые build artifacts:

```bash
ls -l build/target_dsl build/afl_llm_mutator.so
```

Назначение файлов:

- `build/target_dsl` - AFL-instrumented DSL target.
- `build/afl_llm_mutator.so` - AFL++ custom mutator library.

## 4. Smoke-test target без AFL++

Сначала соберите host-версию target:

```bash
make smoke
```

Затем проверьте контрольный crash-path:

```bash
printf 'MODE DEBUG\nSET A 1337\nSET B 109\nSET C 16705\nAPPEND open\nCHECK MAGIC\nCHECK PLEASE\nCHECK FIZZ\nCHECK OPEN\nLOOP 7\nCRASH NOW\n' | ./build/target_dsl_cc
```

Ожидаемый результат: процесс аварийно завершается через `SIGABRT`. Обычно shell показывает `Aborted`. Для этого теста это корректное поведение: команда специально попадает в скрытый crash-path DSL target.

## 5. IPC smoke без AFL++

Эта проверка запускает worker в fake mode, делает `G`-запрос кандидата и отправляет `A`-feedback sample без `afl-fuzz`:

```bash
make ipc-smoke
```

Ожидаемый результат похож на:

```text
ipc smoke ok: received ... bytes; persisted feedback in /tmp/afl-llm-engine-ipc-smoke-.../discovered
```

Это означает, что worker, IPC protocol и feedback persistence работают.

## 6. Полный запуск без LLM: fake mode

Fake mode не требует API keys, интернета или локальной модели. Worker сам генерирует синтаксически похожие DSL-программы.

```bash
cd /opt/diplom/fuzzing-ci-cd/afl-llm-engine
AFL_I_DONT_CARE_ABOUT_MISSING_CRASHES=1 AFL_SKIP_CPUFREQ=1 AFL_NO_UI=1 timeout 8s ./scripts/run_fake.sh
```

Что проверить после запуска:

```bash
ls output/fake/default
sed -n '1,80p' output/fake/default/fuzzer_stats
find runtime/discovered -type f | head
find output/fake/default/crashes -type f
```

Полезные признаки:

- `output/fake/default/fuzzer_stats` существует - AFL++ успел запуститься.
- `runtime/discovered/` содержит файлы - feedback из AFL++ дошел до worker.
- `output/fake/default/crashes/` может содержать crash cases, если фаззер успел найти crash-path.

## 7. Fallback-проверка без worker

Эта команда проверяет, что custom mutator не ломает AFL++, даже если worker недоступен. В таком случае mutator использует локальную fallback-мутацию.

```bash
cd /opt/diplom/fuzzing-ci-cd/afl-llm-engine
AFL_I_DONT_CARE_ABOUT_MISSING_CRASHES=1 AFL_SKIP_CPUFREQ=1 AFL_NO_UI=1 \
AFL_CUSTOM_MUTATOR_LIBRARY="$PWD/build/afl_llm_mutator.so" \
AFL_CUSTOM_MUTATOR_ONLY=1 \
timeout 4s ../AFLplusplus/afl-fuzz -i targets/dsl/seeds -o output/no_worker -x targets/dsl/dsl.dict -- build/target_dsl
```

После запуска:

```bash
sed -n '1,80p' output/no_worker/default/fuzzer_stats
```

## 8. Какую локальную LLM поставить

Для этого проекта хороший стартовый выбор - **Ollama + `qwen3:8b`**.

Почему:

- Ollama просто ставится и удобно работает на Linux.
- Ollama предоставляет OpenAI-compatible endpoint `/v1/chat/completions`.
- Проект уже ожидает именно OpenAI-compatible `chat/completions`.
- `qwen3:8b` достаточно сильная для генерации коротких структурированных DSL-inputs и при этом не слишком тяжелая.

Варианты по железу:

- Слабая машина или CPU-only: `qwen3:4b`.
- Нормальный ноутбук/desktop с запасом RAM или GPU: `qwen3:8b`.
- Хороший GPU и хочется качества выше: `qwen3:14b`.

Альтернатива: **LM Studio**. Оно удобно, если нужен GUI для выбора и запуска моделей, но для shell-based fuzzing проще начать с Ollama.

## 9. Установка Ollama

```bash
curl -fsSL https://ollama.com/install.sh | sh
```

Запустите сервис, если он не стартовал автоматически:

```bash
sudo systemctl start ollama
```

Проверьте:

```bash
ollama -v
```

Скачайте модель:

```bash
ollama pull qwen3:8b
```

Быстрый тест OpenAI-compatible endpoint:

```bash
curl http://127.0.0.1:11434/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen3:8b","messages":[{"role":"user","content":"Return exactly: OK"}],"max_tokens":250}'
```

В ответе должен быть JSON с `choices[0].message.content`.

## 10. Запуск real LLM pipeline через Ollama

```bash
cd /opt/diplom/fuzzing-ci-cd/afl-llm-engine

export LLM_API_URL="http://127.0.0.1:11434/v1/chat/completions"
export LLM_MODEL="qwen3:8b"
unset LLM_API_KEY
export LLM_MUTATOR_LOG_CANDIDATES_DIR="$PWD/runtime/generated"
export NO_PROXY=127.0.0.1,localhost
export no_proxy=127.0.0.1,localhost

AFL_I_DONT_CARE_ABOUT_MISSING_CRASHES=1 AFL_SKIP_CPUFREQ=1 AFL_NO_UI=1 timeout 30s ./scripts/run_real_llm.sh
```

Результаты real run:

```bash
ls output/real/default
sed -n '1,100p' output/real/default/fuzzer_stats
find runtime/discovered -type f | head
find output/real/default/crashes -type f
```

Сырые candidates, которые worker получил от LLM и положил в очередь для AFL++, будут здесь:

```bash
find runtime/generated -type f | sort | tail -20

for f in $(find runtime/generated -type f | sort | tail -5); do
  echo "===== $f"
  sed -n '1,80p' "$f"
done
```

Если используется другая модель, поменяйте только `LLM_MODEL`, например:

```bash
export LLM_MODEL="qwen3:4b"
```

## 11. Полезные переменные окружения

| Переменная | Назначение | Типичное значение |
| --- | --- | --- |
| `AFLPP_DIR` | Путь к AFL++ checkout. | `../AFLplusplus` |
| `AFL_OUTPUT_DIR` | Директория AFL++ output. | `output/fake` или `output/real` |
| `AFL_SEEDS_DIR` | Стартовый corpus. | `./targets/dsl/seeds` |
| `AFL_CUSTOM_MUTATOR_ONLY` | Использовать только custom mutator. | `1` |
| `LLM_MUTATOR_ADDR` | Адрес worker IPC. | `tcp://127.0.0.1:15333` |
| `LLM_MUTATOR_PROMPT_FILE` | Prompt для генерации DSL inputs. | `./targets/dsl/prompt.txt` |
| `LLM_MUTATOR_SEED_DIR` | Seed examples для worker. | `./targets/dsl/seeds` |
| `LLM_MUTATOR_DISCOVERED_DIR` | Feedback samples от AFL++. | `./runtime/discovered` |
| `LLM_MUTATOR_LOG_CANDIDATES_DIR` | Если задана, raw candidates от fake/real generator сохраняются на диск. | `./runtime/generated` |
| `LLM_API_URL` | OpenAI-compatible chat completions endpoint. | `http://127.0.0.1:11434/v1/chat/completions` |
| `LLM_MODEL` | Имя модели. | `qwen3:8b` |
| `LLM_API_KEY` | Bearer token, если endpoint требует ключ. | не нужен для Ollama |
| `LLM_API_TIMEOUT` | Timeout HTTP-запроса, секунд. | `20` |

## 12. Быстрая диагностика

Если `make all` пишет, что `afl-clang-fast` не найден:

```bash
cd /opt/diplom/fuzzing-ci-cd/AFLplusplus
make source-only
```

Если `scripts/run_real_llm.sh` не может достучаться до локального endpoint:

```bash
curl http://127.0.0.1:11434/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen3:8b","messages":[{"role":"user","content":"ping"}],"max_tokens":10}'
```

Если локальный endpoint работает через браузер/curl, но worker не подключается, задайте proxy bypass:

```bash
export NO_PROXY=127.0.0.1,localhost
export no_proxy=127.0.0.1,localhost
```

Если порт worker занят, можно выбрать другой:

```bash
export LLM_MUTATOR_ADDR="tcp://127.0.0.1:15334"
```

Если AFL++ ругается на системные настройки во время короткой проверки, используйте:

```bash
export AFL_I_DONT_CARE_ABOUT_MISSING_CRASHES=1
export AFL_SKIP_CPUFREQ=1
export AFL_NO_UI=1
```

## 13. Минимальный acceptance checklist

Перед тем как считать локальный запуск успешным:

- [ ] `make all` проходит в `afl-llm-engine/`.
- [ ] `make smoke` проходит.
- [ ] Контрольный crash-path через `target_dsl_cc` завершает процесс через `SIGABRT`.
- [ ] `make ipc-smoke` получает candidate и сохраняет feedback.
- [ ] Короткий `scripts/run_fake.sh` создает `output/fake/default/fuzzer_stats`.
- [ ] После fake или real run появляются feedback samples в `runtime/discovered/`.
- [ ] Для real mode локальный endpoint отвечает на `/v1/chat/completions`.
- [ ] `scripts/run_real_llm.sh` создает `output/real/default/fuzzer_stats`.

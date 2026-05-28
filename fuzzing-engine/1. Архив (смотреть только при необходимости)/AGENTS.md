# AGENTS: рабочие инструкции по проекту

Дата актуализации: 2026-05-06

Этот файл предназначен для будущих агентов/ассистентов, работающих в репозитории. Основной продукт описан в `PRD.md`, текущий backlog - в `TASKS.md`.

## Контекст

Проект демонстрирует гибридный fuzzing pipeline:

- AFL++ выполняет coverage-guided fuzzing, corpus management и crash triage.
- Custom mutator получает готовые структурные inputs через локальный IPC.
- Python worker генерирует candidates заранее в fake mode или через OpenAI-compatible `chat/completions`.
- Feedback из AFL++ queue возвращается worker через `afl_custom_queue_new_entry`.

Активная директория проекта: `afl-llm-engine/`.

Vendored AFL++ dependency: `AFLplusplus/`.

## Важные Пути

| Путь | Как обращаться |
| --- | --- |
| `PRD.md` | Product requirements и acceptance criteria. |
| `TASKS.md` | Backlog, статусы, следующие шаги. |
| `afl-llm-engine/README.md` | Пользовательская документация проекта. |
| `afl-llm-engine/Makefile` | Сборка target, mutator и smoke-проверок. |
| `afl-llm-engine/src/mutator/afl_llm_mutator.c` | Основной C-код custom mutator. |
| `afl-llm-engine/src/worker/llm_mutator_server.py` | Python worker, IPC, fake/real LLM. |
| `afl-llm-engine/tests/ipc_smoke.py` | Smoke-проверка IPC `G`/`A` без AFL++. |
| `afl-llm-engine/targets/dsl/target_dsl.c` | DSL target. |
| `afl-llm-engine/targets/dsl/prompt.txt` | Prompt для DSL generation. |
| `afl-llm-engine/targets/dsl/seeds/` | Стартовый corpus. |
| `afl-llm-engine/targets/dsl/dsl.dict` | AFL++ dictionary. |
| `afl-llm-engine/scripts/run_fake.sh` | Полный fake pipeline. |
| `afl-llm-engine/scripts/run_real_llm.sh` | Real LLM pipeline. |
| `afl-llm-engine/build/` | Generated binaries, не редактировать вручную. |
| `afl-llm-engine/output/` | AFL++ output, не коммитить. |
| `afl-llm-engine/runtime/` | Runtime socket/discovered feedback, не коммитить. |
| `AFLplusplus/` | Vendored AFL++; не менять без явной задачи. |

## Правила Работы

- Сначала читать `PRD.md`, затем `TASKS.md`, затем локальные файлы в `afl-llm-engine/`.
- Не менять `AFLplusplus/`, если задача явно не требует патчить AFL++.
- Не коммитить и не документировать реальные API keys.
- Не добавлять secrets в scripts, README, output artifacts или prompt.
- Generated dirs `build/`, `output/`, `runtime/discovered/` должны оставаться disposable.
- При изменении путей и прочего состояния проекта (новые переменные окружение, новая функциональность, смена контрактов и т.д.) обязательно обновлять документацию: `PRD.md`, `README.md`, `TASKS.md`, `USER_GUIDE.md`, этот файл (`AGENTS.md`), скрипты запуска и т.д., то есть следить за актуальностью документации и целостности проекта.
- Для поиска использовать `rg`/`rg --files`.
- Для ручных правок использовать patch-based edits.

## Команды

Сборка проекта:

```bash
cd afl-llm-engine
make all
```

Smoke-сборка:

```bash
cd afl-llm-engine
make smoke
```

Проверка IPC `G`/`A` без AFL++:

```bash
cd afl-llm-engine
make ipc-smoke
```

Проверка crash-path без AFL++:

```bash
cd afl-llm-engine
printf 'MODE DEBUG\nSET A 1337\nSET B 109\nSET C 16705\nAPPEND open\nCHECK MAGIC\nCHECK PLEASE\nCHECK FIZZ\nCHECK OPEN\nLOOP 7\nCRASH NOW\n' | ./build/target_dsl_cc
```

Ожидаемый результат: процесс завершается через `SIGABRT`. Это контрольный crash-path DSL target.

Короткий fake pipeline:

```bash
cd afl-llm-engine
AFL_I_DONT_CARE_ABOUT_MISSING_CRASHES=1 AFL_SKIP_CPUFREQ=1 AFL_NO_UI=1 timeout 8s ./scripts/run_fake.sh
```

Fallback без worker:

```bash
cd afl-llm-engine
AFL_I_DONT_CARE_ABOUT_MISSING_CRASHES=1 AFL_SKIP_CPUFREQ=1 AFL_NO_UI=1 \
AFL_CUSTOM_MUTATOR_LIBRARY="$PWD/build/afl_llm_mutator.so" \
AFL_CUSTOM_MUTATOR_ONLY=1 \
timeout 4s ../AFLplusplus/afl-fuzz -i targets/dsl/seeds -o output/no_worker -x targets/dsl/dsl.dict -- build/target_dsl
```

Real LLM mode:

```bash
cd afl-llm-engine
export LLM_API_URL="https://example.local/v1/chat/completions"
export LLM_MODEL="model-name"
export LLM_API_KEY="set-only-if-needed"
./scripts/run_real_llm.sh
```

Для локальных OpenAI-compatible endpoints может понадобиться:

```bash
export NO_PROXY=127.0.0.1,localhost
export no_proxy=127.0.0.1,localhost
```

## Acceptance Checklist Для Агентов

Перед тем как считать изменение готовым:

- [ ] `make all` проходит в `afl-llm-engine/`.
- [ ] Если затронут target или mutator, `make smoke` проходит.
- [ ] Если затронут IPC или worker, проверены операции `G` и `A`.
- [ ] Если затронуты run scripts, проверен короткий `scripts/run_fake.sh`.
- [ ] Если затронут real LLM mode, проверен хотя бы локальный OpenAI-compatible mock или явно описана причина, почему проверка невозможна.
- [ ] Документация обновлена вместе с изменениями поведения или путей.
- [ ] `git status --short` просмотрен; unrelated user changes не тронуты.

## Известные Точки Внимания

- P0 из `TASKS.md` закрыт; ближайшие остатки - метрики, сравнение baseline vs LLM-mutator и feedback-loop hygiene.
- Worker может логировать пустой `producer error` при заполненной очереди; это P1 в `TASKS.md`.
- Hit/miss counters в mutator уже есть в структуре состояния, но не экспортируются как полноценная метрика.
- Сравнительного runner для baseline AFL++ vs LLM-mutator пока нет.
- `Прочее/` содержит неактивные материалы, старые migrations, html/json и временные логи; это не активная часть fuzzing MVP.

# AGENTS: рабочие инструкции по проекту

Дата актуализации: 2026-05-03

Этот файл предназначен для будущих агентов/ассистентов, работающих в репозитории. Основной продукт описан в `PRD.md`, текущий backlog - в `TASKS.md`.

## Контекст

Проект демонстрирует гибридный fuzzing pipeline:

- AFL++ выполняет coverage-guided fuzzing, corpus management и crash triage.
- Custom mutator получает готовые структурные inputs через локальный IPC.
- Python worker генерирует candidates заранее в fake mode или через OpenAI-compatible `chat/completions`.
- Feedback из AFL++ queue возвращается worker через `afl_custom_queue_new_entry`.

Активная demo-директория: `main-project/llm_aflpp_demo/`.

Vendored AFL++ dependency: `main-project/AFLplusplus/`.

## Важные Пути

| Путь | Как обращаться |
| --- | --- |
| `PRD.md` | Product requirements и acceptance criteria. |
| `TASKS.md` | Backlog, статусы, следующие шаги. |
| `main-project/llm_aflpp_demo/README.md` | Пользовательская документация demo. |
| `main-project/llm_aflpp_demo/Makefile` | Сборка demo. |
| `main-project/llm_aflpp_demo/afl_llm_mutator.c` | Основной C-код custom mutator. |
| `main-project/llm_aflpp_demo/llm_mutator_server.py` | Python worker, IPC, fake/real LLM. |
| `main-project/llm_aflpp_demo/ipc_smoke.py` | Smoke-проверка IPC `G`/`A` без AFL++. |
| `main-project/llm_aflpp_demo/target_dsl.c` | Demo target. |
| `main-project/llm_aflpp_demo/prompt.txt` | Prompt для DSL generation. |
| `main-project/llm_aflpp_demo/seeds/` | Стартовый corpus. |
| `main-project/llm_aflpp_demo/demo.dict` | AFL++ dictionary. |
| `main-project/llm_aflpp_demo/run_fake.sh` | Полный fake pipeline. |
| `main-project/llm_aflpp_demo/run_real_llm.sh` | Real LLM pipeline. |
| `main-project/llm_aflpp_demo/build/` | Generated binaries, не редактировать вручную. |
| `main-project/llm_aflpp_demo/output/` | AFL++ output, не коммитить. |
| `main-project/llm_aflpp_demo/runtime/` | Runtime socket/discovered feedback, не коммитить. |
| `main-project/AFLplusplus/` | Vendored AFL++; не менять без явной задачи. |

## Правила Работы

- Сначала читать `PRD.md`, затем `TASKS.md`, затем локальные файлы в `main-project/llm_aflpp_demo/`.
- Не менять `main-project/AFLplusplus/`, если задача явно не требует патчить AFL++.
- Не коммитить и не документировать реальные API keys.
- Не добавлять secrets в scripts, README, output artifacts или prompt.
- Generated dirs `build/`, `output/`, `runtime/discovered/` должны оставаться disposable.
- При изменении путей и прочего состояния проекта (новые переменные окружение, новая функциональность, смена контрактов и т.д.) обязательно обновлять документацию: `PRD.md`, `README.md`, `TASKS.md`, `USER_GUIDE.md`, этот файл (`AGENTS.md`), скрипты запуска и т.д., то есть следить за актуальностью документации и целостности проекта.
- Для поиска использовать `rg`/`rg --files`.
- Для ручных правок использовать patch-based edits.

## Команды

Сборка demo:

```bash
cd main-project/llm_aflpp_demo
make all
```

Smoke-сборка:

```bash
cd main-project/llm_aflpp_demo
make smoke
```

Проверка IPC `G`/`A` без AFL++:

```bash
cd main-project/llm_aflpp_demo
make ipc-smoke
```

Проверка crash-path без AFL++:

```bash
cd main-project/llm_aflpp_demo
printf 'MODE DEBUG\nSET A 1337\nSET B 109\nSET C 16705\nAPPEND open\nCHECK MAGIC\nCHECK PLEASE\nCHECK FIZZ\nCHECK OPEN\nLOOP 7\nCRASH NOW\n' | ./build/target_dsl_cc
```

Ожидаемый результат: процесс завершается через `SIGABRT`. Это контрольный crash-path demo target.

Короткий fake pipeline:

```bash
cd main-project/llm_aflpp_demo
AFL_I_DONT_CARE_ABOUT_MISSING_CRASHES=1 AFL_SKIP_CPUFREQ=1 AFL_NO_UI=1 timeout 8s ./run_fake.sh
```

Fallback без worker:

```bash
cd main-project/llm_aflpp_demo
AFL_I_DONT_CARE_ABOUT_MISSING_CRASHES=1 AFL_SKIP_CPUFREQ=1 AFL_NO_UI=1 \
AFL_CUSTOM_MUTATOR_LIBRARY="$PWD/build/afl_llm_mutator.so" \
AFL_CUSTOM_MUTATOR_ONLY=1 \
timeout 4s ../AFLplusplus/afl-fuzz -i seeds -o output/no_worker -x demo.dict -- build/target_dsl
```

Real LLM mode:

```bash
cd main-project/llm_aflpp_demo
export LLM_API_URL="https://example.local/v1/chat/completions"
export LLM_MODEL="model-name"
export LLM_API_KEY="set-only-if-needed"
./run_real_llm.sh
```

Для локальных OpenAI-compatible endpoints может понадобиться:

```bash
export NO_PROXY=127.0.0.1,localhost
export no_proxy=127.0.0.1,localhost
```

## Acceptance Checklist Для Агентов

Перед тем как считать изменение готовым:

- [ ] `make all` проходит в `main-project/llm_aflpp_demo/`.
- [ ] Если затронут target или mutator, `make smoke` проходит.
- [ ] Если затронут IPC или worker, проверены операции `G` и `A`.
- [ ] Если затронуты run scripts, проверен короткий `run_fake.sh`.
- [ ] Если затронут real LLM mode, проверен хотя бы локальный OpenAI-compatible mock или явно описана причина, почему проверка невозможна.
- [ ] Документация обновлена вместе с изменениями поведения или путей.
- [ ] `git status --short` просмотрен; unrelated user changes не тронуты.

## Известные Точки Внимания

- P0 из `TASKS.md` закрыт; ближайшие остатки - метрики, сравнение baseline vs LLM-mutator и feedback-loop hygiene.
- Worker может логировать пустой `producer error` при заполненной очереди; это P1 в `TASKS.md`.
- Hit/miss counters в mutator уже есть в структуре состояния, но не экспортируются как полноценная метрика.
- Сравнительного runner для baseline AFL++ vs LLM-mutator пока нет.
- Root-level `*.html`, `*.json`, `migrations/` и `main-project/Материалы/` не являются активной частью fuzzing MVP.

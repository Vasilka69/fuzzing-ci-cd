# LLM + AFL++

Минимальный проект для идеи "LLM как асинхронный генератор кандидатов для AFL++ custom mutator".

Что показывает проект:

- `afl-fuzz` работает через `custom mutator`, но сам мутатор не ждёт сеть и не делает синхронных запросов к LLM.
- Отдельный Python-воркер заранее держит очередь кандидатов за локальным IPC.
- Если очередь пуста, мутатор делает дешёвую локальную мутацию и не тормозит AFL++.
- Когда AFL++ находит новый интересный input, мутатор может отправить его назад воркеру как feedback seed.

## Структура

```text
llm-aflpp/
├── Makefile
├── README.md
├── src/
│   ├── mutator/
│   │   └── afl_llm_mutator.c
│   └── worker/
│       └── llm_mutator_server.py
├── targets/
│   └── dsl/
│       ├── target_dsl.c
│       ├── prompt.txt
│       ├── dsl.dict
│       └── seeds/
├── tests/
│   └── ipc_smoke.py
├── scripts/
│   ├── run_fake.sh
│   ├── run_real_llm.sh
│   └── prepare_env.sh
├── build/
├── output/
└── runtime/
```

`build/`, `output/` и `runtime/` являются disposable/generated директориями и игнорируются git.

## Идея архитектуры

1. `src/worker/llm_mutator_server.py` крутится отдельно и заранее генерирует валидные DSL-программы.
2. `src/mutator/afl_llm_mutator.c` внутри `afl-fuzz` быстро делает `G`-запрос в локальный IPC endpoint.
3. Если готовый кандидат есть, мутатор смешивает его с текущим seed.
4. Если кандидата нет, используется локальная byte-level fallback-мутация.
5. `targets/dsl/target_dsl.c` - простой stdin/persistent-friendly target с мини-грамматикой и скрытым crash-path.

## Требования

- Linux
- `python3`
- собранный `AFL++` в соседней директории `../AFLplusplus`
- `clang`/`gcc`

## 1. Сборка AFL++

Если `AFLplusplus/afl-fuzz` и `AFLplusplus/afl-clang-fast` ещё не собраны:

```bash
cd ../AFLplusplus
make source-only
```

Если LLVM у вас не подхватывается автоматически:

```bash
cd ../AFLplusplus
make LLVM_CONFIG=llvm-config-18 source-only
```

Для полной установки зависимостей посмотрите [../AFLplusplus/docs/INSTALL.md](../AFLplusplus/docs/INSTALL.md).

## 2. Сборка проекта

```bash
cd llm-aflpp
make all
```

Что соберётся:

- `build/target_dsl` - target, скомпилированный через `afl-clang-fast`
- `build/afl_llm_mutator.so` - shared library для `AFL_CUSTOM_MUTATOR_LIBRARY`

Ожидаемый результат: обе сборочные цели появляются в `build/`, а команда завершается без compiler warnings в коде проекта.

## 3. Быстрый smoke-test без AFL++

```bash
cd llm-aflpp
make smoke
printf 'MODE DEBUG\nSET A 1337\nSET B 109\nSET C 16705\nAPPEND open\nCHECK MAGIC\nCHECK PLEASE\nCHECK FIZZ\nCHECK OPEN\nLOOP 7\nCRASH NOW\n' | ./build/target_dsl_cc
```

Эта команда должна завершиться аварийно: это контрольный crash-path для DSL target.
Обычно shell печатает `Aborted` или возвращает код завершения от `SIGABRT`.

## 4. IPC smoke без AFL++

Проверка запускает worker в fake-режиме, делает `G`-запрос кандидата и отправляет `A`-feedback sample без участия `afl-fuzz`:

```bash
cd llm-aflpp
make ipc-smoke
```

Ожидаемый результат похож на:

```text
ipc smoke ok: received 74 bytes; persisted feedback in /tmp/llm-aflpp-ipc-smoke-.../discovered
```

## 5. Запуск в fake-режиме

Этот режим не требует внешнего LLM API. Воркер сам генерирует синтаксически похожие DSL-программы.

```bash
cd llm-aflpp
./scripts/run_fake.sh
```

По умолчанию:

- адрес воркера: `tcp://127.0.0.1:15333`
- входные seeds: `./targets/dsl/seeds`
- выход AFL++: `./output/fake`
- словарь: `./targets/dsl/dsl.dict`
- feedback samples: `./runtime/discovered`

Остановка: `Ctrl+C`.

## 6. Запуск с реальной LLM

Воркер использует OpenAI-compatible endpoint формата `chat/completions`.

Пример:

```bash
cd llm-aflpp
export LLM_API_URL="https://api.openai.com/v1/chat/completions"
export LLM_API_KEY="..."
export LLM_MODEL="gpt-4.1-mini"
./scripts/run_real_llm.sh
```

Для локального OpenAI-compatible сервера можно не задавать `LLM_API_KEY`, если ему не нужен bearer token.
Если локальный endpoint слушает `127.0.0.1` или `localhost`, иногда нужно явно обойти proxy:

```bash
export NO_PROXY=127.0.0.1,localhost
export no_proxy=127.0.0.1,localhost
```

## Полезные переменные окружения

| Переменная | Назначение | Значение по умолчанию |
| --- | --- | --- |
| `AFLPP_DIR` | Путь к локальному AFL++ checkout. | `../AFLplusplus` |
| `AFL_OUTPUT_DIR` | AFL++ output directory для `-o`. | `output/fake` или `output/real` |
| `AFL_SEEDS_DIR` | AFL++ input corpus для `-i`. | `./targets/dsl/seeds` |
| `AFL_CUSTOM_MUTATOR_ONLY` | `1`, чтобы AFL++ использовал только custom mutator. | `1` в run scripts |
| `LLM_MUTATOR_ADDR` | Worker address: `tcp://host:port`, Unix socket path или abstract Unix socket `@name`. | `tcp://127.0.0.1:15333` |
| `LLM_MUTATOR_SOCK` | Legacy-алиас для `LLM_MUTATOR_ADDR`. | не задан |
| `LLM_MUTATOR_PROMPT_FILE` | Prompt для worker. | `./targets/dsl/prompt.txt` |
| `LLM_MUTATOR_SEED_DIR` | Директория seed examples для worker. | `./targets/dsl/seeds` |
| `LLM_MUTATOR_DISCOVERED_DIR` | Куда worker сохраняет feedback samples от AFL++. | `./runtime/discovered` |
| `LLM_MUTATOR_LOG_CANDIDATES_DIR` | Если задана, worker сохраняет каждый raw generated candidate перед выдачей AFL++. | не задан |
| `LLM_MUTATOR_QUEUE_SIZE` | Размер очереди готовых кандидатов. | `128` |
| `LLM_MUTATOR_WORKERS` | Число producer-потоков worker. | `2` |
| `LLM_MUTATOR_MAX_SAMPLE_SIZE` | Максимальный размер seed/feedback sample. | `65535` |
| `LLM_MUTATOR_MAX_CANDIDATE_CHARS` | Максимальный размер текстового LLM-кандидата до отправки AFL++. | `2048` |
| `LLM_API_URL` | OpenAI-compatible `chat/completions` endpoint; включает real LLM mode. | не задан |
| `LLM_API_KEY` | Bearer token для real LLM endpoint, если нужен. | не задан |
| `LLM_MODEL` | Имя модели для real LLM endpoint. | `gpt-4.1-mini` |
| `LLM_API_TIMEOUT` | Timeout HTTP-запроса к real LLM endpoint, секунд. | `20` |
| `PYTHON` | Python interpreter для run scripts и Makefile. | `python3` |

## Как адаптировать под реальную цель

1. Добавить новый target или заменить `targets/dsl/target_dsl.c` на реальный harness/target.
2. Переписать prompt в `targets/dsl/prompt.txt` или завести отдельную директорию под новый формат.
3. Заменить seeds в `targets/dsl/seeds/` на реальные валидные примеры.
4. Если у LLM внутреннее представление отличается от входа target, использовать `afl_custom_post_process()`.
5. Если нужен более сильный feedback loop, расширить протокол между мутатором и воркером.

## Почему этот дизайн удобен

- не ломает hot path AFL++;
- позволяет быстро тестировать идею без сети;
- легко заменить DSL target на реальный формат;
- поддерживает feedback от новых queue entries обратно в LLM-воркер.

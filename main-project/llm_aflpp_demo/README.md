# LLM + AFL++ demo

Минимальный демонстрационный проект для идеи "LLM как асинхронный генератор кандидатов для AFL++ custom mutator".

Что показывает демо:

- `afl-fuzz` работает через `custom mutator`, но сам мутатор не ждёт сеть и не делает синхронных запросов к LLM.
- Отдельный Python-воркер заранее держит очередь кандидатов в Unix socket.
- Если очередь пуста, мутатор делает дешёвую локальную мутацию и не тормозит AFL++.
- Когда AFL++ находит новый интересный input, мутатор может отправить его назад воркеру как feedback seed.

## Структура

```text
llm_aflpp_demo/
├── Makefile
├── README.md
├── afl_llm_mutator.c
├── llm_mutator_server.py
├── target_dsl.c
├── prompt.txt
├── demo.dict
├── run_fake.sh
├── run_real_llm.sh
├── seeds/
│   ├── seed01.txt
│   ├── seed02.txt
│   └── seed03.txt
└── runtime/
```

## Идея архитектуры

1. `llm_mutator_server.py` крутится отдельно и заранее генерирует валидные DSL-программы.
2. `afl_llm_mutator.c` внутри `afl-fuzz` только быстро делает `GET` в локальный Unix socket.
3. Если готовый кандидат есть, мутатор смешивает его с текущим seed.
4. Если кандидата нет, используется локальная byte-level fallback-мутация.
5. `target_dsl.c` — простой stdin/persistent-friendly таргет с мини-грамматикой и скрытым crash-path.

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

## 2. Сборка демо

```bash
cd llm_aflpp_demo
make all
```

Что соберётся:

- `build/target_dsl` — таргет, скомпилированный через `afl-clang-fast`
- `build/afl_llm_mutator.so` — shared library для `AFL_CUSTOM_MUTATOR_LIBRARY`

## 3. Быстрый smoke-test без AFL++

Если хотите сначала просто проверить локальную логику таргета:

```bash
cd llm_aflpp_demo
make smoke
printf 'MODE DEBUG\nSET A 1337\nSET B 109\nSET C 16705\nAPPEND open\nCHECK MAGIC\nCHECK PLEASE\nCHECK FIZZ\nCHECK OPEN\nLOOP 7\nCRASH NOW\n' | ./build/target_dsl_cc
```

Эта команда должна завершиться аварийно: это контрольный crash-path для демо.

## 4. Запуск в fake-режиме

Этот режим не требует внешнего LLM API. Воркер сам генерирует синтаксически похожие DSL-программы.

```bash
cd llm_aflpp_demo
./run_fake.sh
```

По умолчанию:

- адрес воркера: `tcp://127.0.0.1:15333`
- входные seeds: `./seeds`
- выход AFL++: `./output/fake`
- словарь: `./demo.dict`

Остановка: `Ctrl+C`.

## 5. Запуск с реальной LLM

Воркер использует OpenAI-compatible endpoint формата `chat/completions`.

Пример:

```bash
cd llm_aflpp_demo
export LLM_API_URL="https://api.openai.com/v1/chat/completions"
export LLM_API_KEY="..."
export LLM_MODEL="gpt-4.1-mini"
./run_real_llm.sh
```

Для локального OpenAI-compatible сервера можно не задавать `LLM_API_KEY`, если ему не нужен bearer token.

## Полезные переменные окружения

- `AFLPP_DIR` — путь к локальному AFL++ (`../AFLplusplus` по умолчанию)
- `LLM_MUTATOR_ADDR` — адрес воркера, например `tcp://127.0.0.1:15333`
- `LLM_MUTATOR_SOCK` — legacy-алиас для Unix socket / abstract socket
- `LLM_MUTATOR_PROMPT_FILE` — prompt для воркера
- `LLM_MUTATOR_SEED_DIR` — директория с примерами
- `LLM_MUTATOR_QUEUE_SIZE` — размер очереди кандидатов
- `LLM_MUTATOR_WORKERS` — число producer-потоков
- `AFL_CUSTOM_MUTATOR_ONLY` — `1`, чтобы AFL++ использовал только custom mutator
- `AFL_OUTPUT_DIR` — путь для `-o`

## Как адаптировать под реальную цель

1. Заменить `target_dsl.c` на ваш реальный harness/target.
2. Переписать `prompt.txt` под настоящий формат входа.
3. Заменить seeds в `./seeds` на реальные валидные примеры.
4. Если у LLM внутреннее представление отличается от входа таргета, использовать `afl_custom_post_process()`.
5. Если нужен более сильный feedback loop, расширить протокол между мутатором и воркером.

## Почему этот дизайн удобен

- не ломает hot path AFL++;
- позволяет быстро тестировать идею без сети;
- легко заменить demo DSL на реальный формат;
- поддерживает feedback от новых queue entries обратно в LLM-воркер.

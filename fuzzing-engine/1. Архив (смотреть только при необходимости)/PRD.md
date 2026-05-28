# PRD: LLM-мутатор для AFL++ fuzzing

Дата актуализации: 2026-05-06

## 1. Краткое описание

Проект реализует демонстрационный фаззер на базе AFL++, где LLM используется как структурно-семантический мутатор входных данных. AFL++ остается основным движком покрытия, исполнения, scheduler/corpus management и crash triage, а LLM генерирует более валидные и осмысленные кандидаты для структурированных форматов.

Главная архитектурная идея: LLM-вызовы вынесены из hot path AFL++. `afl_custom_fuzz()` не делает HTTP-запросы и не ждет внешнюю модель; он получает уже готовые inputs через локальный IPC от отдельного Python worker.

Активный прототип находится здесь:

- `afl-llm-engine/` - проект LLM + AFL++;
- `AFLplusplus/` - vendored AFL++ checkout, используемый проектом;
- `PRD.md` - этот документ;
- `TASKS.md` - backlog и готовность проекта;
- `AGENTS.md` - рабочие инструкции для будущих агентов/ассистентов.

## 2. Фактическая структура проекта

Активные runtime/build пути:

| Путь | Назначение |
| --- | --- |
| `afl-llm-engine/README.md` | Команды сборки, запуска и адаптации проекта. |
| `afl-llm-engine/Makefile` | Сборка target, mutator и smoke target. |
| `afl-llm-engine/src/mutator/afl_llm_mutator.c` | AFL++ custom mutator shared library source. |
| `afl-llm-engine/src/worker/llm_mutator_server.py` | Асинхронный fake/real LLM worker и IPC server. |
| `afl-llm-engine/targets/dsl/target_dsl.c` | DSL target: stdin, line-based DSL, persistent-friendly loop, hidden crash-path. |
| `afl-llm-engine/tests/ipc_smoke.py` | Локальная smoke-проверка IPC `G`/`A` без запуска AFL++. |
| `afl-llm-engine/targets/dsl/prompt.txt` | Prompt с описанием DSL-формата. |
| `afl-llm-engine/targets/dsl/dsl.dict` | AFL++ dictionary для DSL tokens. |
| `afl-llm-engine/targets/dsl/seeds/` | Стартовый corpus: `seed01.txt`, `seed02.txt`, `seed03.txt`. |
| `afl-llm-engine/scripts/run_fake.sh` | Полный запуск AFL++ + worker без внешнего LLM API. |
| `afl-llm-engine/scripts/run_real_llm.sh` | Полный запуск AFL++ + worker через OpenAI-compatible endpoint. |
| `afl-llm-engine/build/` | Generated binaries: `target_dsl`, `target_dsl_cc`, `afl_llm_mutator.so`. Игнорируется git. |
| `afl-llm-engine/output/` | AFL++ output directories, например `output/fake/default/`. Игнорируется git. |
| `afl-llm-engine/runtime/discovered/` | Feedback samples, полученные через `afl_custom_queue_new_entry`. Игнорируется git. |
| `AFLplusplus/afl-fuzz` | AFL++ fuzzer binary после сборки AFL++. |
| `AFLplusplus/afl-clang-fast` | AFL++ compiler wrapper, используемый `Makefile`. |

Справочные и неактивные для MVP пути:

- `Прочее/Материалы/` - исходные материалы, экспорт чатов и заметки; не используются в runtime.
- `Прочее/migrations/`, `Прочее/Мусор/`, `Прочее/Мусор/temp/` - старые миграции, html/json и временные логи; не входят в текущий fuzzing MVP.
- Большая часть `AFLplusplus/` является vendored upstream-кодом AFL++; менять ее нужно только при отдельной задаче.

## 3. Проблема

Классический coverage-guided fuzzing хорошо исследует низкоуровневые изменения, но на структурированных форматах быстро упирается в проблему валидности: случайные мутации портят синтаксис, не проходят парсер и долго не доходят до логики, где чаще находятся сложные ошибки.

LLM может использовать знания о формате, seed examples и обратную связь от AFL++, чтобы создавать inputs, которые:

- чаще остаются синтаксически валидными;
- комбинируют команды и значения семантически осмысленно;
- быстрее достигают глубоких веток target-программы;
- дополняют, а не заменяют стандартные AFL++ стратегии.

При этом LLM-вызовы медленные и непредсказуемые по latency, поэтому LLM не должна вызываться синхронно внутри `afl_custom_fuzz()`.

## 4. Цели

1. Реализовать рабочий фаззер, где AFL++ использует LLM-generated inputs через custom mutator.
2. Сохранить производительность AFL++: `afl_custom_fuzz()` не должен ждать сетевые LLM-запросы.
3. Поддержать два режима генерации:
   - fake/local режим без внешнего API;
   - real LLM режим через OpenAI-compatible `chat/completions` endpoint.
4. Передавать новые интересные inputs из AFL++ обратно в LLM worker как feedback seeds.
5. Дать воспроизводимый сценарий, где можно сравнить обычный AFL++ и AFL++ с LLM-мутатором.
6. Подготовить архитектуру, которую можно адаптировать с toy DSL на реальные targets и форматы.

## 5. Не цели

- Не создавать новый fuzzing engine с нуля.
- Не заменять AFL++ scheduler, corpus management, coverage tracking и crash triage.
- Не выполнять LLM-запросы синхронно внутри `afl_custom_fuzz()`.
- Не гарантировать, что LLM всегда находит crash быстрее обычного AFL++.
- Не хранить API-ключи в репозитории.
- Не строить web-панель на первом этапе.
- Не модифицировать vendored AFL++ без отдельной необходимости.

## 6. Пользователи

Основной пользователь - исследователь или студент, который показывает и исследует гибридный подход: coverage-guided fuzzing + LLM-assisted structured mutation.

Дополнительные пользователи:

- разработчик security tooling, которому нужен расширяемый прототип;
- инженер, сравнивающий mutation strategies на структурированных форматах;
- преподаватель или рецензент, которому нужна понятная демонстрация архитектуры и экспериментов.

## 7. Основной сценарий

1. Пользователь переходит в `AFLplusplus/` и собирает AFL++ при необходимости.
2. Пользователь переходит в `afl-llm-engine/`.
3. Пользователь запускает `make all`.
4. Пользователь запускает `./scripts/run_fake.sh` для проверки без внешнего LLM API.
5. AFL++ стартует с `AFL_CUSTOM_MUTATOR_LIBRARY=afl-llm-engine/build/afl_llm_mutator.so`.
6. Отдельный Python worker `afl-llm-engine/src/worker/llm_mutator_server.py` наполняет очередь готовыми кандидатами.
7. Custom mutator быстро запрашивает кандидата у worker через локальный IPC.
8. Если кандидат есть, mutator смешивает его с текущим seed и отдает AFL++.
9. Если кандидата нет, mutator делает дешевую локальную fallback-мутацию.
10. Когда AFL++ находит новый интересный queue entry, mutator отправляет его worker.
11. Worker сохраняет feedback sample в `afl-llm-engine/runtime/discovered/` и использует corpus в следующих prompts.
12. Пользователь анализирует coverage, crashes, queue и статистику mutator hit/miss.

## 8. Функциональные требования

### 8.1 AFL++ custom mutator

Файл: `afl-llm-engine/src/mutator/afl_llm_mutator.c`

Build artifact: `afl-llm-engine/build/afl_llm_mutator.so`

Требования:

- Должен собираться как shared library `afl_llm_mutator.so`.
- Должен подключаться через `AFL_CUSTOM_MUTATOR_LIBRARY`.
- Должен реализовать минимум:
  - `afl_custom_init`;
  - `afl_custom_fuzz_count`;
  - `afl_custom_fuzz`;
  - `afl_custom_describe`;
  - `afl_custom_post_process`;
  - `afl_custom_queue_new_entry`;
  - `afl_custom_deinit`.
- Должен поддерживать `AFL_CUSTOM_MUTATOR_ONLY=1`.
- Должен работать без worker: при недоступном IPC использовать локальную fallback-мутацию.
- Должен ограничивать размер кандидатов по `max_size` и внутреннему буферу.
- Не должен делать сетевые HTTP-запросы к LLM.
- Должен передавать новые AFL++ queue entries worker через `A` opcode.

### 8.2 LLM worker

Файл: `afl-llm-engine/src/worker/llm_mutator_server.py`

Требования:

- Должен запускаться отдельным процессом.
- Должен поддерживать очередь кандидатов фиксированного размера.
- Должен иметь producer-потоки, которые заранее генерируют новые inputs.
- Должен поддерживать fake-режим без внешнего API.
- Должен поддерживать real LLM режим при заданном `LLM_API_URL`.
- Должен работать с OpenAI-compatible endpoint формата `chat/completions`.
- Должен читать prompt из `LLM_MUTATOR_PROMPT_FILE`, по умолчанию `afl-llm-engine/targets/dsl/prompt.txt`.
- Должен перечитывать prompt при изменении файла.
- Должен загружать стартовые seeds из `LLM_MUTATOR_SEED_DIR`, по умолчанию `afl-llm-engine/targets/dsl/seeds/`.
- Должен сохранять feedback samples в `LLM_MUTATOR_DISCOVERED_DIR`, по умолчанию `afl-llm-engine/runtime/discovered/`.
- Должен опционально сохранять raw generated candidates в `LLM_MUTATOR_LOG_CANDIDATES_DIR`, если переменная задана.

### 8.3 IPC-протокол

MVP-протокол:

- `G` - запрос готового кандидата от mutator к worker;
- ответ `uint32 big-endian length`;
- `length == 0` означает cache miss;
- при `length > 0` далее передается payload;
- `A` + `uint32 big-endian length` + payload - отправка нового интересного input от AFL++ в worker.

Транспорт:

- по умолчанию TCP loopback `tcp://127.0.0.1:15333`;
- поддерживается Unix socket path через `LLM_MUTATOR_ADDR`/`LLM_MUTATOR_SOCK`;
- поддерживается abstract Unix socket через адрес с префиксом `@`.

### 8.4 Prompting

Файл prompt по умолчанию: `afl-llm-engine/targets/dsl/prompt.txt`

Требования:

- Prompt должен описывать допустимый формат входа target-программы.
- Prompt должен требовать plain text без markdown, пояснений и code fences.
- В prompt должны добавляться seed examples из стартового и feedback corpus.
- В real LLM режиме должны задаваться:
  - `LLM_API_URL`;
  - `LLM_API_KEY` при необходимости;
  - `LLM_MODEL`;
  - `LLM_API_TIMEOUT`;
  - лимиты размера результата.

### 8.5 DSL target

Файл: `afl-llm-engine/targets/dsl/target_dsl.c`

Build artifacts:

- `afl-llm-engine/build/target_dsl` - AFL-instrumented target;
- `afl-llm-engine/build/target_dsl_cc` - host target для smoke-test.

Требования:

- stdin-ввод;
- persistent-friendly цикл через `__AFL_LOOP`;
- простая line-based грамматика;
- скрытый crash-path, требующий осмысленной комбинации команд, регистров, проверок и значений;
- возможность локального smoke-test без AFL++.

### 8.6 CLI и запуск

Проект должен иметь:

- `afl-llm-engine/Makefile` для сборки target и mutator;
- `afl-llm-engine/tests/ipc_smoke.py` или `make ipc-smoke` для проверки IPC `G`/`A` без AFL++;
- `afl-llm-engine/scripts/run_fake.sh` для запуска без внешнего API;
- `afl-llm-engine/scripts/run_real_llm.sh` для запуска с реальным LLM endpoint;
- `afl-llm-engine/README.md` с командами сборки, запуска и переменными окружения.

Ключевые переменные окружения:

| Переменная | Назначение | Значение по умолчанию |
| --- | --- | --- |
| `AFLPP_DIR` | Путь к AFL++ checkout. | `afl-llm-engine/../AFLplusplus` |
| `AFL_OUTPUT_DIR` | AFL++ `-o` directory. | `output/fake` или `output/real` |
| `AFL_SEEDS_DIR` | AFL++ `-i` corpus directory. | `afl-llm-engine/targets/dsl/seeds` |
| `AFL_CUSTOM_MUTATOR_ONLY` | Использовать только custom mutator. | `1` в run scripts |
| `LLM_MUTATOR_ADDR` | Worker address. | `tcp://127.0.0.1:15333` |
| `LLM_MUTATOR_SOCK` | Legacy alias для socket address. | Не задан |
| `LLM_MUTATOR_PROMPT_FILE` | Prompt file. | `afl-llm-engine/targets/dsl/prompt.txt` |
| `LLM_MUTATOR_SEED_DIR` | Seed examples для worker. | `afl-llm-engine/targets/dsl/seeds` |
| `LLM_MUTATOR_DISCOVERED_DIR` | Feedback samples directory. | `afl-llm-engine/runtime/discovered` |
| `LLM_MUTATOR_LOG_CANDIDATES_DIR` | Optional raw generated candidates log directory. | Не задан |
| `LLM_MUTATOR_QUEUE_SIZE` | Размер очереди кандидатов. | `128` |
| `LLM_MUTATOR_WORKERS` | Число producer threads. | `2` |
| `LLM_MUTATOR_MAX_SAMPLE_SIZE` | Максимальный размер feedback/generated sample в bytes. | `65535` |
| `LLM_MUTATOR_MAX_CANDIDATE_CHARS` | Максимальный размер LLM text result в chars. | `2048` |
| `LLM_API_URL` | OpenAI-compatible `chat/completions` endpoint. | Не задан, значит fake mode |
| `LLM_API_KEY` | Bearer token для endpoint. | Не задан |
| `LLM_MODEL` | Model name для real mode. | `gpt-4.1-mini` |
| `LLM_API_TIMEOUT` | Timeout LLM HTTP request в секундах. | `20` |

## 9. Нефункциональные требования

### Производительность

- `afl_custom_fuzz()` должен завершаться быстро и не зависеть от latency LLM API.
- При пустой очереди fallback-мутация должна быть дешевой.
- Reconnect к worker должен быть ограничен по частоте.
- Размеры payload должны ограничиваться, чтобы не раздувать память и время исполнения target.
- Generated artifacts должны оставаться в `afl-llm-engine/build/`, `output/`, `runtime/` и не попадать в git.

### Надежность

- Отсутствие LLM API, падение worker или пустая очередь не должны останавливать AFL++.
- Ошибки LLM endpoint должны логироваться worker и приводить к retry/backoff.
- Некорректный ответ LLM должен обрезаться и безопасно кодироваться в bytes.
- Feedback corpus должен сохраняться на диск.
- Worker должен корректно освобождать socket path при обычном Unix socket запуске.

### Безопасность

- API-ключи передаются только через переменные окружения.
- В репозитории не должно быть secrets.
- Worker должен слушать локальный адрес по умолчанию.
- Размер feedback и generated samples должен быть ограничен.
- Любые реальные LLM endpoints и ключи не должны коммититься в `README.md`, scripts или output artifacts.

### Воспроизводимость

- Должен быть fake-режим без сети.
- Должен быть smoke-test для target crash-path.
- Стартовые seeds, prompt и dictionary должны храниться в репозитории.
- Эксперименты должны сохранять AFL++ output в отдельные директории под `afl-llm-engine/output/`.

## 10. Метрики успеха

Минимальные метрики:

- `make all` в `afl-llm-engine/` собирает target и mutator;
- `make smoke` собирает host target и mutator;
- `./scripts/run_fake.sh` запускает AFL++ и worker без внешнего API;
- custom mutator работает при доступном и недоступном worker;
- feedback samples появляются в `afl-llm-engine/runtime/discovered/`;
- скрытый crash-path достижим на DSL target;
- `./scripts/run_real_llm.sh` работает при заданном OpenAI-compatible `LLM_API_URL`.

Сравнительные метрики:

- time-to-first-crash для обычного AFL++ и AFL++ с LLM-мутатором;
- количество найденных unique paths за фиксированное время;
- количество queue entries за фиксированное время;
- доля syntactically valid inputs;
- hit/miss ratio очереди LLM-кандидатов;
- overhead custom mutator относительно baseline AFL++.

## 11. MVP

MVP считается готовым, если:

1. AFL++ в `AFLplusplus/` собран и может запускать `afl-llm-engine/build/target_dsl`.
2. `afl-llm-engine/build/afl_llm_mutator.so` подключается через `AFL_CUSTOM_MUTATOR_LIBRARY`.
3. Python worker генерирует кандидаты в fake-режиме.
4. Mutator получает кандидаты через IPC и использует fallback при miss или недоступном worker.
5. `afl-llm-engine/scripts/run_fake.sh` запускает полный pipeline одной командой.
6. `afl-llm-engine/scripts/run_real_llm.sh` запускает тот же pipeline с OpenAI-compatible endpoint.
7. Новый AFL++ queue entry отправляется в worker через `afl_custom_queue_new_entry`.
8. `afl-llm-engine/README.md` объясняет сборку, запуск и адаптацию под другой target.

Текущий статус по результатам проверки: MVP в целом реализован; оставшаяся работа относится в основном к метрикам, сравнительным экспериментам, чистоте логирования и документационной доводке.

## 12. Архитектура

```text
afl-llm-engine/
  targets/dsl/prompt.txt
  targets/dsl/seeds/
  runtime/discovered/
          |
          v
  +--------------------------+
  | src/worker/              |
  | llm_mutator_server.py    |
  | fake / real LLM worker   |
  | TCP / Unix socket IPC    |
  +------------+-------------+
               ^
  G: candidate | A: feedback sample
               v
  +--------------------------+
  | src/mutator/             |
  | afl_llm_mutator.c        |
  | build/afl_llm_mutator.so |
  +------------+-------------+
               |
               v
  +--------------------------+
  | AFLplusplus |
  | afl-fuzz coverage engine |
  +------------+-------------+
               |
               v
  +--------------------------+
  | targets/dsl/target_dsl.c |
  | build/target_dsl         |
  | stdin + persistent loop  |
  +--------------------------+
```

Ключевое архитектурное решение: LLM вынесена в отдельный асинхронный worker. AFL++ hot path получает только готовые inputs из локальной очереди.

## 13. План реализации

### Этап 1. Стабилизация проекта

- Проверить сборку `afl-llm-engine`.
- Проверить fake-запуск через `afl-llm-engine/scripts/run_fake.sh`.
- Проверить smoke-test crash-path.
- Зафиксировать README-команды и expected behavior.

### Этап 2. Метрики и логирование

- Добавить экспорт hit/miss/requests для custom mutator.
- Добавить статистику worker queue size, produced samples, served samples, feedback samples и LLM errors.
- Добавить script для сравнения baseline AFL++ vs LLM-mutator AFL++.
- Сохранять результаты сравнений в отдельные директории под `afl-llm-engine/output/`.

### Этап 3. Feedback loop

- Улучшить обработку `afl_custom_queue_new_entry`.
- Исключать дубликаты feedback samples.
- Ограничить размер и количество persisted samples.
- Добавлять наиболее свежие или интересные samples в prompt.

### Этап 4. Адаптация к реальному target

- Добавить новый target/harness рядом с `afl-llm-engine/targets/dsl/target_dsl.c` или вынести в отдельный target subdir.
- Переписать `afl-llm-engine/targets/dsl/prompt.txt` под реальный формат.
- Подготовить валидные стартовые seeds.
- При необходимости реализовать `afl_custom_post_process()` для трансляции LLM-представления в wire-format.

### Этап 5. Улучшение стратегии мутации

- Добавить grammar-aware fallback mutator.
- Разделить LLM-генерации на стратегии: repair, extend, combine, edge-values.
- Добавить rate limiting для LLM.
- Рассмотреть shared-memory ring buffer или mmap вместо socket при необходимости.

## 14. Риски

- LLM может генерировать слишком однотипные inputs.
- LLM может часто нарушать формат без строгого prompt и post-processing.
- Внешний API добавляет latency, стоимость и нестабильность.
- Слишком частое использование LLM-кандидатов может снизить execs/sec.
- `AFL_CUSTOM_MUTATOR_ONLY=1` может убрать полезные стандартные стадии AFL++.
- DSL target может быть слишком простым и не показать преимуществ на реальных форматах.
- Реальная среда может иметь HTTP proxy, который мешает локальному mock endpoint; для localhost может потребоваться `NO_PROXY=127.0.0.1,localhost`.

## 15. Открытые вопросы

- Какой реальный target будет использоваться после DSL target?
- Нужен ли формат внутреннего представления, например JSON/AST, с последующим `post_process()`?
- Какие модели будут тестироваться: локальные или облачные?
- Нужно ли учитывать стоимость LLM-запросов как отдельную метрику?
- Какой режим сравнения считать основным: fixed time, fixed executions или fixed budget?
- Нужно ли хранить crash-inducing inputs отдельно от обычных feedback samples?
- Считается ли текущий путь `afl-llm-engine/` финальным package-layout?

## 16. Acceptance criteria

- Команда `make all` в `afl-llm-engine/` успешно собирает target и mutator.
- Команда `make smoke` в `afl-llm-engine/` успешно собирает локальный target для проверки.
- `afl-llm-engine/scripts/run_fake.sh` запускает AFL++ pipeline без LLM API.
- `afl-llm-engine/scripts/run_real_llm.sh` запускает AFL++ pipeline при заданном `LLM_API_URL`.
- AFL++ не блокируется при недоступном worker.
- Worker принимает `G` и `A` операции по IPC.
- Новые интересные inputs сохраняются в `afl-llm-engine/runtime/discovered/`.
- `afl-llm-engine/README.md` и `PRD.md` описывают, как перенести подход с DSL target на реальный target.
- `TASKS.md` отражает оставшиеся задачи по метрикам, feedback loop и адаптации.
- `AGENTS.md` описывает рабочие правила, команды и границы редактирования для будущих агентов.

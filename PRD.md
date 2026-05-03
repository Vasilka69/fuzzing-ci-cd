# PRD: LLM-мутатор для AFL++ fuzzing

## 1. Краткое описание

Проект должен реализовать фаззер на базе AFL++, в котором LLM используется как структурно-семантический мутатор входных данных. Главная идея: AFL++ остается основным движком покрытия, исполнения и triage, а LLM помогает генерировать более валидные и осмысленные кандидаты для форматов, где обычные байтовые мутации часто ломают синтаксис и долго не доходят до глубоких веток программы.

Базовый прототип находится в `Фаззинг с llm/1/llm_aflpp_demo`. Он показывает минимальную архитектуру:

- `afl_llm_mutator.c` - custom mutator для AFL++;
- `llm_mutator_server.py` - отдельный воркер, который заранее генерирует кандидаты через fake-генератор или OpenAI-compatible LLM endpoint;
- `target_dsl.c` - демонстрационный target с line-based DSL, persistent-friendly stdin-вводом и скрытым crash-path;
- `prompt.txt`, `demo.dict`, `seeds/` - описание формата, словарь и стартовый корпус.

## 2. Проблема

Классический coverage-guided fuzzing хорошо исследует низкоуровневые изменения, но на структурированных форматах быстро упирается в проблему валидности: случайные мутации портят синтаксис, не проходят парсер и не доходят до логики, где чаще находятся сложные ошибки.

LLM может использовать знания о формате, примеры seed-ов и обратную связь от AFL++, чтобы создавать входы, которые:

- чаще остаются синтаксически валидными;
- комбинируют команды и значения семантически осмысленно;
- быстрее достигают глубоких веток target-программы;
- дополняют, а не заменяют стандартные AFL++ стратегии.

При этом LLM-вызовы медленные и непредсказуемые по latency. Поэтому LLM не должна вызываться напрямую из hot path AFL++.

## 3. Цели

1. Реализовать рабочий фаззер, где AFL++ использует LLM-generated inputs через custom mutator.
2. Сохранить производительность AFL++: `afl_custom_fuzz()` не должен ждать сетевые LLM-запросы.
3. Поддержать два режима генерации:
   - fake/local режим для отладки без внешнего API;
   - real LLM режим через OpenAI-compatible `chat/completions` endpoint.
4. Передавать новые интересные inputs из AFL++ обратно в LLM-воркер как feedback seeds.
5. Дать воспроизводимый demo-сценарий, где можно сравнить обычный AFL++ и AFL++ с LLM-мутатором.
6. Подготовить архитектуру, которую можно адаптировать с toy DSL на реальные targets и форматы.

## 4. Не цели

- Не создавать новый fuzzing engine с нуля.
- Не заменять AFL++ scheduler, corpus management, coverage tracking и crash triage.
- Не выполнять LLM-запросы синхронно внутри `afl_custom_fuzz()`.
- Не гарантировать, что LLM всегда находит crash быстрее AFL++.
- Не хранить API-ключи в репозитории.
- Не строить полноценную web-панель на первом этапе.

## 5. Пользователи

Основной пользователь - исследователь или студент, который хочет показать и исследовать гибридный подход: coverage-guided fuzzing + LLM-assisted structured mutation.

Дополнительные пользователи:

- разработчик security tooling, которому нужен расширяемый прототип;
- инженер, сравнивающий mutation strategies на структурированных форматах;
- преподаватель или рецензент, которому нужна понятная демонстрация архитектуры и экспериментов.

## 6. Основной сценарий

1. Пользователь собирает AFL++ и demo-проект.
2. Пользователь запускает `run_fake.sh` для проверки без внешнего LLM API.
3. AFL++ стартует с `AFL_CUSTOM_MUTATOR_LIBRARY=build/afl_llm_mutator.so`.
4. Отдельный Python-воркер наполняет очередь готовыми кандидатами.
5. Custom mutator быстро запрашивает кандидата у воркера через локальный IPC.
6. Если кандидат есть, мутатор смешивает его с текущим seed и отдает AFL++.
7. Если кандидата нет, мутатор делает дешевую локальную fallback-мутацию.
8. Когда AFL++ находит новый интересный queue entry, мутатор отправляет его воркеру.
9. Воркер сохраняет feedback sample и использует его в следующих LLM prompts.
10. Пользователь анализирует coverage, crashes, queue и статистику mutator hit/miss.

## 7. Функциональные требования

### 7.1 AFL++ custom mutator

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
- Должен поддерживать `AFL_CUSTOM_MUTATOR_ONLY=1` для форматов, где стандартные AFL++ стадии слишком часто ломают структуру.
- Должен работать без воркера: при недоступном IPC использовать локальную fallback-мутацию.
- Должен ограничивать размер кандидатов по `max_size` и своему внутреннему буферу.
- Не должен делать сетевые HTTP-запросы к LLM.

### 7.2 LLM worker

- Должен запускаться отдельным процессом.
- Должен поддерживать очередь кандидатов фиксированного размера.
- Должен иметь producer-потоки, которые заранее генерируют новые inputs.
- Должен поддерживать fake-режим без внешнего API.
- Должен поддерживать real LLM режим при заданном `LLM_API_URL`.
- Должен работать с OpenAI-compatible endpoint формата `chat/completions`.
- Должен читать prompt из файла и перечитывать его при изменении.
- Должен загружать стартовые seeds из директории.
- Должен сохранять feedback samples в `runtime/discovered`.

### 7.3 IPC-протокол

MVP-протокол:

- `G` - запрос готового кандидата от мутатора к воркеру;
- ответ `uint32 big-endian length`;
- `length == 0` означает cache miss;
- при `length > 0` далее передается payload;
- `A` + `uint32 big-endian length` + payload - отправка нового интересного input от AFL++ в воркер.

Транспорт:

- по умолчанию TCP loopback `tcp://127.0.0.1:15333`;
- желательно сохранить поддержку Unix socket и abstract Unix socket для локальных запусков.

### 7.4 Prompting

- Prompt должен описывать допустимый формат входа target-программы.
- Prompt должен требовать plain text без markdown, пояснений и code fences.
- В prompt должны добавляться несколько seed examples из стартового и feedback corpus.
- В real LLM режиме должны задаваться:
  - `LLM_API_URL`;
  - `LLM_API_KEY` при необходимости;
  - `LLM_MODEL`;
  - timeout и лимиты размера результата.

### 7.5 Demo target

MVP target - `target_dsl.c`.

Требования:

- stdin-ввод;
- persistent-friendly цикл через `__AFL_LOOP`;
- простая line-based грамматика;
- скрытый crash-path, требующий осмысленной комбинации команд, регистров, проверок и значений;
- возможность локального smoke-test без AFL++.

### 7.6 CLI и запуск

Проект должен иметь:

- `Makefile` для сборки target и mutator;
- `run_fake.sh` для запуска без внешнего API;
- `run_real_llm.sh` для запуска с реальным LLM endpoint;
- README с командами сборки, запуска и переменными окружения.

Ключевые переменные окружения:

- `AFLPP_DIR`;
- `AFL_OUTPUT_DIR`;
- `AFL_SEEDS_DIR`;
- `AFL_CUSTOM_MUTATOR_ONLY`;
- `LLM_MUTATOR_ADDR`;
- `LLM_MUTATOR_SOCK`;
- `LLM_MUTATOR_PROMPT_FILE`;
- `LLM_MUTATOR_SEED_DIR`;
- `LLM_MUTATOR_DISCOVERED_DIR`;
- `LLM_MUTATOR_QUEUE_SIZE`;
- `LLM_MUTATOR_WORKERS`;
- `LLM_API_URL`;
- `LLM_API_KEY`;
- `LLM_MODEL`;
- `LLM_API_TIMEOUT`.

## 8. Нефункциональные требования

### Производительность

- `afl_custom_fuzz()` должен завершаться быстро и не зависеть от latency LLM API.
- При пустой очереди fallback-мутация должна быть дешевой.
- Reconnect к воркеру должен быть ограничен по частоте, чтобы не замедлять fuzzing при недоступном сервере.
- Размеры payload должны ограничиваться, чтобы не раздувать память и время исполнения target.

### Надежность

- Отсутствие LLM API, падение воркера или пустая очередь не должны останавливать AFL++.
- Ошибки LLM endpoint должны логироваться воркером и приводить к retry/backoff.
- Некорректный ответ LLM должен обрезаться и безопасно кодироваться в bytes.
- Feedback corpus должен сохраняться на диск.

### Безопасность

- API-ключи передаются только через переменные окружения.
- В репозитории не должно быть secrets.
- Воркер должен слушать локальный адрес по умолчанию.
- Размер feedback и generated samples должен быть ограничен.

### Воспроизводимость

- Должен быть fake-режим без сети.
- Должен быть smoke-test для target crash-path.
- Стартовые seeds и prompt должны храниться в репозитории.
- Эксперименты должны сохранять output AFL++ в отдельные директории.

## 9. Метрики успеха

Минимальные метрики:

- проект собирается командой `make all`;
- `make smoke` собирает host target и mutator;
- `run_fake.sh` запускает AFL++ и воркер без внешнего API;
- custom mutator работает при доступном и недоступном воркере;
- feedback samples появляются в `runtime/discovered`;
- скрытый crash-path достижим на demo target.

Сравнительные метрики:

- time-to-first-crash для обычного AFL++ и AFL++ с LLM-мутатором;
- количество найденных unique paths за фиксированное время;
- количество queue entries за фиксированное время;
- доля syntactically valid inputs;
- hit/miss ratio очереди LLM-кандидатов;
- overhead custom mutator относительно baseline AFL++.

## 10. MVP

MVP считается готовым, если:

1. AFL++ собран и может запускать `target_dsl`.
2. `afl_llm_mutator.so` подключается через `AFL_CUSTOM_MUTATOR_LIBRARY`.
3. Python worker генерирует кандидаты в fake-режиме.
4. Mutator получает кандидаты через IPC и использует fallback при miss.
5. `run_fake.sh` запускает полный pipeline одной командой.
6. `run_real_llm.sh` запускает тот же pipeline с OpenAI-compatible endpoint.
7. Новый AFL++ queue entry отправляется в worker через `afl_custom_queue_new_entry`.
8. README объясняет сборку, запуск и адаптацию под другой target.

## 11. Архитектура

```text
                  +---------------------------+
                  | prompt.txt + seeds +      |
                  | runtime/discovered        |
                  +-------------+-------------+
                                |
                                v
                      +---------+---------+
                      | LLM mutator       |
                      | worker            |
                      | fake / real LLM   |
                      +---------+---------+
                                ^
              G: get candidate | | A: feedback sample
                                v
                      +---------+---------+
                      | AFL++ custom      |
                      | mutator .so       |
                      +---------+---------+
                                |
                                v
                      +---------+---------+
                      | AFL++ afl-fuzz    |
                      | coverage engine   |
                      +---------+---------+
                                |
                                v
                      +---------+---------+
                      | target / harness  |
                      | stdin + persistent|
                      +-------------------+
```

Ключевое архитектурное решение: LLM вынесена в отдельный асинхронный worker. AFL++ hot path получает только уже готовые inputs из локальной очереди.

## 12. План реализации

### Этап 1. Стабилизация demo

- Проверить сборку `llm_aflpp_demo`.
- Проверить fake-запуск.
- Проверить smoke-test crash-path.
- Зафиксировать README-команды и expected behavior.

### Этап 2. Метрики и логирование

- Добавить лог hit/miss/requests для custom mutator.
- Добавить статистику worker queue size, produced samples, LLM errors.
- Добавить простой script для сравнения baseline AFL++ vs LLM-mutator AFL++.

### Этап 3. Feedback loop

- Улучшить обработку `afl_custom_queue_new_entry`.
- Исключать дубликаты feedback samples.
- Ограничить размер и количество persisted samples.
- Добавлять наиболее свежие или интересные samples в prompt.

### Этап 4. Адаптация к реальному target

- Заменить `target_dsl.c` на реальный harness.
- Переписать `prompt.txt` под реальный формат.
- Подготовить валидные стартовые seeds.
- При необходимости реализовать `afl_custom_post_process()` для трансляции LLM-представления в wire-format.

### Этап 5. Улучшение стратегии мутации

- Добавить grammar-aware fallback mutator.
- Разделить LLM-генерации на несколько стратегий: repair, extend, combine, edge-values.
- Добавить rate limiting для LLM.
- Рассмотреть shared-memory ring buffer или mmap вместо socket при необходимости.

## 13. Риски

- LLM может генерировать слишком однотипные inputs.
- LLM может часто нарушать формат без строгого prompt и post-processing.
- Внешний API добавляет latency, стоимость и нестабильность.
- Слишком частое использование LLM-кандидатов может снизить execs/sec.
- `AFL_CUSTOM_MUTATOR_ONLY=1` может убрать полезные стандартные стадии AFL++.
- Demo target может быть слишком простым и не показать преимуществ на реальных форматах.

## 14. Открытые вопросы

- Какой реальный target будет использоваться после DSL demo?
- Нужен ли формат внутреннего представления, например JSON/AST, с последующим `post_process()`?
- Какие модели будут тестироваться: локальные или облачные?
- Нужно ли учитывать стоимость LLM-запросов как отдельную метрику?
- Какой режим сравнения считать основным: fixed time, fixed executions или fixed budget?
- Нужно ли хранить crash-inducing inputs отдельно от обычных feedback samples?

## 15. Acceptance criteria

- Команда `make all` в demo-проекте успешно собирает target и mutator.
- Команда `make smoke` успешно собирает локальный target для проверки.
- `run_fake.sh` запускает AFL++ pipeline без LLM API.
- `run_real_llm.sh` запускает AFL++ pipeline при заданном `LLM_API_URL`.
- AFL++ не блокируется при недоступном worker.
- Worker принимает `G` и `A` операции по IPC.
- Новые интересные inputs сохраняются в `runtime/discovered`.
- README и PRD описывают, как перенести подход с demo DSL на реальный target.

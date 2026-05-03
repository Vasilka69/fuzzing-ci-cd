# TASKS: LLM-мутатор для AFL++ fuzzing

Дата актуализации: 2026-05-03

Этот backlog собран из `PRD.md` и фактического состояния проекта. Активная рабочая директория demo: `main-project/llm_aflpp_demo/`.

## Текущий Статус

MVP практически готов: сборка, fake pipeline, real-mode worker, IPC `G`/`A`, fallback без worker и feedback persistence уже реализованы. Основной остаток - метрики, сравнение с baseline, чистка логирования и доводка документации/экспериментов.

## Готово

- [x] Создан demo-проект `main-project/llm_aflpp_demo/`.
- [x] Добавлен vendored AFL++ checkout в `main-project/AFLplusplus/`.
- [x] Реализован AFL++ custom mutator в `main-project/llm_aflpp_demo/afl_llm_mutator.c`.
- [x] Mutator собирается в `main-project/llm_aflpp_demo/build/afl_llm_mutator.so`.
- [x] Реализован Python worker `main-project/llm_aflpp_demo/llm_mutator_server.py`.
- [x] Поддержан fake режим без внешнего API.
- [x] Поддержан real LLM режим через OpenAI-compatible `chat/completions`.
- [x] Реализован IPC protocol: `G` для candidate и `A` для feedback.
- [x] Поддержан TCP loopback `tcp://127.0.0.1:15333`.
- [x] Поддержаны Unix socket path и abstract Unix socket.
- [x] Добавлен demo target `main-project/llm_aflpp_demo/target_dsl.c`.
- [x] Добавлены prompt, dictionary и стартовые seeds.
- [x] Добавлены `run_fake.sh` и `run_real_llm.sh`.
- [x] Добавлен `README.md` для demo.
- [x] Проверен `make all`.
- [x] Проверен `make smoke`.
- [x] Проверен контрольный crash-path target.
- [x] Проверен fake pipeline AFL++ + worker.
- [x] Проверен worker feedback persistence в `runtime/discovered/`.
- [x] Проверен fallback AFL++ run без worker.
- [x] Проверен real-mode worker через локальный OpenAI-compatible mock endpoint.
- [x] Актуализирован `PRD.md` под фактические repo-relative пути.
- [x] Добавлен этот `TASKS.md`.
- [x] Добавлен `AGENTS.md`.

## P0: Закрыть MVP Для Сдачи

- [ ] Исправить warning smoke-сборки `target_dsl.c`: `read called with bigger length than size of the destination buffer`.
- [ ] Исправить clang warning в AFL-сборке `target_dsl.c` про `#pragma GCC optimize("O0")`.
- [ ] Уточнить `README.md`: добавить все поддерживаемые env vars из `PRD.md`.
- [ ] Уточнить `README.md`: добавить заметку про `NO_PROXY=127.0.0.1,localhost` для локальных OpenAI-compatible endpoints.
- [ ] Сделать короткий smoke script или make target для проверки IPC `G`/`A` без AFL++.
- [ ] Добавить ожидаемый результат smoke-команд в README: build artifacts, crash-path behavior, feedback path.

## P1: Метрики И Сравнение

- [ ] Добавить экспорт mutator counters: `requests`, `hits`, `misses`, hit ratio.
- [ ] Выбрать формат экспорта counters: stderr summary, file в `runtime/`, или AFL custom introspection-friendly output.
- [ ] Добавить worker counters: produced, served, queue misses, feedback accepted, feedback rejected, LLM errors.
- [ ] Исправить пустой `producer error` при заполненной очереди worker.
- [ ] Добавить script `main-project/llm_aflpp_demo/compare_runs.sh` или аналог:
  - baseline AFL++ без custom mutator;
  - AFL++ с custom mutator + fake worker;
  - фиксированное время или фиксированное число executions.
- [ ] Сохранять результаты сравнений в отдельных директориях под `main-project/llm_aflpp_demo/output/`.
- [ ] Добавить парсер `fuzzer_stats` для summary metrics.
- [ ] Документировать минимальный формат отчета: execs/sec, corpus_found, edges_found, bitmap_cvg, saved_crashes, hit/miss.
- [ ] Добавить способ считать syntactically valid inputs для DSL corpus.

## P1: Feedback Loop

- [ ] Добавить dedupe feedback samples перед записью в `runtime/discovered/`.
- [ ] Ограничить количество persisted feedback samples.
- [ ] Добавить политику выбора examples для prompt: свежие, маленькие, coverage-interesting или случайная смесь.
- [ ] Не сохранять пустые или слишком короткие samples, если они не полезны как feedback.
- [ ] Рассмотреть отдельную директорию для crash-inducing samples.

## P2: Улучшение Мутаций

- [ ] Сделать fallback mutator более grammar-aware для DSL.
- [ ] Разделить fake/LLM генерацию на стратегии: repair, extend, combine, edge-values.
- [ ] Добавить rate limiting для real LLM режима.
- [ ] Добавить настройку temperature/max_tokens через env vars.
- [ ] Добавить post-processing для удаления markdown/code fences из LLM output.
- [ ] Добавить validation/sanitization DSL-кандидатов перед отправкой в AFL++.

## P2: Адаптация К Реальному Target

- [ ] Выбрать реальный target после DSL demo.
- [ ] Определить формат seed corpus для реального target.
- [ ] Подготовить prompt под реальный формат.
- [ ] При необходимости реализовать wire-format translation в `afl_custom_post_process()`.
- [ ] Добавить отдельный README-раздел с шагами адаптации.

## P3: Инфраструктура И Чистота

- [ ] Добавить CI или локальный `make check`, который запускает `make all`, `make smoke`, IPC smoke.
- [ ] Проверить, что generated dirs `build/`, `output/`, `runtime/discovered/` остаются ignored.
- [ ] Добавить secret-scan команду для проекта без vendored AFL++ и архивов материалов.
- [ ] Решить, остаются ли root-level `*.html`, `*.json`, `migrations/` в этом репозитории или их нужно вынести из fuzzing MVP.
- [ ] Уточнить в документации, что `main-project/AFLplusplus/` является vendored upstream dependency.

## Команды Проверки

```bash
cd main-project/llm_aflpp_demo
make all
make smoke
printf 'MODE DEBUG\nSET A 1337\nSET B 109\nSET C 16705\nAPPEND open\nCHECK MAGIC\nCHECK PLEASE\nCHECK FIZZ\nCHECK OPEN\nLOOP 7\nCRASH NOW\n' | ./build/target_dsl_cc
```

```bash
cd main-project/llm_aflpp_demo
AFL_I_DONT_CARE_ABOUT_MISSING_CRASHES=1 AFL_SKIP_CPUFREQ=1 AFL_NO_UI=1 timeout 8s ./run_fake.sh
```

```bash
cd main-project/llm_aflpp_demo
AFL_I_DONT_CARE_ABOUT_MISSING_CRASHES=1 AFL_SKIP_CPUFREQ=1 AFL_NO_UI=1 \
AFL_CUSTOM_MUTATOR_LIBRARY="$PWD/build/afl_llm_mutator.so" \
AFL_CUSTOM_MUTATOR_ONLY=1 \
timeout 4s ../AFLplusplus/afl-fuzz -i seeds -o output/no_worker -x demo.dict -- build/target_dsl
```

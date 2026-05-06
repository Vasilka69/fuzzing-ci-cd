# TASKS: LLM-мутатор для AFL++ fuzzing

Дата актуализации: 2026-05-06

Этот backlog собран из `PRD.md` и фактического состояния проекта. Активная рабочая директория: `llm-aflpp/`.

## Текущий Статус

MVP практически готов: сборка, fake pipeline, real-mode worker, IPC `G`/`A`, fallback без worker и feedback persistence уже реализованы. Основной остаток - метрики, сравнение с baseline, чистка логирования и доводка документации/экспериментов.

## Готово

- [x] Создан проект `llm-aflpp/`.
- [x] Проект переименован из `llm_aflpp_demo` в `llm-aflpp/`.
- [x] Активные директории `llm-aflpp/` и `AFLplusplus/` вынесены из `main-project/` в корень репозитория.
- [x] Побочные материалы вынесены в `Прочее/`.
- [x] Код разнесен по директориям `src/`, `targets/`, `tests/`, `scripts/`.
- [x] Добавлен vendored AFL++ checkout в `AFLplusplus/`.
- [x] Реализован AFL++ custom mutator в `llm-aflpp/src/mutator/afl_llm_mutator.c`.
- [x] Mutator собирается в `llm-aflpp/build/afl_llm_mutator.so`.
- [x] Реализован Python worker `llm-aflpp/src/worker/llm_mutator_server.py`.
- [x] Поддержан fake режим без внешнего API.
- [x] Поддержан real LLM режим через OpenAI-compatible `chat/completions`.
- [x] Реализован IPC protocol: `G` для candidate и `A` для feedback.
- [x] Поддержан TCP loopback `tcp://127.0.0.1:15333`.
- [x] Поддержаны Unix socket path и abstract Unix socket.
- [x] Добавлен DSL target `llm-aflpp/targets/dsl/target_dsl.c`.
- [x] Добавлены prompt, dictionary и стартовые seeds.
- [x] Добавлены `scripts/run_fake.sh` и `scripts/run_real_llm.sh`.
- [x] Добавлен `README.md` для проекта.
- [x] Добавлен `tests/ipc_smoke.py` / `make ipc-smoke` для проверки IPC `G`/`A` без AFL++.
- [x] Проверен `make all`.
- [x] Проверен `make smoke`.
- [x] Проверен `make ipc-smoke`.
- [x] Проверен контрольный crash-path target.
- [x] Проверен fake pipeline AFL++ + worker.
- [x] Проверен worker feedback persistence в `runtime/discovered/`.
- [x] Проверен fallback AFL++ run без worker.
- [x] Проверен real-mode worker через локальный OpenAI-compatible mock endpoint.
- [x] Актуализирован `PRD.md` под фактические repo-relative пути.
- [x] Добавлен этот `TASKS.md`.
- [x] Добавлен `AGENTS.md`.

## P0: Закрыть MVP Для Сдачи

- [x] Исправить warning smoke-сборки `target_dsl.c`: `read called with bigger length than size of the destination buffer`.
- [x] Исправить clang warning в AFL-сборке `target_dsl.c` про `#pragma GCC optimize("O0")`.
- [x] Уточнить `README.md`: добавить все поддерживаемые env vars из `PRD.md`.
- [x] Уточнить `README.md`: добавить заметку про `NO_PROXY=127.0.0.1,localhost` для локальных OpenAI-compatible endpoints.
- [x] Сделать короткий smoke script или make target для проверки IPC `G`/`A` без AFL++.
- [x] Добавить ожидаемый результат smoke-команд в README: build artifacts, crash-path behavior, feedback path.

## P1: Метрики И Сравнение

- [ ] Добавить экспорт mutator counters: `requests`, `hits`, `misses`, hit ratio.
- [ ] Выбрать формат экспорта counters: stderr summary, file в `runtime/`, или AFL custom introspection-friendly output.
- [ ] Добавить worker counters: produced, served, queue misses, feedback accepted, feedback rejected, LLM errors.
- [ ] Исправить пустой `producer error` при заполненной очереди worker.
- [ ] Добавить script `llm-aflpp/scripts/compare_runs.sh` или аналог:
  - baseline AFL++ без custom mutator;
  - AFL++ с custom mutator + fake worker;
  - фиксированное время или фиксированное число executions.
- [ ] Сохранять результаты сравнений в отдельных директориях под `llm-aflpp/output/`.
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

- [ ] Выбрать реальный target после DSL target.
- [ ] Определить формат seed corpus для реального target.
- [ ] Подготовить prompt под реальный формат.
- [ ] При необходимости реализовать wire-format translation в `afl_custom_post_process()`.
- [ ] Добавить отдельный README-раздел с шагами адаптации.

## P3: Инфраструктура И Чистота

- [ ] Добавить CI или локальный `make check`, который запускает `make all`, `make smoke`, IPC smoke.
- [ ] Проверить, что generated dirs `build/`, `output/`, `runtime/discovered/` остаются ignored.
- [ ] Добавить secret-scan команду для проекта без vendored AFL++ и архивов материалов.
- [x] Вынести root-level `*.html`, `*.json`, `migrations/`, материалы и временные логи из fuzzing MVP в `Прочее/`.
- [x] Уточнить в документации, что `AFLplusplus/` является vendored upstream dependency.

## Команды Проверки

```bash
cd llm-aflpp
make all
make smoke
make ipc-smoke
printf 'MODE DEBUG\nSET A 1337\nSET B 109\nSET C 16705\nAPPEND open\nCHECK MAGIC\nCHECK PLEASE\nCHECK FIZZ\nCHECK OPEN\nLOOP 7\nCRASH NOW\n' | ./build/target_dsl_cc
```

```bash
cd llm-aflpp
AFL_I_DONT_CARE_ABOUT_MISSING_CRASHES=1 AFL_SKIP_CPUFREQ=1 AFL_NO_UI=1 timeout 8s ./scripts/run_fake.sh
```

```bash
cd llm-aflpp
AFL_I_DONT_CARE_ABOUT_MISSING_CRASHES=1 AFL_SKIP_CPUFREQ=1 AFL_NO_UI=1 \
AFL_CUSTOM_MUTATOR_LIBRARY="$PWD/build/afl_llm_mutator.so" \
AFL_CUSTOM_MUTATOR_ONLY=1 \
timeout 4s ../AFLplusplus/afl-fuzz -i targets/dsl/seeds -o output/no_worker -x targets/dsl/dsl.dict -- build/target_dsl
```

# PRD: Fuzzing Service

## Назначение

Fuzzing service выполняет AFL++ fuzzing и интегрирует готовое fuzzing ядро, включающее custom mutator, LLM worker, feedback loop и fake/real LLM modes. Сервис является обычным executor'ом и соблюдает общий lifecycle job.

## Scope

Входит:

- adapter layer к готовому fuzzing engine;
- подготовка target artifact/source snapshot;
- seed corpus/dictionary/prompt loading;
- запуск fake mode для воспроизводимых тестов;
- запуск real LLM mode через endpoint ref;
- запуск AFL++/engine process с timeout/resource limits;
- сбор crashes/hangs/corpus/stats/report;
- mapping policy `fail_on_crash`/`fail_on_hang` в job status.

Не входит:

- разработка нового fuzzing engine с нуля;
- UI для анализа crash cases;
- облачный LLM provider management.

## Входные параметры

```json
{
  "target_artifact_uri": "storage://artifacts/target",
  "target_command": "./target @@",
  "seed_corpus_uri": "storage://fuzzing/seeds.zip",
  "dictionary_uri": "storage://fuzzing/dict.txt",
  "mode": "fake",
  "budget_seconds": 3600,
  "memory_limit_mb": 2048,
  "prompt_uri": "storage://fuzzing/prompt.txt",
  "llm": {
    "endpoint_ref": "connection://local-llm",
    "model": "model-name",
    "temperature": 0.7
  },
  "policy": {
    "fail_on_crash": true,
    "fail_on_hang": false,
    "max_crashes": 1,
    "min_execs": 10000,
    "save_corpus": true
  }
}
```

## Выходы

- `fuzzing_report_uri`
- `crash_artifacts[]`
- `hang_artifacts[]`
- `corpus_uri`
- `afl_stats`
- `mutator_stats`
- `llm_worker_stats`
- `logs_uri`

## Политика статуса

- Crash target-программы — результат тестирования, а не infrastructure error.
- Если `fail_on_crash=true` и crash найден, job status = `failed`, error type = `fuzzing_crash_found`.
- Если crash найден, artifacts всё равно сохраняются.
- Ошибка запуска AFL++/engine, отсутствие target или неверный `target_command` — ошибка выполнения job.

## Acceptance criteria

- Service can run fake mode without external LLM.
- Service can call real engine through adapter interface.
- Fuzzing hot path does not call LLM synchronously from Java service.
- Report/crashes/hangs/logs are uploaded to storage.
- Policy correctly maps findings to success/failed.
- Timeout terminates engine and preserves partial logs/report.

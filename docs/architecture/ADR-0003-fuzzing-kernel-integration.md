# ADR-0003 — Интеграция готового fuzzing-ядра через adapter boundary

Дата: 2026-05-29
Статус: принято.

## Контекст

Планируется внедрить готовое fuzzing-ядро в `fuzzing-service`. Ядро может быть написано не на Java и включать AFL++, custom mutator, LLM worker, demo target и вспомогательные scripts. Переписывание ядра увеличит риск и выйдет за рамки дипломного MVP.

## Решение

Java/Spring Boot `fuzzing-service` не переписывает ядро, а управляет им через adapter boundary:

```text
Kafka job -> FuzzingJobHandler -> FuzzingKernelAdapter -> core process/container -> ResultCollector -> artifacts/events
```

Adapter отвечает за:

- подготовку workspace;
- materialization target/corpus/config;
- запуск ядра как process/container;
- передачу env/config файлов;
- сбор stdout/stderr/stats/crashes/corpus;
- mapping exit code и policy в `ExecutorEventMessage`.

## Обязательное правило hot path

`afl_custom_fuzz()` не делает сетевой LLM-запрос. LLM worker заранее генерирует candidates и кладет их в локальную очередь. Mutator быстро читает из очереди, а при miss использует local grammar-aware fallback.

## Альтернативы

1. Переписать ядро на Java — отклонено: высокий риск и нет смысла для диплома.
2. Запускать LLM напрямую из mutator — отклонено: latency убьет AFL throughput.
3. Вынести fuzzing в отдельный non-Java сервис без Spring Boot wrapper — возможно, но нарушит единообразие executor-слоя.

## Последствия

- Нужны adapter tests на fake kernel.
- Docker image fuzzing-service будет тяжелее остальных из-за AFL++/toolchain.
- Нужно явно документировать boundaries: что часть ядра готовая, а что реализовано в сервисе.

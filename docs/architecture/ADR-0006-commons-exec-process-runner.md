# ADR-0006 — Apache Commons Exec для process runner

Дата: 2026-05-30
Статус: принято.

## Контекст

`CORE-012` требует общий process runner в `common/cicd-executor-core`: запуск внешней команды,
timeout, grace period и chunking stdout/stderr.

Executor-ы будут запускать пользовательские команды и инструменты сборки, поэтому runtime не должен
собирать shell string из пользовательского ввода или полагаться на самодельную низкоуровневую обвязку
над `ProcessBuilder`.

## Решение

Подключить `org.apache.commons:commons-exec` версии `1.6.0` как production dependency
`common/cicd-executor-core`.

`LocalProcessRunner` использует Apache Commons Exec для запуска процесса, stream handler-а и lifecycle
дочернего процесса. Проектный API принимает команду только как `List<String>`: executable и аргументы
передаются отдельно, без парсинга shell string.

Timeout реализован поверх asynchronous execution: после превышения timeout runner отправляет graceful
destroy процессу и descendants, ждет grace period, затем применяет forced destroy к оставшимся процессам.
Stdout/stderr читаются раздельно и передаются caller-у byte chunk-ами с sequence number.

## Альтернативы

JDK `ProcessBuilder` без зависимости уменьшает dependency surface, но перекладывает на проект больше
тонкостей запуска, stream pumping и lifecycle handling. Для executor runtime это повышает риск deadlock-ов
на stdout/stderr и нестабильного timeout behavior.

Apache Commons Exec `ExecuteWatchdog` без собственной grace-фазы проще, но watchdog уничтожает процесс
после timeout без контролируемого grace period, а `CORE-012` явно требует grace period.

## Последствия

- Общий core-модуль получает production dependency на Apache Commons Exec.
- Версия зафиксирована в parent POM через dependency management.
- Зависимость зрелая, поддерживается Apache Commons, распространяется под Apache-2.0 и не добавляет
  runtime transitive dependency graph.
- Изменение не меняет Kafka/OpenSearch/JSON contracts.
- Интеграция runner-а в `build-service`, `script-service` и другие executor-ы остается отдельными
  сервисными задачами.

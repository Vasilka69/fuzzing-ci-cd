# Логика работы логов через OpenSearch

Документ описывает текущую целевую схему логирования CI/CD-платформы через OpenSearch. Его можно использовать как исходный контекст для генерации PRD, TASKS, архитектурных задач и проверочных сценариев.

## 1. Назначение

OpenSearch используется как основное внешнее хранилище логов исполнения задач и, при соответствующей настройке, как транспорт событий от исполняющих сервисов к `master-service`.

Цель схемы:

- не хранить большие текстовые логи в таблице `job_history`;
- хранить в БД master только историю, статусы, длительность и метаданные запуска;
- получать логи по `jobId` и `historyId` напрямую из OpenSearch;
- быстро показывать изменения статусов и логов на frontend через master API и SSE;
- сохранить возможность переключения транспорта событий через настройки.

## 2. Участники

- `build-service` - выполняет сборку, публикует логи сборки и события job.
- `deploy-service` - выполняет развертывание, публикует логи deploy и события job.
- `script-service` - выполняет пользовательские скрипты, публикует логи сценария и события job.
- `cicd-common` - содержит общий контракт события, OpenSearch-документ и publisher-компоненты.
- `master-service` - читает события и логи из OpenSearch, обновляет историю в БД, отдает данные frontend.
- `frontend` - отображает запуски пайплайна, детали jobs и логи из master API; также подписывается на SSE.
- `OpenSearch` - хранит документы событий и логов.

## 3. Настройки

Общие настройки для executor-сервисов и master:

```yaml
app:
  executor-events:
    transport: kafka | opensearch
    opensearch:
      enabled: true
      endpoint: http://localhost:9200
      index: cicd-executor-events
```

Ключевые переменные окружения:

- `EXECUTOR_EVENTS_TRANSPORT`: `kafka` или `opensearch`.
- `OPENSEARCH_LOGS_ENABLED`: включает публикацию/чтение логов через OpenSearch.
- `OPENSEARCH_ENDPOINT`: адрес OpenSearch.
- `OPENSEARCH_EVENTS_INDEX`: имя индекса.

Дополнительные настройки master для чтения событий:

```yaml
app.executor-events.opensearch.batch-size: 200
app.executor-events.opensearch.poll-interval-ms: 500
app.executor-events.opensearch.startup-lookback-seconds: 10
app.executor-events.opensearch.max-pages-per-poll: 10
app.executor-events.opensearch.history-fetch-size: 500
app.executor-events.opensearch.history-max-pages: 10
```

## 4. Важная особенность транспорта

В executor-сервисах публикация разделена на два независимых канала:

- `ExecutorEventPublisher` публикует служебные события job без текстовых логов.
- `ExecutorLogPublisher` публикует текстовые логи отдельными документами `JOB_LOG`.

Даже при `transport=kafka` логи могут продолжать писаться в OpenSearch, если `app.executor-events.opensearch.enabled=true`.

Для целевой эксплуатации через OpenSearch рекомендуется:

```properties
EXECUTOR_EVENTS_TRANSPORT=opensearch
OPENSEARCH_LOGS_ENABLED=true
```

эти значения должны быть заданы для `master-service`, `build-service`, `deploy-service` и `script-service`.

## 5. Контракт события

Внутренний контракт события представлен `ExecutorEventMessage`:

```text
eventType       - тип события: JOB_QUEUED, JOB_RUNNING, JOB_FINISHED, JOB_SKIPPED, JOB_LOG и т.д.
pipelineId      - идентификатор pipeline
jobId           - идентификатор job
status          - статус: QUEUED, RUNNING, SUCCESS, FAILED, CANCELED
historyId       - идентификатор записи истории запуска job
startedAt       - время старта
finishedAt      - время завершения
durationMs      - длительность
logs            - текст логов, используется только для лог-документов
additionalData  - произвольные метаданные выполнения
```

`historyId` генерируется executor-сервисом для конкретного запуска job и используется как ключ связи между записью истории в БД master и логами в OpenSearch.

## 6. Документ OpenSearch

Все документы пишутся в один индекс, по умолчанию `cicd-executor-events`.

Структура `ExecutorEventDocument`:

```text
documentId      - UUID документа
ingestedAt      - время записи документа в OpenSearch
sourceService   - build-service, deploy-service или script-service
eventType       - тип события или JOB_LOG
pipelineId      - UUID pipeline строкой
jobId           - UUID job строкой
status          - статус выполнения
historyId       - ID истории job
startedAt       - время старта
finishedAt      - время завершения
durationMs      - длительность в миллисекундах
logs            - текст логов, только для JOB_LOG
additionalData  - метаданные
```

Индекс создается автоматически при первой публикации, если еще не существует. Маппинг:

- `documentId`, `sourceService`, `eventType`, `pipelineId`, `jobId`, `status` - `keyword`;
- `ingestedAt`, `startedAt`, `finishedAt` - `date`;
- `historyId`, `durationMs` - numeric long;
- `logs` - `text`;
- `additionalData` - object.

## 7. Разделение событий и логов

Executor-сервисы вызывают публикацию в таком порядке:

```text
executorLogPublisher.publish(jobId, eventMessage)
executorEventPublisher.publish(jobId, withoutLogs(eventMessage))
```

Итог:

- лог-документ имеет `eventType=JOB_LOG`, содержит `logs`, содержит `additionalData.logOnly=true`;
- в `additionalData.sourceEventType` лог-документа сохраняется исходный тип события, например `JOB_RUNNING` или `JOB_FINISHED`;
- служебный event-документ имеет исходный `eventType`, например `JOB_FINISHED`, но поле `logs` очищено;
- master не получает большие логи в событии и не пишет их в БД.

`OpenSearchExecutorLogPublisher` публикует документ только если:

- `eventMessage` не `null`;
- `historyId` задан;
- `logs` не пустые.

Для лог-документов используется `refresh=true`, чтобы frontend и диагностика могли быстрее прочитать свежие логи.

## 8. Как executor-сервисы формируют логи

Каждый executor формирует человекочитаемый текст логов перед публикацией финального события.

`build-service` включает:

- описание шага;
- команду;
- рабочую директорию;
- статус и exit code;
- вывод команды;
- результат публикации артефакта в Nexus, если публикация выполнялась.

`deploy-service` включает:

- план deploy;
- режим deploy;
- имя контейнера для Docker;
- URL артефакта в Nexus;
- локальную копию артефакта;
- статус и exit code;
- вывод deploy-команды.

`script-service` включает:

- статус сценария;
- последовательность шагов;
- вывод каждого шага;
- результат публикации артефакта, если публикация задана.

## 9. Чтение событий master-сервисом

Если `app.executor-events.transport=opensearch`, в `master-service` включается `OpenSearchExecutorEventPoller`.

Poller:

- стартует с нижней границы `now - startupLookbackSeconds`;
- периодически читает индекс `cicd-executor-events`;
- сортирует документы по `ingestedAt ASC`, затем `documentId ASC`;
- использует `searchAfter`, чтобы продолжать чтение с последнего обработанного документа;
- читает не больше `batchSize * maxPagesPerPoll` документов за один проход;
- игнорирует документы с `eventType=JOB_LOG`;
- конвертирует остальные документы обратно в `ExecutorEventMessage`;
- передает событие в `ExecutorEventService`.

`ExecutorEventService`:

- обновляет статус `JobEntity`;
- создает или обновляет `JobHistoryEntity`;
- сохраняет в БД только `id`, `job`, `startDate`, `duration`, `additionalData`;
- не сохраняет текст логов;
- запускает диагностику ошибок;
- публикует SSE-событие для frontend;
- уведомляет оркестратор pipeline, чтобы master мог перейти к следующей job.

## 10. Хранение в БД master

Текстовое поле `logs` удалено из таблицы `job_history` миграцией.

БД master хранит:

- ID истории (`historyId`);
- связь с job;
- дату старта;
- длительность;
- `additionalData` со служебными метаданными.

Логи по истории всегда восстанавливаются из OpenSearch по связке:

```text
jobId + historyId
```

## 11. Чтение логов для истории и UI

`OpenSearchHistoryLogService` читает документы:

```text
jobId = <jobId>
eventType = JOB_LOG
historyId exists
logs exists
```

Затем:

- документы сортируются по `ingestedAt ASC`, затем `documentId ASC`;
- документы группируются по `historyId`;
- куски логов объединяются через `\n`;
- результат возвращается как `Map<historyId, logs>`.

Эта логика используется:

- `JobHistoryService.findByJob`;
- `PipelineRunHistoryService.findByPipeline`;
- `JobFailureDiagnosticsService` для диагностики ошибок.

Frontend получает логи не напрямую из OpenSearch, а через master API:

- `GET /api/v1/pipelines/{pipelineId}/runs`;
- `GET /api/v1/job-history/by-job/{jobId}`.

## 12. SSE и быстрые обновления frontend

Master предоставляет SSE endpoint:

```text
GET /api/v1/logs/stream
GET /api/v1/logs/stream?jobId=<jobId>
```

Событие SSE называется `job-log` и содержит:

```text
jobId
pipelineId
eventType
status
historyId
logs
timestamp
additionalData
```

Frontend `PipelineDetailsView` подписывается на общий stream, фильтрует события по `pipelineId` и запускает тихое обновление истории запусков. Дополнительно используется polling:

- чаще при активных запусках;
- реже в простое.

## 13. Диагностика ошибок

Диагностика запускается master-сервисом для событий:

- `eventType=JOB_FINISHED`;
- `status != SUCCESS`;
- задан `historyId`;
- задан `jobId`.

Для анализа сервис сначала пытается получить последние строки логов из OpenSearch через `OpenSearchHistoryLogService.resolveTailLogsByHistoryId`. Если логи еще не доступны, используется fallback из события, но в целевой схеме события идут без логов, поэтому критично корректно индексировать `JOB_LOG`.

## 14. Последовательность выполнения

Типовой сценарий:

```text
1. UI отправляет команду запуска pipeline в master-service.
2. master-service отправляет команду нужному executor-сервису.
3. executor-service ставит job в очередь и публикует JOB_QUEUED.
4. executor-service начинает job, генерирует historyId и публикует JOB_RUNNING.
5. Если в событии есть logs и historyId, executor пишет отдельный JOB_LOG в OpenSearch.
6. executor выполняет команду сборки/deploy/script.
7. executor формирует финальные логи.
8. executor пишет JOB_LOG с финальными логами в OpenSearch.
9. executor пишет JOB_FINISHED без logs в OpenSearch.
10. master poller читает JOB_FINISHED, обновляет БД и статус job.
11. master читает JOB_LOG из OpenSearch при запросе истории.
12. frontend получает обновления через SSE и API master.
```

## 15. Инварианты для будущих доработок

- Не возвращать прямой доступ frontend к OpenSearch; frontend должен работать через master API.
- Не хранить текст логов в `job_history`.
- Не отправлять большие логи в Kafka/event-документах; служебные события должны идти без `logs`.
- Для каждого запуска job должен быть стабильный `historyId`.
- Все лог-документы должны иметь `eventType=JOB_LOG`.
- Лог-документы должны содержать `jobId`, `pipelineId`, `historyId`, `sourceService`, `logs`.
- Служебные документы должны иметь исходный `eventType`: `JOB_RUNNING`, `JOB_FINISHED`, `JOB_SKIPPED` и т.д.
- `additionalData` должен содержать `runId`, `commandType`, `phase`, `executorType`, `requestedAt`, `initiatedBy`, если эти значения доступны.
- Любой новый executor должен использовать общий `ExecutorEventPublisher` и `ExecutorLogPublisher` из `cicd-common`.

## 16. Ограничения текущей реализации

- Event-документы и log-документы хранятся в одном индексе, различаются по `eventType`.
- OpenSearch poller хранит cursor в памяти. После рестарта master перечитывает документы только в пределах `startupLookbackSeconds`.
- Для событий OpenSearch publisher не использует `refresh=true`, поэтому задержка доставки события зависит от refresh interval OpenSearch и poll interval master.
- Для логов используется `refresh=true`, поэтому логи становятся доступнее быстрее, чем обычные event-документы.
- Если `OPENSEARCH_LOGS_ENABLED=false`, новые логи не будут сохраняться, а поскольку `job_history.logs` удален, UI не сможет показать текст логов для новых запусков.
- Если `historyId` отсутствует, лог-документ не публикуется.

## 17. Требования для генерации TASKS/PRD

Функциональные требования:

- Система должна поддерживать хранение логов executor-сервисов в OpenSearch.
- Система должна читать логи из OpenSearch через master-service.
- Система не должна хранить текст логов в БД master.
- Система должна поддерживать переключение транспорта служебных событий через `app.executor-events.transport`.
- Система должна поддерживать SSE-обновления frontend при поступлении новых событий.
- Система должна отображать историю pipeline как запуски, внутри которых показаны stages/jobs/logs.

Нефункциональные требования:

- Логи должны быть доступны по `jobId + historyId`.
- Чтение истории не должно требовать сканирования всей БД.
- События должны быть идемпотентны на уровне `historyId`: повторное событие обновляет ту же запись истории.
- Ошибка чтения логов из OpenSearch не должна ломать выдачу истории: API может вернуть историю без логов.
- Ошибка публикации события/лога в OpenSearch должна логироваться executor-сервисом как инфраструктурная ошибка.

Acceptance criteria:

- При успешной job в OpenSearch появляется минимум один `JOB_LOG` и одно финальное событие `JOB_FINISHED`.
- В `JOB_FINISHED` поле `logs` отсутствует или `null`.
- В `JOB_LOG` поле `logs` содержит человекочитаемый вывод executor-сервиса.
- `GET /api/v1/pipelines/{pipelineId}/runs` возвращает jobs с логами, восстановленными из OpenSearch.
- После удаления поля `logs` из `job_history` UI продолжает отображать логи запусков.
- При `EXECUTOR_EVENTS_TRANSPORT=opensearch` master получает события через poller и обновляет статусы jobs.
- При `EXECUTOR_EVENTS_TRANSPORT=kafka` master получает события через Kafka, но логи все равно могут читаться из OpenSearch при `OPENSEARCH_LOGS_ENABLED=true`.


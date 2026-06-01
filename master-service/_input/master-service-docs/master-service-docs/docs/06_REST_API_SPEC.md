# 06. REST API master-service

Базовый префикс: `/api/v1`.

Общие правила:

- Коллекции возвращают `{ items, pageInfo }` или `{ items, nextCursor }`.
- Ошибка возвращается в формате `{ error: { code, message, details }, correlationId }`.
- Write-запросы, которые могут быть повторены клиентом, поддерживают `Idempotency-Key`.
- Frontend не обращается напрямую к PostgreSQL, Kafka, OpenSearch или storage.

## Auth

| Method | Endpoint | Функция |
| --- | --- | --- |
| `POST` | `/auth/login` | Авторизация. |
| `POST` | `/auth/logout` | Завершение сессии/token revoke, если реализовано. |
| `GET` | `/auth/me` | Текущий пользователь и effective permissions. |

## Folders / Pipelines

| Method | Endpoint | Функция |
| --- | --- | --- |
| `GET` | `/folders?parentId=&includePipelines=` | Дерево папок. |
| `POST` | `/folders` | Создать папку. |
| `PUT` | `/folders/{id}` | Обновить папку. |
| `DELETE` | `/folders/{id}` | Удалить/отвязать папку. |
| `GET` | `/pipelines?folderId=&isActive=&query=&page=&size=` | Список pipeline. |
| `POST` | `/pipelines` | Создать pipeline. |
| `GET` | `/pipelines/{id}` | Получить pipeline со stage/job/dependency graph. |
| `PUT` | `/pipelines/{id}` | Обновить metadata pipeline. |
| `DELETE` | `/pipelines/{id}` | Деактивировать/удалить pipeline. |

## Pipeline structure

| Method | Endpoint | Функция |
| --- | --- | --- |
| `POST` | `/pipelines/{id}/stages` | Создать stage. |
| `PUT` | `/stages/{id}` | Обновить stage. |
| `DELETE` | `/stages/{id}` | Удалить stage. |
| `POST` | `/stages/{id}/jobs` | Создать job. |
| `PUT` | `/jobs/{id}` | Обновить job. |
| `DELETE` | `/jobs/{id}` | Удалить job. |
| `POST` | `/jobs/{id}/dependencies` | Добавить dependency. |
| `DELETE` | `/jobs/{id}/dependencies/{dependencyId}` | Удалить dependency. |
| `POST` | `/pipelines/{id}/validate` | Проверить graph/templates/params без запуска. |

## Job templates

| Method | Endpoint | Функция |
| --- | --- | --- |
| `GET` | `/job-templates?jobType=` | Список templates. |
| `GET` | `/job-templates/{id}` | Template + JSON params template. |
| `POST` | `/job-templates/{id}/validate` | Валидация params. |

## Runs / Executions

| Method | Endpoint | Функция |
| --- | --- | --- |
| `POST` | `/pipelines/{id}/runs` | Запустить pipeline вручную. |
| `GET` | `/pipeline-runs?pipelineId=&status=&triggerType=&from=&to=&sort=` | Список запусков. |
| `GET` | `/pipeline-runs/{id}` | Детали запуска. |
| `GET` | `/pipeline-runs/{id}/graph` | Runtime graph со статусами. |
| `GET` | `/pipeline-runs/{id}/events?limit=&cursor=` | События запуска. |
| `POST` | `/pipeline-runs/{id}/cancel` | Отмена запуска. |
| `POST` | `/pipeline-runs/{id}/retry` | Retry failed/skipped частей. |
| `GET` | `/job-executions?pipelineRunId=&jobId=&status=&jobType=` | Список executions. |
| `GET` | `/job-executions/{id}` | Детали execution. |
| `POST` | `/job-executions/{id}/cancel` | Отмена job execution. |
| `POST` | `/job-executions/{id}/retry` | Retry конкретной job. |

## Logs / SSE

| Method | Endpoint | Функция |
| --- | --- | --- |
| `GET` | `/job-executions/{id}/logs?cursor=&limit=&tail=&from=&to=` | Логи из OpenSearch по `jobExecutionId`. |
| `GET` | `/jobs/{id}/history` | История job, сгруппированная по `jobExecutionId`. |
| `GET` | `/logs/stream` | SSE stream всех job events/logs. |
| `GET` | `/logs/stream?pipelineRunId={id}` | SSE для run. |
| `GET` | `/logs/stream?jobId={id}` | SSE для job. |
| `GET` | `/logs/stream?jobExecutionId={id}` | SSE для execution. |

SSE event names:

- `job-event` — статус/прогресс/артефакт.
- `job-log` — лог-фрагмент.
- `heartbeat` — служебный heartbeat.

## Artifacts

| Method | Endpoint | Функция |
| --- | --- | --- |
| `GET` | `/job-executions/{id}/artifacts` | Artifacts конкретной попытки. |
| `GET` | `/artifacts?pipelineRunId=&jobExecutionId=&artifactType=` | Список artifacts. |
| `GET` | `/artifacts/{id}` | Metadata. |
| `GET` | `/artifacts/{id}/download-url` | Временная ссылка скачивания. |

## Integrations / Secrets / Environments

| Method | Endpoint | Функция |
| --- | --- | --- |
| `GET` | `/external-connections?connectionType=` | Список подключений. |
| `POST` | `/external-connections` | Создать подключение. |
| `PUT` | `/external-connections/{id}` | Обновить подключение. |
| `GET` | `/secret-refs` | Ссылки на секреты без значений. |
| `POST` | `/secret-refs` | Создать ссылку на секрет. |
| `GET` | `/environments` | Deployment environments. |
| `POST` | `/deployment-approvals/{id}/approve` | Approve deployment. |
| `POST` | `/deployment-approvals/{id}/reject` | Reject deployment. |
| `GET` | `/deployment-releases?environmentId=&status=&releaseId=` | Releases history. |

## Monitoring / Audit / Permissions

| Method | Endpoint | Функция |
| --- | --- | --- |
| `GET` | `/executors` | Executor instances heartbeat/status. |
| `GET` | `/audit-events?from=&to=&actorId=&resourceType=` | Audit log. |
| `GET` | `/permissions?resourceType=&resourceId=` | Effective permissions. |
| `POST` | `/permissions` | Создать permission assignment. |
| `DELETE` | `/permissions/{id}` | Удалить permission assignment. |

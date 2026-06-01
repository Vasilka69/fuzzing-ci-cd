# 07. React UI на Ant Design

## Технологии

- React + TypeScript + Vite.
- Ant Design: Layout, Menu, Table, Form, Steps, Timeline, Tree, Tabs, Drawer, Modal, Tag, Alert, Descriptions, Progress, Spin.
- React Router.
- TanStack Query или RTK Query для REST cache.
- EventSource для SSE.
- Monaco Editor или простая AntD `Input.TextArea` для script/template JSON на MVP.

## Основные страницы

| Route | Страница | Функционал |
| --- | --- | --- |
| `/login` | LoginPage | Авторизация. |
| `/` | DashboardPage | Активные runs, failed jobs, executor health. |
| `/pipelines` | PipelineListPage | Folders tree, table pipelines, create/run actions. |
| `/pipelines/:id` | PipelineDetailsPage | Metadata, stages/jobs, last runs. |
| `/pipelines/:id/designer` | PipelineDesignerPage | Stage/job editor, dependencies, templates. |
| `/runs` | PipelineRunsPage | История запусков с фильтрами. |
| `/runs/:id` | PipelineRunPage | Runtime graph, statuses, logs, artifacts. |
| `/jobs/:id/history` | JobHistoryPage | История job по executions. |
| `/artifacts` | ArtifactListPage | Поиск и скачивание artifacts. |
| `/deployments` | DeploymentPage | Environments, approvals, releases. |
| `/executors` | ExecutorsPage | Heartbeat executor services. |
| `/settings/connections` | ConnectionsPage | External connections. |
| `/settings/secrets` | SecretRefsPage | Secret refs без значений. |
| `/admin/permissions` | PermissionsPage | Resource-level permissions. |
| `/audit` | AuditPage | Audit events. |

## UI components

| Component | Назначение |
| --- | --- |
| `AppLayout` | AntD Layout, sidebar, topbar, user menu. |
| `StatusTag` | Единый цвет/label статусов run/job. |
| `PipelineGraph` | Runtime graph stage/job; MVP может быть AntD Steps + Cards, затем React Flow. |
| `JobTemplateSelector` | Выбор template по `jobType`. |
| `DynamicJobParamsForm` | Генерация формы по `paramsTemplate` JSON. |
| `DependencyEditor` | Создание depends_on edges и condition. |
| `RunControlPanel` | Run/cancel/retry buttons с permission checks. |
| `LogViewer` | Tail logs, autoscroll, search, pause, download. |
| `ArtifactTable` | Artifacts с download-url action. |
| `ApprovalPanel` | Pending approvals с approve/reject. |
| `ExecutorHealthTable` | Executor instances, workerId, status, lastHeartbeat. |
| `ErrorBoundary` | UI fallback. |
| `ApiErrorAlert` | Единый показ `error.code/message/correlationId`. |

## Realtime behavior

1. На `PipelineRunPage` frontend вызывает `GET /api/v1/pipeline-runs/{id}` и `GET /graph`.
2. Открывает `EventSource('/api/v1/logs/stream?pipelineRunId=...')`.
3. На `job-event` обновляет cache run/graph/job execution.
4. На `job-log` добавляет log chunk в `LogViewer` для нужного `jobExecutionId`.
5. При разрыве SSE делает reconnect с backoff.
6. При долгой недоступности SSE включает polling `/pipeline-runs/{id}` и `/job-executions/{id}/logs?tail=...`.

## Ant Design forms

### Pipeline metadata

- `name` — required.
- `description`.
- `folderId`.
- `isActive`.

### Stage form

- `name` — required.
- `position`.
- `runPolicy`: `sequential | parallel`.

### Job form

- `name`.
- `stageId`.
- `position`.
- `jobType`.
- `templateId/templatePath`.
- `condition`: `on_success | on_failure | always`.
- `continueOnError`.
- `timeoutSeconds`.
- `maxAttempts`.
- `resourceLimits`.
- `workspacePolicy`.
- `params` dynamic JSON.

## Permission-aware UI

Frontend скрывает/disabled actions на основе effective permissions, но backend всегда выполняет окончательную проверку.

| Action | Permission |
| --- | --- |
| Просмотр pipeline/run/logs/artifacts | `view` |
| Редактирование pipeline | `edit` |
| Запуск pipeline | `run` |
| Отмена run/job | `cancel` |
| Approval deployment | `approve_deployment` |
| Управление secret refs | `manage_secrets` |
| Управление connections | `manage_connections` |
| Администрирование | `admin` |

## Frontend file list

```text
frontend/src/api/
├── client.ts
├── authApi.ts
├── foldersApi.ts
├── pipelinesApi.ts
├── jobTemplatesApi.ts
├── pipelineRunsApi.ts
├── jobExecutionsApi.ts
├── logsApi.ts
├── artifactsApi.ts
├── deploymentsApi.ts
├── connectionsApi.ts
├── permissionsApi.ts
└── sseClient.ts
```

```text
frontend/src/shared/types/
├── api.ts
├── pipeline.ts
├── run.ts
├── executorContracts.ts
├── artifact.ts
├── security.ts
└── pagination.ts
```

## MVP acceptance for frontend

- Можно создать pipeline со stage/job.
- Можно выбрать template и заполнить params.
- Можно запустить pipeline и видеть run graph.
- Статусы обновляются через SSE или polling fallback.
- Логи отображаются из master API, не напрямую из OpenSearch.
- Artifacts доступны через master API download-url.
- Protected deployment approval доступен только с permission.

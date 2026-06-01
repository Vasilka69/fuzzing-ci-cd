# 08. Security, RBAC, secrets, audit

## RBAC модель

Роли:

- `ADMIN` — системное администрирование.
- `DEVELOPER` — создание/редактирование/запуск pipeline.
- `VIEWER` — просмотр.
- `OPERATOR` — deployment operations и approvals.

Permissions:

```text
view, edit, run, cancel, approve_deployment,
manage_secrets, manage_connections, admin
```

Права могут быть выданы роли или пользователю на resource:

```text
system, folder, pipeline, environment, secret_ref, external_connection
```

Явный `deny` должен иметь приоритет над `allow`.

## Проверки master-service

| Операция | Проверка |
| --- | --- |
| Читать pipeline/run/logs/artifacts | `view` на pipeline/folder/system. |
| Редактировать pipeline/stage/job | `edit`. |
| Запуск pipeline | `run`. |
| Отмена run/job | `cancel`. |
| Использовать `secret_ref` в job params | `view`/специальный доступ к `secret_ref`. |
| Создать secret ref | `manage_secrets`. |
| Создать external connection | `manage_connections`. |
| Approve protected deployment | `approve_deployment` на environment или `OPERATOR`. |

## Секреты

Правила:

- Значения секретов не хранятся в PostgreSQL.
- В job message передаются только refs.
- Значения секретов не пишутся в Kafka, OpenSearch, logs, artifacts metadata, audit details.
- Executor получает секрет только через доверенный `SecretResolver`/Vault.
- Master может валидировать право использования `secret_ref`, но не раскрывает значение.

## SecretRef модель

Минимальные поля:

- `name`
- `provider`: `env | vault | file | kubernetes_secret | manual`
- `external_key`
- `scope`: `global | project | environment | user`
- `metadata`: mount/path/field/allowed_usage без секретного значения.

## OpenSearch redaction

Master не должен доверять executor logs. Для UI отображать логи как текст, не HTML. При диагностике не включать секреты в error summaries. На стороне executor-а должен быть redaction перед публикацией, но master/UI тоже не должны интерпретировать logs как safe markup.

## Audit events

Записывать audit для:

- login/logout/failure;
- create/update/delete pipeline/stage/job;
- run/cancel/retry;
- approve/reject deployment;
- create/update/delete permissions;
- create/update secret refs and external connections;
- failed authorization attempts;
- transport/poller errors, если влияют на статус runs.

## CORS/session

Для локальной разработки разрешить frontend origin из env. Для production ограничить allowlist. Токены хранить безопасно; MVP может использовать JWT bearer, но refresh/session policy нужно явно зафиксировать перед production.

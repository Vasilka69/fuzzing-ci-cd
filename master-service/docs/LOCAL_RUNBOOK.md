# Локальный Runbook: запуск и базовая проверка pipeline через UI

## Что поднимаем

- PostgreSQL
- Kafka (+ Zookeeper)
- OpenSearch
- `master-service`
- `frontend`

## Требования

- Docker Desktop
- Java 21
- Maven 3.9+
- Node.js 20+ и npm

## 1) Запустить инфраструктуру

Из корня проекта:

```powershell
docker compose up -d postgres zookeeper kafka opensearch
```

Проверка:

```powershell
docker compose ps
```

Если `opensearch` не стартует и в логах есть ошибка про `OPENSEARCH_INITIAL_ADMIN_PASSWORD`:

```powershell
# 1) Убедитесь, что в .env есть OPENSEARCH_INITIAL_ADMIN_PASSWORD
# 2) Пересоздайте контейнер OpenSearch
docker compose down
docker compose up -d opensearch
docker compose logs -f opensearch
```

Примечание: в `docker-compose.yml` security plugin для локальной разработки отключен (`DISABLE_SECURITY_PLUGIN=true`), пароль нужен для совместимости startup-скриптов OpenSearch 2.12+.

## 2) Запустить backend (`master-service`)

Вариант для базовой UI-проверки:

```powershell
mvn -pl master-service spring-boot:run
```

Вариант для e2e-демо c OpenSearch-поллером:

```powershell
$env:EXECUTOR_EVENTS_TRANSPORT="opensearch"
mvn -pl master-service spring-boot:run
```

Проверка health:

```powershell
curl http://localhost:8080/actuator/health
```

## 3) Запустить frontend

```powershell
cd frontend
npm install
npm run dev
```

UI будет на `http://localhost:5173`.

## 4) Базовая проверка pipeline через UI

1. Откройте `http://localhost:5173`.
2. Войдите любым логином/паролем (в dev-режиме пользователь создается автоматически).
3. Перейдите в `Pipelines` -> `New Pipeline`.
4. Откройте pipeline -> `Open Designer`.
5. Создайте:
   - stage `build` (position `1`);
   - job типа `script` в этом stage.
6. Вернитесь в детали pipeline и нажмите `Run Pipeline`.
7. Проверьте `Runs` и карточку запуска `/runs/{id}`:
   - создание `pipeline_run`;
   - создание `job_execution`;
   - отображение графа и статусов в UI.

Важно:

- Без реальных executor-событий запуск обычно остается в `queued/running`.
- Для полного happy-path (`success`) используйте demo-скрипт ниже.

## 5) Полный demo happy-path (опционально)

С включенным `EXECUTOR_EVENTS_TRANSPORT=opensearch`:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/e2e/demo-pipeline-opensearch.ps1
```

Скрипт:

- создаст pipeline/stages/jobs;
- запустит run;
- запишет synthetic executor events в OpenSearch;
- дождется финального статуса run=`success`.

## `/triggers` — используется ли сейчас

Да, endpoint'ы `/api/v1/triggers*` рабочие, но в текущем фронтенде отдельной страницы/клиента для них пока нет.

Они уже используются на backend для:

- webhook/manual API trigger flow;
- schedule trigger через `TriggerScheduler`.

То есть отсутствие вызовов из UI сейчас ожидаемо.

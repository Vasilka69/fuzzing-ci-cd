# CI/CD Master Service

Репозиторий содержит реализацию `master-service` для управления CI/CD pipeline:

- хранение и управление pipeline в PostgreSQL;
- запуск `pipeline_run` и `job_execution`;
- transactional outbox в Kafka;
- прием executor событий (Kafka/OpenSearch transport модели);
- REST API `/api/v1` и SSE поток событий.

## Быстрый старт

1. Скопируйте `.env.example` в `.env` и при необходимости скорректируйте значения.
2. Поднимите инфраструктуру:

```bash
docker compose up -d postgres zookeeper kafka opensearch
```

Если `opensearch` падает с ошибкой про `OPENSEARCH_INITIAL_ADMIN_PASSWORD`, проверьте что переменная задана в `.env` (см. `.env.example`), затем пересоздайте контейнер:

```bash
docker compose down
docker compose up -d opensearch
docker compose logs -f opensearch
```

3. Запустите backend:

```bash
mvn -pl master-service spring-boot:run
```

4. Запустите frontend (MVP UI):

```bash
cd frontend
npm install
npm run dev
```

Frontend по умолчанию доступен на `http://localhost:5173`, API base URL задается переменной `VITE_API_BASE_URL` (см. `.env.example` и `frontend/.env.example`).

## Локальный runbook

Пошаговый запуск всех ресурсов и базовая проверка pipeline через UI:
[docs/LOCAL_RUNBOOK.md](docs/LOCAL_RUNBOOK.md).

## E2E demo scenario

Для воспроизводимого e2e-сценария успешного pipeline (через OpenSearch transport) используйте:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/e2e/demo-pipeline-opensearch.ps1
```

Подробности и prerequisites: [scripts/e2e/README.md](scripts/e2e/README.md).

## Важные ограничения

- `master-service` — единственный writer в бизнес-таблицы PostgreSQL.
- Исполнители не пишут напрямую в БД master-сервиса.
- Большие логи и бинарные артефакты не хранятся в PostgreSQL.

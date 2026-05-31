# Предлагаемая структура репозитория

```text
.
├── AGENTS.md
├── README.md
├── PRD.md
├── TASKS.md
├── pom.xml
├── mvnw / mvnw.cmd
├── .env.example
├── docker-compose.yml
├── common/
│   ├── cicd-contracts/
│   ├── cicd-executor-core/
│   └── cicd-test-support/
├── demo/
│   └── mock-master-publisher/
├── services/
│   ├── vcs-service/
│   ├── storage-service/
│   ├── build-service/
│   ├── fuzzing-service/
│   ├── deploy-service/
│   └── script-service/
├── deploy/
│   ├── docker/
│   └── k8s/
└── docs/
    ├── prd/
    ├── tasks/
    ├── context/
    ├── architecture/
    ├── checklists/
    ├── templates/
    ├── prompts/
    └── k8s/
```

## Правила зависимостей

```text
services/* -> common/cicd-executor-core -> common/cicd-contracts
services/* -> common/cicd-test-support только в test scope
demo/mock-master-publisher -> common/cicd-contracts
common/* не зависит от services/*
executor-ы не зависят от master-service/ui
```

## Java package naming

Выбранный namespace проекта: `ru.diplom.cicd`.

```text
common/cicd-contracts       -> ru.diplom.cicd.contracts
common/cicd-executor-core   -> ru.diplom.cicd.executor.core
common/cicd-test-support    -> ru.diplom.cicd.test.support
demo/mock-master-publisher  -> ru.diplom.cicd.demo.mockmaster
services/vcs-service        -> ru.diplom.cicd.vcs
services/storage-service    -> ru.diplom.cicd.storage
services/build-service      -> ru.diplom.cicd.build
services/fuzzing-service    -> ru.diplom.cicd.fuzzing
services/deploy-service     -> ru.diplom.cicd.deploy
services/script-service     -> ru.diplom.cicd.script
```

## Maven команды

```bash
./mvnw -T 1C clean verify
./mvnw -pl demo/mock-master-publisher -am test
./mvnw -pl services/build-service -am test
./mvnw -pl services/fuzzing-service -am verify
```

## Docker/Kubernetes

Каждый сервис должен иметь:

```text
services/<service>/Dockerfile
services/<service>/src/main/resources/application.yml
deploy/k8s/<service>/deployment.yaml
deploy/k8s/<service>/configmap.yaml
```

Если сервис имеет HTTP API, добавить `service.yaml`. Если только Kafka consumer + actuator, `Service` нужен для health/metrics внутри кластера по решению команды.

# AGENTS.md — common modules

Работая в `common/*`, помни: эти модули используются всеми executor-сервисами. Любое изменение здесь потенциально меняет контракты всей системы.

## Релевантные документы

- `/AGENTS.md`
- `/docs/architecture/ADR-0002-executor-contracts.md`
- `/docs/context/DB_CONTEXT.md`
- `/docs/checklists/REVIEW_CHECKLIST.md`

## Правила

- Не ломать JSON contract без ADR.
- Не добавлять зависимость на конкретный service module.
- Не добавлять зависимость на master-service/ui.
- DTO должны сериализоваться в camelCase.
- Enums должны соответствовать корневому `AGENTS.md`.
- Любой publisher/runner должен быть покрыт unit tests.
- Secret redaction и sandbox validation считаются security-sensitive кодом; нужны negative tests.

## Проверки

```bash
./mvnw -pl common/cicd-contracts,common/cicd-executor-core,common/cicd-test-support -am test
```

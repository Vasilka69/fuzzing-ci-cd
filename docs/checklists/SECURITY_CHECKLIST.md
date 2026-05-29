# Security checklist для AI-generated изменений

## Секреты

- [ ] Секреты передаются только через `secret_ref`/`credentials_ref`.
- [ ] Нет секретов в логах, exceptions, events, artifacts, tests.
- [ ] Redaction покрывает token/password/private key/URL credentials.

## Sandbox

- [ ] `privileged=false`.
- [ ] Нет host network.
- [ ] Нет hostPath.
- [ ] Нет Docker socket mount.
- [ ] `allowPrivilegeEscalation=false`.
- [ ] `capabilities.drop=[ALL]`.
- [ ] `runAsNonRoot=true`.
- [ ] Network: `none` или egress allowlist.

## Команды и процессы

- [ ] Нет shell concatenation с пользовательским вводом.
- [ ] Entry point whitelist применяется.
- [ ] Timeout и grace shutdown есть.
- [ ] stdout/stderr имеют лимит и chunking.
- [ ] Exit codes мапятся в корректный error type.

## Dependencies

- [ ] Новая dependency обоснована.
- [ ] Проверена лицензия.
- [ ] Проверен maintenance/security risk.
- [ ] Нет hallucinated/unmaintained package.

## Agentic workflow

- [ ] Агент не выполнил непонятные команды.
- [ ] Агент не отключил проверки.
- [ ] Агент не изменил инструкции/CI/security policy без явной задачи.

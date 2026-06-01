insert into public.user_role (code, name, description) values
('ADMIN', 'Administrator', 'Полное администрирование системы'),
('DEVELOPER', 'Developer', 'Создание, настройка и запуск pipeline'),
('VIEWER', 'Viewer', 'Просмотр pipeline, запусков, логов и артефактов'),
('OPERATOR', 'Operator', 'Управление deployment-сценариями и окружениями')
on conflict (code) do update set
    name = excluded.name,
    description = excluded.description;


insert into public.permission_assignment (subject_type, role_id, resource_type, resource_id, permission, effect)
select 'role', r.id, v.resource_type, null::uuid, v.permission, 'allow'
from public.user_role r
join (values
    ('ADMIN', 'system', 'admin'),
    ('ADMIN', 'system', 'manage_secrets'),
    ('ADMIN', 'system', 'manage_connections'),
    ('DEVELOPER', 'system', 'view'),
    ('DEVELOPER', 'system', 'edit'),
    ('DEVELOPER', 'system', 'run'),
    ('DEVELOPER', 'system', 'cancel'),
    ('VIEWER', 'system', 'view'),
    ('OPERATOR', 'system', 'view'),
    ('OPERATOR', 'system', 'run'),
    ('OPERATOR', 'system', 'cancel'),
    ('OPERATOR', 'system', 'approve_deployment')
) as v(role_code, resource_type, permission) on v.role_code = r.code
on conflict do nothing;

insert into public.deployment_environment (name, description, config) values
('development', 'Среда разработки', '{"protected":false}'::jsonb),
('testing', 'Тестовая среда', '{"protected":false}'::jsonb),
('production', 'Эксплуатационная среда', '{"protected":true,"requires_operator_role":true,"requires_approval":true,"max_parallel_deployments":1,"retention_policy":"release"}'::jsonb)
on conflict (name) do update set
    description = excluded.description,
    config = excluded.config,
    updated_at = now();

insert into public.job_template (path, job_type, display_name, params_template, default_params) values
('vcs/git', 'vcs', 'Git checkout',
 '{
    "vcs_type":"git",
    "repository_url":"",
    "ref":"main",
    "ref_type":"branch",
    "checkout_depth":1,
    "submodules":false,
    "credentials_ref":"",
    "snapshot_policy":{"format":"tar.gz","include_git_metadata":false}
  }'::jsonb,
 '{}'::jsonb),
('vcs/mercurial', 'vcs', 'Mercurial checkout',
 '{
    "vcs_type":"mercurial",
    "repository_url":"",
    "ref":"default",
    "ref_type":"branch",
    "credentials_ref":"",
    "snapshot_policy":{"format":"tar.gz"}
  }'::jsonb,
 '{}'::jsonb),
('storage/source-snapshot', 'storage', 'Prepare source snapshot',
 '{
    "operation":"save",
    "source_uri":"",
    "destination_policy":"internal_storage",
    "retention":"default",
    "verify_checksum":true
  }'::jsonb,
 '{}'::jsonb),
('storage/promote-artifact', 'storage', 'Promote artifact',
 '{
    "operation":"promote",
    "source_uri":"",
    "destination":"repository_manager",
    "retention":"release",
    "verify_checksum":true
  }'::jsonb,
 '{}'::jsonb),
('storage/cleanup', 'storage', 'Cleanup artifacts',
 '{
    "operation":"cleanup",
    "target_uri":"",
    "retention":"expired_only",
    "dry_run":true
  }'::jsonb,
 '{}'::jsonb),
('build/maven', 'build', 'Maven build',
 '{
    "build_tool":"maven",
    "entrypoint":"mvn",
    "source_snapshot_uri":"",
    "working_directory":".",
    "args":["clean","package"],
    "jdk_version":"17",
    "expected_artifacts":["target/*.jar"],
    "toolchain":{},
    "environment":{},
    "allowed_network":true,
    "sandbox_policy":{"runtime":"docker","run_as_non_root":true,"privileged":false,"read_only_root_filesystem":true,"drop_capabilities":["ALL"],"allow_privilege_escalation":false,"network":{"mode":"egress_allowlist","egress_allowlist":["repository_manager"]},"mounts":{"workspace":"rw","inputs":"ro","docker_socket":false}}
  }'::jsonb,
 '{}'::jsonb),
('build/gradle', 'build', 'Gradle build',
 '{
    "build_tool":"gradle",
    "entrypoint":"gradle",
    "source_snapshot_uri":"",
    "working_directory":".",
    "args":["build"],
    "jdk_version":"17",
    "expected_artifacts":["build/libs/*"],
    "toolchain":{},
    "environment":{},
    "allowed_network":true,
    "sandbox_policy":{"runtime":"docker","run_as_non_root":true,"privileged":false,"read_only_root_filesystem":true,"drop_capabilities":["ALL"],"allow_privilege_escalation":false,"network":{"mode":"egress_allowlist","egress_allowlist":["repository_manager"]},"mounts":{"workspace":"rw","inputs":"ro","docker_socket":false}}
  }'::jsonb,
 '{}'::jsonb),
('build/javac', 'build', 'Javac build',
 '{
    "build_tool":"javac",
    "entrypoint":"javac",
    "source_snapshot_uri":"",
    "working_directory":".",
    "args":[],
    "expected_artifacts":[],
    "toolchain":{},
    "environment":{},
    "allowed_network":false,
    "sandbox_policy":{"runtime":"docker","run_as_non_root":true,"privileged":false,"read_only_root_filesystem":true,"drop_capabilities":["ALL"],"allow_privilege_escalation":false,"network":{"mode":"none","egress_allowlist":[]},"mounts":{"workspace":"rw","inputs":"ro","docker_socket":false}}
  }'::jsonb,
 '{}'::jsonb),
('build/gcc', 'build', 'GCC build',
 '{
    "build_tool":"gcc",
    "entrypoint":"gcc",
    "source_snapshot_uri":"",
    "working_directory":".",
    "args":[],
    "expected_artifacts":[],
    "toolchain":{},
    "environment":{},
    "allowed_network":false,
    "sandbox_policy":{"runtime":"docker","run_as_non_root":true,"privileged":false,"read_only_root_filesystem":true,"drop_capabilities":["ALL"],"allow_privilege_escalation":false,"network":{"mode":"none","egress_allowlist":[]},"mounts":{"workspace":"rw","inputs":"ro","docker_socket":false}}
  }'::jsonb,
 '{}'::jsonb),
('fuzzing/afl-llm', 'fuzzing', 'AFL++ LLM-assisted fuzzing',
 '{
    "target_artifact_uri":"",
    "target_command":"",
    "seed_corpus_uri":"",
    "dictionary_uri":"",
    "mode":"fake",
    "budget_seconds":3600,
    "memory_limit_mb":2048,
    "prompt_uri":"",
    "policy":{
      "fail_on_crash":true,
      "fail_on_hang":false,
      "max_crashes":1,
      "min_execs":0,
      "save_corpus":true
    },
    "llm":{
      "endpoint_ref":"",
      "model":"",
      "temperature":0.7,
      "timeout_seconds":30,
      "max_retries":2,
      "fallback_mode":"fake"
    },
    "sandbox_policy":{"runtime":"docker","run_as_non_root":true,"privileged":false,"read_only_root_filesystem":true,"drop_capabilities":["ALL"],"allow_privilege_escalation":false,"network":{"mode":"none","egress_allowlist":[]},"mounts":{"workspace":"rw","inputs":"ro","docker_socket":false},"limits":{"pids":512,"stdout_bytes":52428800}}
  }'::jsonb,
 '{}'::jsonb),
('deploy/ssh-bash', 'deploy', 'SSH Bash deployment',
 '{
    "deployment_type":"ssh_bash",
    "release_id":"",
    "artifact_uri":"",
    "environment":"testing",
    "target":{"host":"","port":22,"user":"","credentials_ref":""},
    "copy":{"destination_path":"","backup_existing":true},
    "commands":[],
    "healthcheck":{},
    "rollback":{"enabled":true,"strategy":"restore_previous_artifact"},
    "idempotency":{"check_existing_release":true}
  }'::jsonb,
 '{}'::jsonb),
('deploy/windows-cmd', 'deploy', 'Windows cmd deployment',
 '{
    "deployment_type":"windows_cmd",
    "release_id":"",
    "artifact_uri":"",
    "environment":"testing",
    "target":{"host":"","port":22,"user":"","credentials_ref":""},
    "copy":{"destination_path":"","backup_existing":true},
    "commands":[],
    "healthcheck":{},
    "rollback":{"enabled":true},
    "idempotency":{"check_existing_release":true}
  }'::jsonb,
 '{}'::jsonb),
('deploy/file-copy', 'deploy', 'File copy deployment',
 '{
    "deployment_type":"file_copy",
    "release_id":"",
    "artifact_uri":"",
    "environment":"testing",
    "target":{"connection_ref":"","destination_path":""},
    "verify_checksum":true,
    "rollback":{"enabled":false}
  }'::jsonb,
 '{}'::jsonb),
('deploy/docker', 'deploy', 'Docker deployment',
 '{
    "deployment_type":"docker",
    "release_id":"",
    "artifact_uri":"",
    "environment":"testing",
    "target":{"host":"","user":"","credentials_ref":""},
    "image":"",
    "container_name":"",
    "ports":[],
    "environment_vars":{},
    "healthcheck":{},
    "rollback":{"enabled":true},
    "idempotency":{"check_existing_release":true}
  }'::jsonb,
 '{}'::jsonb),
('deploy/docker-compose', 'deploy', 'Docker Compose deployment',
 '{
    "deployment_type":"docker_compose",
    "release_id":"",
    "artifact_uri":"",
    "environment":"testing",
    "target":{"host":"","user":"","credentials_ref":""},
    "compose_file_path":"",
    "commands":[],
    "healthcheck":{},
    "rollback":{"enabled":true},
    "idempotency":{"check_existing_release":true}
  }'::jsonb,
 '{}'::jsonb),
('deploy/systemd', 'deploy', 'systemd deployment',
 '{
    "deployment_type":"systemd",
    "release_id":"",
    "artifact_uri":"",
    "environment":"testing",
    "target":{"host":"","user":"","credentials_ref":""},
    "service_name":"",
    "destination_path":"",
    "healthcheck":{},
    "rollback":{"enabled":true,"strategy":"restore_previous_artifact"},
    "idempotency":{"check_existing_release":true}
  }'::jsonb,
 '{}'::jsonb),
('script/bash', 'script', 'Bash script',
 '{
    "script_type":"bash",
    "script":"",
    "script_artifact_uri":"",
    "working_directory":".",
    "input_artifacts":[],
    "environment":{},
    "allowed_network":false,
    "expected_outputs":[],
    "runtime_image":"",
    "sandbox_policy":{"runtime":"docker","run_as_non_root":true,"privileged":false,"read_only_root_filesystem":true,"drop_capabilities":["ALL"],"allow_privilege_escalation":false,"network":{"mode":"none","egress_allowlist":[]},"mounts":{"workspace":"rw","inputs":"ro","docker_socket":false},"limits":{"pids":256,"stdout_bytes":10485760}}
  }'::jsonb,
 '{}'::jsonb),
('script/cmd', 'script', 'cmd script',
 '{
    "script_type":"cmd",
    "script":"",
    "script_artifact_uri":"",
    "working_directory":".",
    "input_artifacts":[],
    "environment":{},
    "allowed_network":false,
    "expected_outputs":[],
    "runtime_image":"",
    "sandbox_policy":{"runtime":"docker","run_as_non_root":true,"privileged":false,"read_only_root_filesystem":true,"drop_capabilities":["ALL"],"allow_privilege_escalation":false,"network":{"mode":"none","egress_allowlist":[]},"mounts":{"workspace":"rw","inputs":"ro","docker_socket":false},"limits":{"pids":256,"stdout_bytes":10485760}}
  }'::jsonb,
 '{}'::jsonb)
on conflict (path) do update set
    job_type = excluded.job_type,
    display_name = excluded.display_name,
    params_template = excluded.params_template,
    default_params = excluded.default_params,
    is_active = true,
    updated_at = now();



insert into public.secret_ref (name, provider, external_key, description, scope, metadata) values
('default-vcs-token', 'vault', 'kv/data/cicd/default/vcs-token', 'Пример ссылки на VCS token в Vault', 'project', '{"vault_mount":"kv","vault_path":"cicd/default/vcs-token","vault_field":"token","allowed_usage":["vcs"]}'::jsonb),
('default-deploy-key', 'vault', 'kv/data/cicd/default/deploy-key', 'Пример ссылки на deployment private key в Vault', 'environment', '{"vault_mount":"kv","vault_path":"cicd/default/deploy-key","vault_field":"private_key","allowed_usage":["deploy"]}'::jsonb),
('default-webhook-secret', 'vault', 'kv/data/cicd/default/webhook-secret', 'Пример секрета подписи VCS webhook в Vault', 'project', '{"vault_mount":"kv","vault_path":"cicd/default/webhook-secret","vault_field":"secret","allowed_usage":["webhook"]}'::jsonb)
on conflict (name) do update set
    provider = excluded.provider,
    external_key = excluded.external_key,
    description = excluded.description,
    scope = excluded.scope,
    metadata = excluded.metadata,
    updated_at = now();

insert into public.external_connection (name, connection_type, url, credentials_ref, config) values
('default-internal-storage', 'internal_storage', 'storage://default', null, '{"retention_days":30,"api_layer":true,"physical_backend":"filesystem_or_s3_compatible"}'::jsonb),
('default-repository-manager', 'repository_manager', null, null, '{"description":"Заполнить URL менеджера репозиториев при развертывании"}'::jsonb),
('default-llm-endpoint', 'llm_endpoint', null, null, '{"mode":"openai_compatible","enabled":false,"local_allowed":true}'::jsonb),
('default-vault', 'secret_provider', 'http://vault:8200', null, '{"provider":"vault","auth_methods":["approle","kubernetes"],"enabled":false}'::jsonb)
on conflict (name) do update set
    connection_type = excluded.connection_type,
    url = excluded.url,
    credentials_ref = excluded.credentials_ref,
    config = excluded.config,
    is_active = true,
    updated_at = now();

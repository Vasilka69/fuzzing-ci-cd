create extension if not exists "uuid-ossp";

create table if not exists public.app_user
(
    id            uuid default uuid_generate_v4() not null
        constraint app_user_pk primary key,
    login         text                            not null
        constraint app_user_login_uq unique,
    email         text
        constraint app_user_email_uq unique,
    password_hash text                            not null,
    display_name  text,
    is_active     boolean default true            not null,
    created_at    timestamptz default now()       not null,
    updated_at    timestamptz default now()       not null
);

create table if not exists public.user_role
(
    id          uuid default uuid_generate_v4() not null
        constraint user_role_pk primary key,
    code        text                            not null
        constraint user_role_code_uq unique,
    name        text                            not null,
    description text
);

create table if not exists public.user_role_assignment
(
    id         uuid default uuid_generate_v4() not null
        constraint user_role_assignment_pk primary key,
    user_id    uuid                            not null
        constraint user_role_assignment_user_id_fk references public.app_user(id) on delete cascade,
    role_id    uuid                            not null
        constraint user_role_assignment_role_id_fk references public.user_role(id) on delete cascade,
    created_at timestamptz default now()       not null,
    constraint user_role_assignment_uq unique (user_id, role_id)
);


create table if not exists public.permission_assignment
(
    id            uuid default uuid_generate_v4() not null
        constraint permission_assignment_pk primary key,
    subject_type  text                            not null,
    user_id       uuid
        constraint permission_assignment_user_id_fk references public.app_user(id) on delete cascade,
    role_id       uuid
        constraint permission_assignment_role_id_fk references public.user_role(id) on delete cascade,
    resource_type text                            not null,
    resource_id   uuid,
    permission    text                            not null,
    effect        text default 'allow'            not null,
    created_at    timestamptz default now()       not null,
    constraint permission_assignment_subject_type_check check (subject_type in ('user', 'role')),
    constraint permission_assignment_subject_check check ((subject_type = 'user' and user_id is not null and role_id is null) or (subject_type = 'role' and role_id is not null and user_id is null)),
    constraint permission_assignment_resource_type_check check (resource_type in ('system', 'folder', 'pipeline', 'environment', 'secret_ref', 'external_connection')),
    constraint permission_assignment_permission_check check (permission in ('view', 'edit', 'run', 'cancel', 'approve_deployment', 'manage_secrets', 'manage_connections', 'admin')),
    constraint permission_assignment_effect_check check (effect in ('allow', 'deny')),
    constraint permission_assignment_resource_check check ((resource_type = 'system' and resource_id is null) or (resource_type <> 'system' and resource_id is not null))
);

create table if not exists public.folder
(
    id          uuid default uuid_generate_v4() not null
        constraint folder_pk primary key,
    name        text                            not null,
    description text,
    parent_id   uuid
        constraint folder_parent_id_fk references public.folder(id) on delete set null,
    created_at  timestamptz default now()       not null,
    updated_at  timestamptz default now()       not null
);

create table if not exists public.pipeline
(
    id          uuid default uuid_generate_v4() not null
        constraint pipeline_pk primary key,
    folder_id   uuid
        constraint pipeline_folder_id_fk references public.folder(id) on delete set null,
    name        text                            not null,
    description text,
    is_active   boolean default true            not null,
    created_by  uuid
        constraint pipeline_created_by_fk references public.app_user(id) on delete set null,
    created_at  timestamptz default now()       not null,
    updated_at  timestamptz default now()       not null
);

create table if not exists public.stage
(
    id          uuid default uuid_generate_v4() not null
        constraint stage_pk primary key,
    pipeline_id uuid                            not null
        constraint stage_pipeline_id_fk references public.pipeline(id) on delete cascade,
    position    integer                         not null,
    name        text                            not null,
    description text,
    run_policy  text default 'sequential'       not null,
    created_at  timestamptz default now()       not null,
    updated_at  timestamptz default now()       not null,
    constraint unique_pipeline_stage_position unique (pipeline_id, position),
    constraint stage_position_positive check (position > 0),
    constraint stage_run_policy_check check (run_policy in ('sequential', 'parallel'))
);

create table if not exists public.job_template
(
    id              uuid default uuid_generate_v4() not null
        constraint job_template_pk primary key,
    path            text                            not null
        constraint job_template_path_uq unique,
    job_type        text                            not null,
    display_name    text                            not null,
    params_template jsonb default '{}'::jsonb       not null,
    default_params  jsonb default '{}'::jsonb       not null,
    is_active       boolean default true            not null,
    created_at      timestamptz default now()       not null,
    updated_at      timestamptz default now()       not null,
    constraint job_template_type_check check (job_type in ('vcs', 'storage', 'build', 'fuzzing', 'deploy', 'script'))
);

create table if not exists public.job
(
    id                uuid default uuid_generate_v4() not null
        constraint job_pk primary key,
    stage_id          uuid                            not null
        constraint job_stage_id_fk references public.stage(id) on delete cascade,
    job_template_id   uuid
        constraint job_template_id_fk references public.job_template(id) on delete set null,
    position          integer                         not null,
    name              text                            not null,
    job_type          text                            not null,
    params            jsonb default '{}'::jsonb       not null,
    script            text,
    is_script_primary boolean default false           not null,
    condition         text default 'on_success'       not null,
    timeout_seconds   integer default 3600            not null,
    max_attempts      integer default 1               not null,
    resource_limits   jsonb default '{}'::jsonb       not null,
    sandbox_policy    jsonb default '{"runtime":"docker","run_as_non_root":true,"privileged":false,"read_only_root_filesystem":true,"drop_capabilities":["ALL"],"allow_privilege_escalation":false,"network":{"mode":"none","egress_allowlist":[]},"mounts":{"workspace":"rw","inputs":"ro","docker_socket":false},"limits":{}}'::jsonb not null,
    continue_on_error boolean default false           not null,
    is_active         boolean default true            not null,
    created_at        timestamptz default now()       not null,
    updated_at        timestamptz default now()       not null,
    constraint unique_stage_job_position unique (stage_id, position),
    constraint job_position_positive check (position > 0),
    constraint job_type_check check (job_type in ('vcs', 'storage', 'build', 'fuzzing', 'deploy', 'script')),
    constraint job_condition_check check (condition in ('on_success', 'on_failure', 'always')),
    constraint job_max_attempts_positive check (max_attempts > 0),
    constraint job_timeout_positive check (timeout_seconds > 0)
);

create table if not exists public.job_dependency
(
    id                uuid default uuid_generate_v4() not null
        constraint job_dependency_pk primary key,
    job_id            uuid                            not null
        constraint job_dependency_job_id_fk references public.job(id) on delete cascade,
    depends_on_job_id uuid                            not null
        constraint job_dependency_depends_on_job_id_fk references public.job(id) on delete cascade,
    condition         text default 'on_success'       not null,
    created_at        timestamptz default now()       not null,
    constraint job_dependency_uq unique (job_id, depends_on_job_id),
    constraint job_dependency_no_self_check check (job_id <> depends_on_job_id),
    constraint job_dependency_condition_check check (condition in ('on_success', 'on_failure', 'always'))
);

create table if not exists public.job_params
(
    id              uuid default uuid_generate_v4() not null
        constraint job_params_pk primary key,
    job_id          uuid                            not null
        constraint job_params_job_id_fk references public.job(id) on delete cascade,
    job_template_id uuid
        constraint job_params_template_id_fk references public.job_template(id) on delete set null,
    params          jsonb default '{}'::jsonb       not null,
    created_at      timestamptz default now()       not null
);

create table if not exists public.trigger
(
    id           uuid default uuid_generate_v4() not null
        constraint trigger_pk primary key,
    pipeline_id  uuid                            not null
        constraint trigger_pipeline_id_fk references public.pipeline(id) on delete cascade,
    name         text                            not null,
    trigger_type text                            not null,
    config       jsonb default '{}'::jsonb       not null,
    is_active    boolean default true            not null,
    created_at   timestamptz default now()       not null,
    updated_at   timestamptz default now()       not null,
    constraint trigger_type_check check (trigger_type in ('manual', 'vcs_push', 'schedule', 'api')),
    constraint trigger_pipeline_name_uq unique (pipeline_id, name)
);

create table if not exists public.trigger_event
(
    id                      uuid default uuid_generate_v4() not null
        constraint trigger_event_pk primary key,
    trigger_id              uuid
        constraint trigger_event_trigger_id_fk references public.trigger(id) on delete set null,
    pipeline_id             uuid
        constraint trigger_event_pipeline_id_fk references public.pipeline(id) on delete set null,
    pipeline_run_id         uuid,
    event_source            text                            not null,
    external_event_id       text,
    idempotency_key         text,
    ref                     text,
    commit_hash             text,
    payload_hash            text,
    payload                 jsonb default '{}'::jsonb       not null,
    status                  text default 'received'         not null,
    error_message           text,
    received_at             timestamptz default now()       not null,
    processed_at            timestamptz,
    constraint trigger_event_status_check check (status in ('received', 'accepted', 'ignored', 'failed')),
    constraint trigger_event_external_uq unique (event_source, external_event_id),
    constraint trigger_event_idempotency_uq unique (event_source, idempotency_key)
);

create table if not exists public.pipeline_run
(
    id                  uuid default uuid_generate_v4() not null
        constraint pipeline_run_pk primary key,
    pipeline_id         uuid                            not null
        constraint pipeline_run_pipeline_id_fk references public.pipeline(id) on delete cascade,
    trigger_id          uuid
        constraint pipeline_run_trigger_id_fk references public.trigger(id) on delete set null,
    status              text default 'queued'           not null,
    correlation_id      uuid default uuid_generate_v4() not null,
    started_by          uuid
        constraint pipeline_run_started_by_fk references public.app_user(id) on delete set null,
    triggered_by_type   text default 'user'             not null,
    trigger_payload     jsonb default '{}'::jsonb       not null,
    started_at          timestamptz default now()       not null,
    finished_at         timestamptz,
    summary             text,
    constraint pipeline_run_status_check check (status in ('queued', 'running', 'waiting_approval', 'success', 'failed', 'canceling', 'canceled', 'timeout')),
    constraint pipeline_run_triggered_by_type_check check (triggered_by_type in ('user', 'trigger', 'api', 'system'))
);



do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'trigger_event_pipeline_run_id_fk'
          and conrelid = 'public.trigger_event'::regclass
    ) then
        alter table public.trigger_event
            add constraint trigger_event_pipeline_run_id_fk
            foreign key (pipeline_run_id) references public.pipeline_run(id) on delete set null;
    end if;
end $$;

create table if not exists public.job_execution
(
    id                 uuid default uuid_generate_v4() not null
        constraint job_execution_pk primary key,
    pipeline_run_id    uuid                            not null
        constraint job_execution_pipeline_run_id_fk references public.pipeline_run(id) on delete cascade,
    job_id             uuid                            not null
        constraint job_execution_job_id_fk references public.job(id) on delete cascade,
    attempt            integer default 1               not null,
    status             text default 'queued'           not null,
    worker_id          text,
    started_at         timestamptz,
    finished_at        timestamptz,
    duration_ms        bigint,
    logs_uri           text,
    result             jsonb default '{}'::jsonb       not null,
    metrics            jsonb default '{}'::jsonb       not null,
    error_type         text,
    error_code         text,
    error_message      text,
    error_retryable    boolean,
    artifact_manifest  jsonb default '[]'::jsonb       not null,
    created_at         timestamptz default now()       not null,
    updated_at         timestamptz default now()       not null,
    constraint unique_job_execution_attempt unique (pipeline_run_id, job_id, attempt),
    constraint job_execution_attempt_positive check (attempt > 0),
    constraint job_execution_status_check check (status in ('queued', 'running', 'waiting_approval', 'success', 'failed', 'timeout', 'canceling', 'canceled', 'retrying', 'skipped')),
    constraint job_execution_error_type_check check (error_type is null or error_type in ('validation_error', 'user_code_error', 'infrastructure_error', 'timeout', 'canceled', 'security_error', 'fuzzing_crash_found', 'cancel_failed', 'unknown'))
);



create table if not exists public.cancellation_request
(
    id                  uuid default uuid_generate_v4() not null
        constraint cancellation_request_pk primary key,
    pipeline_run_id     uuid
        constraint cancellation_request_pipeline_run_id_fk references public.pipeline_run(id) on delete cascade,
    job_execution_id    uuid
        constraint cancellation_request_job_execution_id_fk references public.job_execution(id) on delete cascade,
    requested_by        uuid
        constraint cancellation_request_requested_by_fk references public.app_user(id) on delete set null,
    reason              text default 'user_requested'  not null,
    status              text default 'requested'       not null,
    grace_period_seconds integer default 30            not null,
    requested_at        timestamptz default now()      not null,
    delivered_at        timestamptz,
    completed_at        timestamptz,
    error_message       text,
    constraint cancellation_request_target_check check (pipeline_run_id is not null or job_execution_id is not null),
    constraint cancellation_request_status_check check (status in ('requested', 'delivered', 'completed', 'failed', 'ignored')),
    constraint cancellation_request_grace_positive check (grace_period_seconds > 0)
);

create table if not exists public.artifact
(
    id               uuid default uuid_generate_v4() not null
        constraint artifact_pk primary key,
    pipeline_run_id  uuid
        constraint artifact_pipeline_run_id_fk references public.pipeline_run(id) on delete set null,
    job_execution_id uuid
        constraint artifact_job_execution_id_fk references public.job_execution(id) on delete set null,
    artifact_type    text                            not null,
    name             text                            not null,
    uri              text                            not null,
    sha256           text,
    size_bytes       bigint,
    content_type     text,
    retention_policy text default 'default'           not null,
    metadata         jsonb default '{}'::jsonb       not null,
    created_at       timestamptz default now()       not null,
    expires_at       timestamptz,
    status           text default 'available'          not null,
    checksum_verified boolean default false            not null,
    constraint artifact_status_check check (status in ('uploading', 'available', 'expired', 'deleted', 'failed')),
    constraint artifact_size_non_negative check (size_bytes is null or size_bytes >= 0),
    constraint artifact_type_check check (artifact_type in ('source_snapshot', 'build_artifact', 'fuzzing_report', 'crash_case', 'hang_case', 'corpus', 'log', 'deployment_manifest', 'script_output', 'release_package', 'other'))
);



create table if not exists public.storage_object
(
    id                  uuid default uuid_generate_v4() not null
        constraint storage_object_pk primary key,
    artifact_id         uuid
        constraint storage_object_artifact_id_fk references public.artifact(id) on delete set null,
    pipeline_run_id     uuid
        constraint storage_object_pipeline_run_id_fk references public.pipeline_run(id) on delete set null,
    job_execution_id    uuid
        constraint storage_object_job_execution_id_fk references public.job_execution(id) on delete set null,
    storage_uri         text                            not null
        constraint storage_object_uri_uq unique,
    backend             text                            not null,
    bucket              text,
    object_key          text,
    upload_status       text default 'initiated'        not null,
    size_bytes          bigint,
    sha256              text,
    content_type        text,
    retention_policy    text default 'default'          not null,
    expires_at          timestamptz,
    metadata            jsonb default '{}'::jsonb       not null,
    created_at          timestamptz default now()       not null,
    completed_at        timestamptz,
    deleted_at          timestamptz,
    constraint storage_object_status_check check (upload_status in ('initiated', 'uploading', 'completed', 'failed', 'deleted')),
    constraint storage_object_size_non_negative check (size_bytes is null or size_bytes >= 0),
    constraint storage_object_retention_check check (retention_policy in ('temporary', 'default', 'diagnostic', 'release', 'permanent'))
);

create table if not exists public.secret_ref
(
    id           uuid default uuid_generate_v4() not null
        constraint secret_ref_pk primary key,
    name         text                            not null
        constraint secret_ref_name_uq unique,
    provider     text                            not null,
    external_key text                            not null,
    description  text,
    scope        text default 'project'           not null,
    metadata     jsonb default '{}'::jsonb        not null,
    created_by   uuid
        constraint secret_ref_created_by_fk references public.app_user(id) on delete set null,
    created_at   timestamptz default now()       not null,
    updated_at   timestamptz default now()       not null,
    constraint secret_ref_provider_check check (provider in ('env', 'vault', 'file', 'kubernetes_secret', 'manual')),
    constraint secret_ref_scope_check check (scope in ('global', 'project', 'environment', 'user'))
);

create table if not exists public.external_connection
(
    id              uuid default uuid_generate_v4() not null
        constraint external_connection_pk primary key,
    name            text                            not null
        constraint external_connection_name_uq unique,
    connection_type text                            not null,
    url             text,
    credentials_ref text,
    secret_ref_id   uuid
        constraint external_connection_secret_ref_id_fk references public.secret_ref(id) on delete set null,
    config          jsonb default '{}'::jsonb       not null,
    is_active       boolean default true            not null,
    created_at      timestamptz default now()       not null,
    updated_at      timestamptz default now()       not null,
    constraint external_connection_type_check check (connection_type in ('vcs', 'repository_manager', 'deployment_target', 'docker_registry', 'llm_endpoint', 'internal_storage', 'secret_provider'))
);

create table if not exists public.deployment_environment
(
    id          uuid default uuid_generate_v4() not null
        constraint deployment_environment_pk primary key,
    name        text                            not null
        constraint deployment_environment_name_uq unique,
    description text,
    config      jsonb default '{}'::jsonb       not null,
    is_active   boolean default true            not null,
    created_at  timestamptz default now()       not null,
    updated_at  timestamptz default now()       not null
);

create table if not exists public.deployment_release
(
    id                    uuid default uuid_generate_v4() not null
        constraint deployment_release_pk primary key,
    release_id            text                            not null
        constraint deployment_release_release_id_uq unique,
    pipeline_run_id        uuid
        constraint deployment_release_pipeline_run_id_fk references public.pipeline_run(id) on delete set null,
    job_execution_id       uuid
        constraint deployment_release_job_execution_id_fk references public.job_execution(id) on delete set null,
    environment_id         uuid
        constraint deployment_release_environment_id_fk references public.deployment_environment(id) on delete set null,
    target_connection_id   uuid
        constraint deployment_release_target_connection_id_fk references public.external_connection(id) on delete set null,
    artifact_uri           text                            not null,
    artifact_sha256        text,
    deployment_type        text                            not null,
    status                 text default 'created'          not null,
    manifest_uri           text,
    rollback_release_id    text,
    healthcheck_result     jsonb default '{}'::jsonb       not null,
    metadata               jsonb default '{}'::jsonb       not null,
    created_at             timestamptz default now()       not null,
    deployed_at            timestamptz,
    constraint deployment_release_type_check check (deployment_type in ('ssh_bash', 'windows_cmd', 'file_copy', 'docker', 'docker_compose', 'systemd', 'custom_script')),
    constraint deployment_release_status_check check (status in ('created', 'running', 'success', 'failed', 'rolled_back', 'rollback_failed'))
);



create table if not exists public.deployment_approval
(
    id              uuid default uuid_generate_v4() not null
        constraint deployment_approval_pk primary key,
    pipeline_run_id uuid
        constraint deployment_approval_pipeline_run_id_fk references public.pipeline_run(id) on delete cascade,
    job_execution_id uuid
        constraint deployment_approval_job_execution_id_fk references public.job_execution(id) on delete cascade,
    environment_id  uuid                            not null
        constraint deployment_approval_environment_id_fk references public.deployment_environment(id) on delete cascade,
    requested_by    uuid
        constraint deployment_approval_requested_by_fk references public.app_user(id) on delete set null,
    approved_by     uuid
        constraint deployment_approval_approved_by_fk references public.app_user(id) on delete set null,
    status          text default 'pending'          not null,
    reason          text,
    created_at      timestamptz default now()       not null,
    decided_at      timestamptz,
    constraint deployment_approval_status_check check (status in ('pending', 'approved', 'rejected', 'expired', 'canceled'))
);

create table if not exists public.executor_heartbeat
(
    id           uuid default uuid_generate_v4() not null
        constraint executor_heartbeat_pk primary key,
    worker_id    text                            not null
        constraint executor_heartbeat_worker_id_uq unique,
    service_type text                            not null,
    status       text default 'alive'            not null,
    capacity     jsonb default '{}'::jsonb       not null,
    last_seen_at timestamptz default now()       not null,
    constraint executor_heartbeat_service_type_check check (service_type in ('vcs', 'storage', 'build', 'fuzzing', 'deploy', 'script')),
    constraint executor_heartbeat_status_check check (status in ('alive', 'draining', 'unavailable'))
);

create table if not exists public.audit_event
(
    id            uuid default uuid_generate_v4() not null
        constraint audit_event_pk primary key,
    actor_user_id uuid
        constraint audit_event_actor_user_id_fk references public.app_user(id) on delete set null,
    event_type    text                            not null,
    entity_type   text,
    entity_id     uuid,
    payload       jsonb default '{}'::jsonb       not null,
    created_at    timestamptz default now()       not null
);

create table if not exists public.outbox_event
(
    id             uuid default uuid_generate_v4() not null
        constraint outbox_event_pk primary key,
    aggregate_type text                            not null,
    aggregate_id   uuid,
    event_type      text                            not null,
    schema_version  integer default 1               not null,
    topic           text                            not null,
    message_key     text                            not null,
    payload         jsonb                           not null,
    headers         jsonb default '{}'::jsonb       not null,
    status          text default 'pending'          not null,
    attempts        integer default 0               not null,
    last_error      text,
    created_at      timestamptz default now()       not null,
    published_at    timestamptz,
    locked_at       timestamptz,
    constraint outbox_event_status_check check (status in ('pending', 'published', 'failed')),
    constraint outbox_event_attempts_non_negative check (attempts >= 0),
    constraint outbox_event_schema_version_positive check (schema_version > 0)
);

create table if not exists public.inbox_event
(
    id                 uuid default uuid_generate_v4() not null
        constraint inbox_event_pk primary key,
    message_id         uuid,
    consumer_name      text                            not null,
    topic              text                            not null,
    message_key        text,
    job_execution_id   uuid
        constraint inbox_event_job_execution_id_fk references public.job_execution(id) on delete set null,
    event_source       text default 'kafka'            not null,
    source_document_id text,
    event_type         text                            not null,
    schema_version     integer default 1               not null,
    payload_hash       text,
    processed_at       timestamptz default now()       not null,
    status             text default 'processed'        not null,
    error_message      text,
    constraint inbox_event_message_consumer_uq unique (message_id, consumer_name),
    constraint inbox_event_source_check check (event_source in ('kafka', 'opensearch', 'api', 'system')),
    constraint inbox_event_identity_check check (message_id is not null or source_document_id is not null),
    constraint inbox_event_status_check check (status in ('processed', 'failed', 'ignored')),
    constraint inbox_event_schema_version_positive check (schema_version > 0)
);


create table if not exists public.executor_event_cursor
(
    id                 uuid default uuid_generate_v4() not null
        constraint executor_event_cursor_pk primary key,
    consumer_name      text                            not null
        constraint executor_event_cursor_consumer_uq unique,
    event_source       text default 'opensearch'        not null,
    index_name         text,
    last_ingested_at   timestamptz,
    last_document_id   text,
    last_message_id    uuid,
    updated_at         timestamptz default now()       not null,
    metadata           jsonb default '{}'::jsonb       not null,
    constraint executor_event_cursor_source_check check (event_source in ('opensearch', 'kafka'))
);

create index if not exists idx_user_role_assignment_user_id on public.user_role_assignment(user_id);
create index if not exists idx_permission_assignment_user_id on public.permission_assignment(user_id);
create index if not exists idx_permission_assignment_role_id on public.permission_assignment(role_id);
create index if not exists idx_permission_assignment_resource on public.permission_assignment(resource_type, resource_id);
create unique index if not exists permission_assignment_role_resource_permission_uq on public.permission_assignment(role_id, resource_type, resource_id, permission, effect) where subject_type = 'role' and resource_id is not null;
create unique index if not exists permission_assignment_user_resource_permission_uq on public.permission_assignment(user_id, resource_type, resource_id, permission, effect) where subject_type = 'user' and resource_id is not null;
create unique index if not exists permission_assignment_role_system_permission_uq on public.permission_assignment(role_id, permission, effect) where subject_type = 'role' and resource_type = 'system' and resource_id is null;
create unique index if not exists permission_assignment_user_system_permission_uq on public.permission_assignment(user_id, permission, effect) where subject_type = 'user' and resource_type = 'system' and resource_id is null;
create index if not exists idx_folder_parent_id on public.folder(parent_id);
create index if not exists idx_pipeline_folder_id on public.pipeline(folder_id);
create index if not exists idx_stage_pipeline_id on public.stage(pipeline_id);
create index if not exists idx_job_stage_id on public.job(stage_id);
create index if not exists idx_job_template_job_type on public.job_template(job_type);
create index if not exists idx_job_dependency_job_id on public.job_dependency(job_id);
create index if not exists idx_job_dependency_depends_on_job_id on public.job_dependency(depends_on_job_id);
create index if not exists idx_trigger_pipeline_id on public.trigger(pipeline_id);
create index if not exists idx_trigger_event_trigger_id on public.trigger_event(trigger_id);
create index if not exists idx_trigger_event_status_received_at on public.trigger_event(status, received_at);
create index if not exists idx_trigger_event_pipeline_run_id on public.trigger_event(pipeline_run_id);
create unique index if not exists trigger_event_external_non_null_uq on public.trigger_event(event_source, external_event_id) where external_event_id is not null;
create unique index if not exists trigger_event_idempotency_non_null_uq on public.trigger_event(event_source, idempotency_key) where idempotency_key is not null;
create unique index if not exists trigger_event_payload_hash_uq on public.trigger_event(event_source, payload_hash) where payload_hash is not null;
create index if not exists idx_pipeline_run_pipeline_id on public.pipeline_run(pipeline_id);
create index if not exists idx_pipeline_run_correlation_id on public.pipeline_run(correlation_id);
create index if not exists idx_pipeline_run_status on public.pipeline_run(status);
create index if not exists idx_job_execution_pipeline_run_id on public.job_execution(pipeline_run_id);
create index if not exists idx_job_execution_job_id on public.job_execution(job_id);
create index if not exists idx_job_execution_status on public.job_execution(status);
create index if not exists idx_cancellation_request_job_execution_id on public.cancellation_request(job_execution_id);
create index if not exists idx_cancellation_request_pipeline_run_id on public.cancellation_request(pipeline_run_id);
create index if not exists idx_cancellation_request_status on public.cancellation_request(status);
create index if not exists idx_artifact_pipeline_run_id on public.artifact(pipeline_run_id);
create index if not exists idx_artifact_job_execution_id on public.artifact(job_execution_id);
create index if not exists idx_artifact_type on public.artifact(artifact_type);
create index if not exists idx_storage_object_job_execution_id on public.storage_object(job_execution_id);
create index if not exists idx_storage_object_upload_status on public.storage_object(upload_status);
create index if not exists idx_storage_object_retention_expires_at on public.storage_object(retention_policy, expires_at);
create index if not exists idx_secret_ref_provider on public.secret_ref(provider);
create index if not exists idx_external_connection_type on public.external_connection(connection_type);
create index if not exists idx_deployment_approval_environment_id on public.deployment_approval(environment_id);
create index if not exists idx_deployment_approval_status on public.deployment_approval(status);
create index if not exists idx_deployment_release_job_execution_id on public.deployment_release(job_execution_id);
create index if not exists idx_deployment_release_environment_id on public.deployment_release(environment_id);
create index if not exists idx_deployment_release_status on public.deployment_release(status);
create index if not exists idx_audit_event_created_at on public.audit_event(created_at);
create index if not exists idx_outbox_event_status_created_at on public.outbox_event(status, created_at);
create index if not exists idx_outbox_event_topic on public.outbox_event(topic);
create index if not exists idx_inbox_event_consumer_processed_at on public.inbox_event(consumer_name, processed_at);
create index if not exists idx_inbox_event_job_execution_id on public.inbox_event(job_execution_id);
create index if not exists idx_inbox_event_source_document on public.inbox_event(event_source, source_document_id);
create unique index if not exists inbox_event_source_document_consumer_uq on public.inbox_event(event_source, source_document_id, consumer_name) where source_document_id is not null;
create index if not exists idx_executor_event_cursor_source on public.executor_event_cursor(event_source, index_name);
create index if not exists idx_pipeline_run_status_started_at on public.pipeline_run(status, started_at desc);
create index if not exists idx_job_execution_status_created_at on public.job_execution(status, created_at desc);

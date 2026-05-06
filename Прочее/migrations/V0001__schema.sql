create extension if not exists "uuid-ossp";

create table if not exists public.folder
(
    id          uuid default uuid_generate_v4() not null
        constraint pipeline_pk
            primary key,
    name        text                            not null,
    description text,
    parent_id   uuid
        constraint parent_folder_id_fk
            references public.folder
);

create table if not exists public.pipeline
(
    id          uuid default uuid_generate_v4() not null
        constraint pipeline_pk
            primary key,
    name        text                            not null,
    description text,
    folder_id   uuid
        constraint pipeline_folder_id_fk
            references public.folder
);

create table if not exists public.stage
(
    id          uuid default uuid_generate_v4() not null
        constraint stage_pk
            primary key,
    pipeline_id uuid                            not null
        constraint stage_pipeline_id_fk
            references public.pipeline,
    "order"     integer                         not null,
    name        text                            not null,
    description text,
    constraint "unique_pipeline_stage" unique (pipeline_id, "order")
);

create table if not exists public.job
(
    id                uuid             default uuid_generate_v4() not null
        constraint job_pk
            primary key,
    stage_id          uuid    not null
        constraint job_stage_id_fk
            references public.stage,
    "order"           integer not null,
    status            text    not null,
    script            text,
    is_script_primary boolean not null default false,
    constraint "unique_stage_job" unique (stage_id, "order")
);

create table if not exists public.job_template
(
    id              uuid default uuid_generate_v4() not null
        constraint job_template_pk
            primary key,
    path            text                            not null,
    params_template jsonb                           not null
);

create table if not exists public.job_params
(
    id              uuid default uuid_generate_v4() not null
        constraint job_params_pk
            primary key
        constraint job_params_id_fk
            references public.job,
    job_template_id uuid                            not null
        constraint job_template_id_fk
            references public.job_template,
    params          jsonb                           not null
);

create table if not exists public.job_history
(
    id              bigint primary key,
    job_id          uuid      not null
        constraint job_fk
            references public.job,
    duration        bigint    not null,
    start_date      timestamp not null,
    logs            text      not null,
    additional_data jsonb
);

create table if not exists public.trigger
(
    id          bigint primary key,
    name        text not null,
    pipeline_id uuid not null
        constraint pipeline_fk
            references public.pipeline
);

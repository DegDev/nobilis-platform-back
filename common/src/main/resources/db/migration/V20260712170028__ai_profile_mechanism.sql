-- nobilis-platform :: ai — metadata-driven AI-profile mechanism schema + Ollama catalog seed
-- (milestone 06-ai-slice, slice 1 of 6).
--
-- Seven tables: ai_provider (catalog), ai_provider_purpose (pure join: which providers serve a
-- purpose), ai_provider_field / ai_provider_field_option (catalog: the data-driven form's field
-- descriptors), ai_profile (admin-saved purpose->provider binding, BaseEntity-audited, UNIQUE
-- purpose), ai_profile_param (pure value join: saved operational overrides), ai_secret (encrypted
-- value store, keyed by a composite string ref). varchar throughout, never a native Postgres enum.
-- Per BL-003 all migrations live in common regardless of which module's classes own the tables.
--
-- Ollama's wire format is its NATIVE /api/chat endpoint (Fork 5, LOCKED), not OpenAI-compat — so
-- the seeded operational fields use Ollama's own param names (temperature/top_p/num_predict), not
-- an OpenAI-compat max_tokens translation.

create table ai_provider (
    code           varchar(64)  not null primary key,
    label          varchar(255) not null,
    hint           varchar(1024),
    requires_local boolean      not null default false,
    sort_order     integer      not null default 0
);

create table ai_provider_purpose (
    purpose       varchar(64) not null,
    provider_code varchar(64) not null,
    sort_order    integer     not null default 0,
    constraint pk_ai_provider_purpose primary key (purpose, provider_code),
    constraint fk_ai_provider_purpose_provider foreign key (provider_code) references ai_provider (code)
);

create table ai_provider_field (
    id            bigint generated always as identity primary key,
    version       bigint        not null default 0,
    created_at    timestamptz   not null,
    updated_at    timestamptz   not null,
    purpose       varchar(64)   not null,
    provider_code varchar(64)   not null,
    field_key     varchar(64)   not null,
    category      varchar(20)   not null,
    type          varchar(20)   not null,
    editable      boolean       not null default true,
    default_value varchar(1024),
    min_value     numeric,
    max_value     numeric,
    sort_order    integer       not null default 0,
    constraint fk_ai_provider_field_provider foreign key (provider_code) references ai_provider (code),
    constraint uq_ai_provider_field_purpose_provider_key unique (purpose, provider_code, field_key)
);

create table ai_provider_field_option (
    id         bigint generated always as identity primary key,
    version    bigint       not null default 0,
    created_at timestamptz  not null,
    updated_at timestamptz  not null,
    field_id   bigint       not null,
    value      varchar(255) not null,
    sort_order integer      not null default 0,
    constraint fk_ai_provider_field_option_field foreign key (field_id) references ai_provider_field (id)
);

create table ai_profile (
    id            bigint generated always as identity primary key,
    version       bigint      not null default 0,
    created_at    timestamptz not null,
    updated_at    timestamptz not null,
    purpose       varchar(64) not null,
    category      varchar(64),
    provider_code varchar(64) not null,
    constraint uq_ai_profile_purpose unique (purpose),
    constraint fk_ai_profile_provider foreign key (provider_code) references ai_provider (code)
);

create table ai_profile_param (
    profile_id bigint      not null,
    field_key  varchar(64) not null,
    value      text,
    constraint pk_ai_profile_param primary key (profile_id, field_key),
    constraint fk_ai_profile_param_profile foreign key (profile_id) references ai_profile (id)
);

create table ai_secret (
    ref        varchar(255) not null primary key,
    value      text         not null,
    updated_at timestamptz  not null
);

-- Catalog seed: Ollama only, one purpose ("default"), idempotent.

insert into ai_provider (code, label, hint, requires_local, sort_order)
values ('ollama', 'Ollama', 'Native local LLM runtime (host-installed, not containerized).', true, 0)
on conflict (code) do nothing;

insert into ai_provider_purpose (purpose, provider_code, sort_order)
values ('default', 'ollama', 0)
on conflict (purpose, provider_code) do nothing;

insert into ai_provider_field (
    created_at, updated_at, purpose, provider_code, field_key, category, type, editable,
    default_value, min_value, max_value, sort_order
)
values
    (now(), now(), 'default', 'ollama', 'base-url', 'INFRA', 'STRING', false,
     'http://localhost:11434', null, null, 0),
    (now(), now(), 'default', 'ollama', 'model', 'OPERATIONAL', 'STRING', true,
     'llama3', null, null, 1),
    (now(), now(), 'default', 'ollama', 'temperature', 'OPERATIONAL', 'NUMBER', true,
     '0.7', 0, 1, 2),
    (now(), now(), 'default', 'ollama', 'top_p', 'OPERATIONAL', 'NUMBER', true,
     '0.9', 0, 1, 3),
    (now(), now(), 'default', 'ollama', 'num_predict', 'OPERATIONAL', 'NUMBER', true,
     '512', 1, 8192, 4)
on conflict (purpose, provider_code, field_key) do nothing;

-- nobilis-platform :: auth — account identity core (milestone 02-auth, B0.1).
--
-- The RBAC identity root: an account, the provider-scoped identities it authenticates through, and
-- the coarse realms it belongs to. Enum-like columns are varchar (Java @Enumerated(STRING)); no
-- native Postgres enum types (values can't be dropped/renamed without recreating the type). The
-- audit/lock columns mirror BaseEntity (Long IDENTITY id, @Version, @CreatedDate/@LastModifiedDate).
-- Roles and permissions arrive in a later pass (B0.2).

create table account (
    id         bigint generated always as identity primary key,
    version    bigint       not null default 0,
    created_at timestamptz  not null,
    updated_at timestamptz  not null,
    status     varchar(16)  not null
);

create table account_identity (
    id            bigint generated always as identity primary key,
    version       bigint       not null default 0,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null,
    account_id    bigint       not null,
    provider_type varchar(16)  not null,
    external_id   varchar(255) not null,
    secret_hash   varchar(255),
    constraint fk_account_identity_account foreign key (account_id) references account (id),
    constraint uq_account_identity_provider_external unique (provider_type, external_id)
);

create table account_realm (
    account_id bigint      not null,
    realm      varchar(16) not null,
    constraint pk_account_realm primary key (account_id, realm),
    constraint fk_account_realm_account foreign key (account_id) references account (id)
);

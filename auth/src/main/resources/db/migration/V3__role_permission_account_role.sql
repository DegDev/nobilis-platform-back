-- nobilis-platform :: auth — RBAC roles (milestone 02-auth, B0.2).
--
-- Roles as admin-editable permission bundles: a role, the permissions it grants, and the accounts it
-- is assigned to. Permissions are plain varchar strings (the value of an EnginePermissions constant),
-- never a native Postgres enum and never an FK to a catalog table — so a domain product extends the
-- catalog without touching the engine schema. role_permission and account_role are pure membership
-- joins (composite PK, no surrogate id, not audited). role/account audit columns live on their own
-- tables (BaseEntity). "role" is a non-reserved keyword in PostgreSQL, so it needs no quoting.

create table role (
    id         bigint generated always as identity primary key,
    version    bigint       not null default 0,
    created_at timestamptz  not null,
    updated_at timestamptz  not null,
    code       varchar(64)  not null,
    name       varchar(255) not null,
    constraint uq_role_code unique (code)
);

create table role_permission (
    role_id    bigint       not null,
    permission varchar(128) not null,
    constraint pk_role_permission primary key (role_id, permission),
    constraint fk_role_permission_role foreign key (role_id) references role (id)
);

create table account_role (
    account_id bigint not null,
    role_id    bigint not null,
    constraint pk_account_role primary key (account_id, role_id),
    constraint fk_account_role_account foreign key (account_id) references account (id),
    constraint fk_account_role_role foreign key (role_id) references role (id)
);

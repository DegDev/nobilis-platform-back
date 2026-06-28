-- nobilis-platform :: common — baseline schema (milestone 01-common).
--
-- Engine settings store. Secret values are persisted as a "v1:"-prefixed AES-256-GCM ciphertext
-- (see CryptoService); non-secret values are stored as plaintext. The audit/lock columns mirror
-- BaseEntity (Long IDENTITY id, @Version, @CreatedDate/@LastModifiedDate).

create table setting (
    id         bigint generated always as identity primary key,
    version    bigint        not null default 0,
    created_at timestamptz   not null,
    updated_at timestamptz   not null,
    key        varchar(255)  not null,
    value      varchar(4096),
    secret     boolean       not null default false,
    constraint uq_setting_key unique (key)
);

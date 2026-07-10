-- nobilis-platform :: common — CMS content-block model (milestone 03-cms-screen).
--
-- Generic content-block mechanism (not typed per-domain entities). Status is per-item (draft or
-- published), not per-locale. Translations are a child entity, not an @ElementCollection, so each
-- has its own audit trail. The audit/lock columns mirror BaseEntity (Long IDENTITY id, @Version,
-- @CreatedDate/@LastModifiedDate).

create table content_block (
    id         bigint generated always as identity primary key,
    version    bigint        not null default 0,
    created_at timestamptz   not null,
    updated_at timestamptz   not null,
    key        varchar(255)  not null,
    status     varchar(20)   not null,
    constraint uq_content_block_key unique (key)
);

create table content_translation (
    id                bigint generated always as identity primary key,
    version           bigint        not null default 0,
    created_at        timestamptz   not null,
    updated_at        timestamptz   not null,
    content_block_id  bigint        not null,
    locale            varchar(10)   not null,
    body              text          not null,
    constraint fk_content_translation_content_block foreign key (content_block_id) references content_block (id),
    constraint uq_content_translation_block_locale unique (content_block_id, locale)
);

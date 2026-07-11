-- nobilis-platform :: common — notifications config model (milestone 03-notifications-config).
--
-- Config/data layer only; actual dispatch is milestone 04 (integration worker).
-- Engine mechanism: NotificationType.key is free-form (domain inserts order.created etc. at 07).
-- Transport is varchar (EMAIL/TELEGRAM/SMS), never a native PG enum (convention).
-- Cascade deletes: removing a type drops its templates + their translations.

create table notification_type (
    id          bigint generated always as identity primary key,
    version     bigint        not null default 0,
    created_at  timestamptz   not null,
    updated_at  timestamptz   not null,
    key         varchar(255)  not null,
    enabled     boolean       not null,
    description varchar(1024),
    constraint uq_notification_type_key unique (key)
);

create table notification_template (
    id         bigint generated always as identity primary key,
    version    bigint        not null default 0,
    created_at timestamptz   not null,
    updated_at timestamptz   not null,
    type_id    bigint        not null,
    transport  varchar(20)   not null,
    constraint fk_notification_template_type foreign key (type_id) references notification_type (id) on delete cascade,
    constraint uq_notification_template_type_transport unique (type_id, transport)
);

create table notification_template_translation (
    id          bigint generated always as identity primary key,
    version     bigint        not null default 0,
    created_at  timestamptz   not null,
    updated_at  timestamptz   not null,
    template_id bigint        not null,
    locale      varchar(10)   not null,
    subject     varchar(512),
    body        text          not null,
    constraint fk_notification_template_translation_template foreign key (template_id) references notification_template (id) on delete cascade,
    constraint uq_notification_template_translation_locale unique (template_id, locale)
);

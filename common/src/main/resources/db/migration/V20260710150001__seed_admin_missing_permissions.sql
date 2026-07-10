-- nobilis-platform :: auth — backfill the ADMIN role's role_permission with permissions that were
-- added to EnginePermissions after the original seed (V20260710120002): CONTENT_MANAGE (CMS) and
-- NOTIFICATIONS_MANAGE (notifications). The original seed migration is applied and must not be
-- edited in place; this migration adds the missing rows instead. Idempotent (ON CONFLICT DO NOTHING).

insert into role_permission (role_id, permission)
select r.id, perm.permission
from role r
    cross join (values ('CONTENT_MANAGE'), ('NOTIFICATIONS_MANAGE')) as perm(permission)
where r.code = 'ADMIN'
on conflict (role_id, permission) do nothing;

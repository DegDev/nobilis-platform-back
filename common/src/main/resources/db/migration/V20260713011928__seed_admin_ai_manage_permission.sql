-- nobilis-platform :: auth — backfill the ADMIN role's role_permission with AI_MANAGE, added to
-- EnginePermissions after the original seed (V20260710120002) for milestone 06-ai-slice's admin
-- controller. The original seed migration is applied and must not be edited in place; this
-- migration adds the missing row instead (same pattern as V20260710150001). Idempotent
-- (ON CONFLICT DO NOTHING).

insert into role_permission (role_id, permission)
select r.id, 'AI_MANAGE'
from role r
where r.code = 'ADMIN'
on conflict (role_id, permission) do nothing;

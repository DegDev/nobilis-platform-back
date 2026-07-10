-- nobilis-platform :: auth — seed the engine's default ADMIN role (milestone 03, pass 4a).
--
-- One engine-level role carrying the engine's OWN permissions only (EnginePermissions.ALL:
-- ACCOUNT_MANAGE + SETTINGS_MANAGE), so a later pass can link an admin account to a real role.
-- Engine seed only — domain roles/permissions arrive in milestone 07 and are never seeded here.
-- The code 'ADMIN' matches AdminLoginService.ADMIN_ROLE, so a DB-backed admin presents the same
-- role as the stateless config-admin. Idempotent (ON CONFLICT DO NOTHING) so a re-run is a no-op;
-- id is database-generated (never hard-coded), so role_permission resolves it by the role code.

insert into role (created_at, updated_at, code, name)
values (now(), now(), 'ADMIN', 'Administrator')
on conflict (code) do nothing;

insert into role_permission (role_id, permission)
select r.id, perm.permission
from role r
    cross join (values ('ACCOUNT_MANAGE'), ('SETTINGS_MANAGE')) as perm(permission)
where r.code = 'ADMIN'
on conflict (role_id, permission) do nothing;

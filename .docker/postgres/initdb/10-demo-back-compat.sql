-- demo-back-compatible login: role `postgres` / password `example`.
-- Lets you reuse the same Adminer credentials as the demo-back stack
-- (which runs plain `postgres` with POSTGRES_PASSWORD=example).
--
-- The app itself keeps using nobilis/nobilis — this is an extra superuser
-- for manual DB browsing only.
--
-- NOTE: Postgres runs scripts in /docker-entrypoint-initdb.d exactly once,
-- at FIRST init of an empty data volume. On an already-initialised pg_data
-- volume this does NOT run — the role was created manually there. Wipe the
-- volume and it gets recreated from this script.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'postgres') THEN
    CREATE ROLE postgres WITH LOGIN SUPERUSER PASSWORD 'example';
  END IF;
END
$$;

-- The service role's grants on the `social` schema.
--
-- The tables are created by a migration, so they belong to the migration's role and nothing
-- else can touch them. `public` hides this: Supabase ships default privileges there, so a
-- table created in `public` is reachable by the API roles without anybody granting anything.
-- A schema created by hand carries none of that.
--
-- Production has these because whoever set the pipeline up granted them by hand. Development
-- did not, which is why `generate` answered `permission denied for schema social` the moment
-- the schema was exposed to PostgREST.
--
-- **Only `service_role`.** The edge functions are the only thing that should read or write
-- here; the admin panel goes through `public` views that carry their own permission check, and
-- `anon` and `authenticated` are deliberately left with nothing. The RLS on those tables has
-- no policies, so they would get no rows either way — this makes the intent explicit rather
-- than relying on that.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.

grant usage on schema social to service_role;

grant all on all tables    in schema social to service_role;
grant all on all sequences in schema social to service_role;
grant all on all functions in schema social to service_role;

-- For anything added to the schema later, so the next table does not repeat this file.
alter default privileges in schema social grant all on tables    to service_role;
alter default privileges in schema social grant all on sequences to service_role;
alter default privileges in schema social grant all on functions to service_role;

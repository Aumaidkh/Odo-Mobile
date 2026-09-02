-- A permission of its own for feature flags (#363's flags section).
--
-- Not folded into `admin.roles.write`. Flags and roles are different powers: shipping a
-- rollout is an engineering action somebody may hold without being able to grant themselves
-- more access, and the two being one permission would mean nobody could have the first
-- without the second.
--
-- Seeded to super_admin only, because that is the only role that exists today which should
-- hold it. A fourth role — the mockup calls it Engineer — is a row in admin_roles and a row
-- here, not a migration of code.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.

insert into public.admin_role_permissions (role_slug, permission)
values ('super_admin', 'flags.write')
on conflict (role_slug, permission) do nothing;

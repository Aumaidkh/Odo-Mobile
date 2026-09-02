-- The first super-admin. Run once, by hand, from the SQL editor.
--
-- This file exists because granting a role requires holding `admin.roles.write`, and at the
-- moment the schema is created nobody holds anything. There is no way to break that loop from
-- inside the model, so it gets broken from outside it — the SQL editor runs as the service
-- role, which bypasses RLS.
--
-- Change the two values below before running. The address must be one that can sign in to the
-- Firebase project with an email and a password, because that is what `admin-session` verifies
-- before it looks anybody up here.
--
-- `user_id` stays null. It is bound to a Supabase account by `admin-session` on the first
-- sign-in, which is the only thing that should ever write it.
--
-- Safe to re-run: it will not duplicate the admin or the role grant, and it will not
-- resurrect an admin somebody deliberately deactivated.

\set admin_email '\'admin@odoapp.in\''
\set admin_name  '\'Murtaza\''

-- The SQL editor does not support \set. If you are pasting this in rather than running it
-- through psql, replace :admin_email and :admin_name below with quoted literals by hand.

insert into public.admin_users (email, name)
values (lower(:admin_email), :admin_name)
on conflict (email) do nothing;

insert into public.admin_user_roles (admin_id, role_slug, granted_by)
select a.id, 'super_admin', null
  from public.admin_users a
 where a.email = lower(:admin_email)
on conflict (admin_id, role_slug) do nothing;

-- What it looks like when it worked: one row, super_admin, active, user_id still null.
select a.email, a.is_active, a.user_id, ur.role_slug
  from public.admin_users a
  join public.admin_user_roles ur on ur.admin_id = a.id
 where a.email = lower(:admin_email);

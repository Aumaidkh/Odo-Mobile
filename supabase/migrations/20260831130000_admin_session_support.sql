-- The two SQL objects `admin-session` and the admin shell need, beyond the model in
-- 20260831120000_admin_rbac.sql.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.


-- ─────────────────────────────────────────────────────────────────────────────
-- Looking an account up by address.
--
-- This is a verbatim copy of the function 20260820120000_blog.sql already defines, and the
-- duplication is deliberate. `create or replace` makes it a no-op on a project that has the
-- blog schema, and on one that does not — dev, today — it means the admin panel does not
-- silently depend on a blog migration nobody intends to apply there. The admin panel and the
-- blog are separate deployments with separate reasons to exist; a shared function that only
-- one of them ships is a dependency waiting to be discovered at sign-in.
--
-- If the definition here and the one in the blog migration ever diverge, they are the same
-- function and whichever ran last wins. They should not diverge: it is four lines and it does
-- one thing.
--
-- SECURITY DEFINER because `auth.users` is readable by no client role and must stay that way.
-- It returns an id and nothing else, so it cannot be used to enumerate addresses.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function public.auth_user_id_by_email(p_email text)
returns uuid
language sql
security definer
-- Empty search_path, so a schema planted on the caller's path cannot shadow auth.users.
-- Every object below is therefore schema-qualified.
set search_path = ''
stable
as $function$
  select u.id
  from auth.users u
  where lower(u.email) = lower(p_email)
  limit 1;
$function$;

-- The default grant on a new function is to PUBLIC, which would put it on the anon role's
-- PostgREST surface — an unauthenticated "does this address have an account?" oracle. Only
-- the Edge Functions may call it.
revoke all on function public.auth_user_id_by_email(text) from public;
revoke all on function public.auth_user_id_by_email(text) from anon;
revoke all on function public.auth_user_id_by_email(text) from authenticated;
grant execute on function public.auth_user_id_by_email(text) to service_role;


-- ─────────────────────────────────────────────────────────────────────────────
-- Who am I, and what may I do.
--
-- One call, because the alternative is three: read your own `admin_users` row, read the roles
-- on it, then read the permissions on those. The shell asks this once after sign-in to decide
-- what to put in the nav.
--
-- **This is not an access check.** It is what the nav draws. Every actual permission question
-- is answered by `admin_has()` inside a policy, at the moment of the write, which is the whole
-- point of D3 in docs/ADMIN_PANEL_PLAN.md — a client that lies about this list still cannot
-- write anything, it just draws itself a menu that does not work.
--
-- Returns null for somebody who is not an active admin, rather than an empty shape. A caller
-- that cannot tell "no roles" from "not staff" would draw an empty panel for both.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function public.my_admin_identity()
returns jsonb
language sql
stable
security definer
set search_path = public
as $function$
    select jsonb_build_object(
        'id',    u.id,
        'email', u.email,
        'name',  u.name,
        -- Sorted, so the same person always gets the same array and a client-side diff of
        -- "did my permissions change" does not fire on a reordering.
        'permissions', coalesce(
            (
                select jsonb_agg(distinct rp.permission order by rp.permission)
                  from public.admin_user_roles ur
                  join public.admin_role_permissions rp on rp.role_slug = ur.role_slug
                 where ur.admin_id = u.id
            ),
            '[]'::jsonb
        ),
        'roles', coalesce(
            (
                select jsonb_agg(ur.role_slug order by ur.role_slug)
                  from public.admin_user_roles ur
                 where ur.admin_id = u.id
            ),
            '[]'::jsonb
        )
    )
      from public.admin_users u
     where u.user_id = auth.uid()
       and u.is_active
$function$;

comment on function public.my_admin_identity() is
    'The signed-in admin''s identity and permission list, for drawing the nav. Never an '
    'access check — admin_has() in a policy is. Null when the caller is not an active admin.';

revoke all on function public.my_admin_identity() from public, anon;
grant execute on function public.my_admin_identity() to authenticated;

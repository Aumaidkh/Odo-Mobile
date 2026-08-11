-- Look up an auth user by phone number, for the firebase-session Edge Function.
--
-- The alternative is admin.listUsers, which pages through every user in the project and gets
-- slower with every signup. This is one indexed lookup.
--
-- SECURITY DEFINER because auth.users is not readable by any client role, and this must stay
-- that way: the function returns an id and nothing else, so it cannot be used to enumerate
-- numbers, email addresses or anything else on the row.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.

create or replace function public.auth_user_id_by_phone(p_phone text)
returns uuid
language sql
security definer
-- Empty search_path, so a schema planted on the caller's path cannot shadow auth.users.
-- Every object below is therefore schema-qualified.
set search_path = ''
stable
as $$
  -- GoTrue stores phone numbers as bare digits. Normalising here rather than at the call site
  -- means a caller passing E.164 with the leading + still matches.
  select u.id
  from auth.users u
  where u.phone = ltrim(p_phone, '+')
  limit 1;
$$;

-- Only the Edge Function may call this. The default grant on a new function is to PUBLIC,
-- which would put it on the anon role's PostgREST surface — an unauthenticated
-- "does this number have an account?" oracle.
revoke all on function public.auth_user_id_by_phone(text) from public;
revoke all on function public.auth_user_id_by_phone(text) from anon;
revoke all on function public.auth_user_id_by_phone(text) from authenticated;
grant execute on function public.auth_user_id_by_phone(text) to service_role;

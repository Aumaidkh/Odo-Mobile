-- A browsable user directory for the support screen, with contact details masked by default
-- and every reveal recorded.
--
-- `admin_find_user` (20260831160000) stays: it is the "I have the exact number" path and it
-- returns everything at once. This is the other half — "somebody is on the phone and I have
-- half a number" — which the exact-match lookup cannot answer.
--
-- **Masking is done in the database, not in the browser.** A client that received the real
-- number and drew asterisks over it would be one keystroke in a developer console away from
-- showing everything, and the reveal would never be logged. So the list returns masked text,
-- and the only way to the real value is a second function that writes an audit row first.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.


-- ─────────────────────────────────────────────────────────────────────────────
-- Masking helpers.
--
-- Enough of the value to recognise it against what somebody is reading out, and not enough to
-- be a contact list. A phone keeps its last four; an address keeps its first character and
-- its domain.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function public.mask_phone(p_phone text)
returns text
language sql
immutable
as $function$
    select case
        when p_phone is null or length(p_phone) < 4 then null
        else repeat('•', greatest(length(p_phone) - 4, 0)) || right(p_phone, 4)
    end
$function$;

create or replace function public.mask_email(p_email text)
returns text
language sql
immutable
as $function$
    select case
        when p_email is null or position('@' in p_email) < 2 then null
        else left(p_email, 1) || repeat('•', 4) || substring(p_email from position('@' in p_email))
    end
$function$;


-- ─────────────────────────────────────────────────────────────────────────────
-- The directory.
--
-- Paged, because 41,000 rows is not a screen. `p_query` narrows by a partial phone or address
-- — which is exactly the enumeration risk `admin_find_user` was written to avoid, and is
-- acceptable here only because what comes back is masked and the caller is already staff.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function public.admin_list_users(
    p_query  text default null,
    p_limit  int  default 25,
    p_offset int  default 0
)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $function$
declare
    v_query text := nullif(btrim(coalesce(p_query, '')), '');
    v_limit int  := least(greatest(coalesce(p_limit, 25), 1), 100);
    v_total bigint;
    v_rows  jsonb;
begin
    if not public.admin_has('users.read') then
        raise exception 'not permitted' using errcode = '42501';
    end if;

    select count(*) into v_total
      from public.profiles p
      left join auth.users u on u.id = p.id
     where v_query is null
        or p.phone ilike '%' || v_query || '%'
        or coalesce(u.email, '') ilike '%' || v_query || '%'
        or coalesce(p.full_name, '') ilike '%' || v_query || '%';

    select coalesce(jsonb_agg(row_to_json(t)::jsonb order by t.created_at desc), '[]'::jsonb)
      into v_rows
      from (
        select
            p.id,
            p.full_name           as name,
            public.mask_phone(p.phone)      as phone,
            public.mask_email(u.email)      as email,
            p.city,
            p.restriction,
            p.created_at,
            (select count(*) from public.cars c where c.owner_id = p.id and c.deleted_at is null) as cars,
            -- Whether support has granted or withheld anything, which is the one
            -- entitlement fact worth showing in a list. The plan itself lives with
            -- the store and is not the database's to report.
            (
                select e.granted from public.entitlement_overrides e
                 where e.owner_id = p.id and e.feature = 'PRO'
            ) as pro_override
          from public.profiles p
          left join auth.users u on u.id = p.id
         where v_query is null
            or p.phone ilike '%' || v_query || '%'
            or coalesce(u.email, '') ilike '%' || v_query || '%'
            or coalesce(p.full_name, '') ilike '%' || v_query || '%'
         order by p.created_at desc
         limit v_limit offset greatest(coalesce(p_offset, 0), 0)
      ) t;

    return jsonb_build_object('total', v_total, 'rows', v_rows);
end;
$function$;

comment on function public.admin_list_users(text, int, int) is
    'Paged, masked user directory for the support screen. Contact details are masked here '
    'rather than in the client, so the real values never reach a browser that has not asked '
    'for them through admin_reveal_user_contact — which logs.';

revoke all on function public.admin_list_users(text, int, int) from public, anon;
grant execute on function public.admin_list_users(text, int, int) to authenticated;


-- ─────────────────────────────────────────────────────────────────────────────
-- Revealing one account's contact details.
--
-- The audit row is written **before** the value is returned, and in the same transaction. If
-- the insert fails the reveal fails with it, so there is no path that hands over a number
-- without a record of who asked.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function public.admin_reveal_user_contact(p_id uuid)
returns jsonb
language plpgsql
volatile
security definer
set search_path = public
as $function$
declare
    v_phone text;
    v_email text;
begin
    if not public.admin_has('users.read') then
        raise exception 'not permitted' using errcode = '42501';
    end if;

    select p.phone, u.email into v_phone, v_email
      from public.profiles p
      left join auth.users u on u.id = p.id
     where p.id = p_id;

    if not found then
        return null;
    end if;

    insert into public.admin_audit_log (actor_admin_id, action, subject_type, subject_id, after)
    values (
        public.current_admin_id(),
        'REVEAL',
        'profiles',
        p_id::text,
        -- What was revealed, not the values themselves. The log says somebody looked;
        -- copying the number into it would make the audit trail its own contact list.
        jsonb_build_object('revealed', jsonb_build_array('phone', 'email'))
    );

    return jsonb_build_object('phone', v_phone, 'email', v_email);
end;
$function$;

comment on function public.admin_reveal_user_contact(uuid) is
    'Returns one account''s real phone and email, after writing the audit row. The insert and '
    'the read are one transaction, so there is no path that reveals without recording it. The '
    'log records that a reveal happened, never the values.';

revoke all on function public.admin_reveal_user_contact(uuid) from public, anon;
grant execute on function public.admin_reveal_user_contact(uuid) to authenticated;

-- Withdrawing an entitlement override (issue #448).
--
-- Clear used to delete the row. The app pulls this table as a delta, and a delta cannot
-- mention a row that is no longer there — an absent row and a read that returned nothing look
-- identical — so every device kept the grant forever. A withdrawal is a tombstone now: the row
-- stays, the client sees it and drops its local copy.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.


alter table public.entitlement_overrides
    add column if not exists deleted_at timestamptz;

comment on column public.entitlement_overrides.deleted_at is
    'Set when support withdrew the override. The owner still reads the row — that is how the '
    'app learns the grant is gone — while every admin surface filters it out.';


-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Every write moves granted_at.
--
-- The client's delta pull is ordered by this column, and the column's default only applies on
-- insert. So an edited row kept the timestamp of its first grant, sorted behind the device's
-- cursor, and was never fetched again — the revoke simply never arrived. Stamped here rather
-- than in each caller's payload, because a writer that forgets is a silent failure.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function public.stamp_entitlement_override()
returns trigger
language plpgsql
as $function$
begin
    new.granted_at := now();
    return new;
end;
$function$;

drop trigger if exists trg_entitlement_overrides_stamp on public.entitlement_overrides;
create trigger trg_entitlement_overrides_stamp
    before insert or update on public.entitlement_overrides
    for each row execute function public.stamp_entitlement_override();


-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Withdrawing one.
--
-- An RPC rather than a PATCH from the panel: `now()` cannot be sent in a PostgREST payload
-- (the value arrives as the text 'now()', which is not a valid timestamptz), and the moment a
-- row was withdrawn is the server's to decide anyway.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function public.admin_clear_entitlement(p_owner_id uuid, p_feature text)
returns void
language plpgsql
volatile
security definer
set search_path = public
as $function$
begin
    if not public.admin_has('users.entitlements.write') then
        raise exception 'not permitted' using errcode = '42501';
    end if;

    update public.entitlement_overrides
       set deleted_at = now()
     where owner_id = p_owner_id
       and feature = p_feature
       and deleted_at is null;
end;
$function$;

comment on function public.admin_clear_entitlement(uuid, text) is
    'Withdraw an override by marking it deleted. Never a DELETE: the client pulls this table '
    'as a delta and cannot be told about a row that is gone.';

revoke all on function public.admin_clear_entitlement(uuid, text) from public, anon;
grant execute on function public.admin_clear_entitlement(uuid, text) to authenticated;


-- ─────────────────────────────────────────────────────────────────────────────
-- 3. The admin surfaces skip tombstones. Both functions are replaced whole because the filter
-- lives inside a sub-select; only the `deleted_at is null` clauses differ from what was there.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function public.admin_find_user(p_query text)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $function$
declare
    v_query text := btrim(coalesce(p_query, ''));
    v_row   record;
begin
    if not public.admin_has('users.read') then
        raise exception 'not permitted' using errcode = '42501';
    end if;
    if v_query = '' then
        return null;
    end if;

    select p.id, p.phone, p.restriction, p.restriction_reason, p.restricted_at, u.email, p.created_at
      into v_row
      from public.profiles p
      left join auth.users u on u.id = p.id
     where p.phone = v_query
        or lower(coalesce(u.email, '')) = lower(v_query)
        or p.id::text = v_query
     limit 1;

    if not found then
        return null;
    end if;

    return jsonb_build_object(
        'id', v_row.id,
        'phone', v_row.phone,
        'email', v_row.email,
        'restriction', v_row.restriction,
        'restriction_reason', v_row.restriction_reason,
        'restricted_at', v_row.restricted_at,
        'created_at', v_row.created_at,
        'entitlements', coalesce(
            (
                select jsonb_agg(jsonb_build_object(
                    'feature', e.feature,
                    'granted', e.granted,
                    'expires_at', e.expires_at,
                    'reason', e.reason,
                    'granted_at', e.granted_at
                ) order by e.feature)
                  from public.entitlement_overrides e
                 where e.owner_id = v_row.id
                   and e.deleted_at is null
            ),
            '[]'::jsonb
        )
    );
end;
$function$;

comment on function public.admin_find_user(text) is
    'One user by exact phone, email or id, with their entitlement overrides. Exact rather '
    'than prefix on purpose: a support tool that lists everyone matching a few digits is an '
    'enumeration tool.';

revoke all on function public.admin_find_user(text) from public, anon;
grant execute on function public.admin_find_user(text) to authenticated;


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
            p.full_name                as name,
            public.mask_phone(p.phone) as phone,
            public.mask_email(u.email) as email,
            -- The city's name, not its id: an admin reading a support ticket needs
            -- "Pune", and a uuid in that column would be a second lookup every time.
            (select c.name from public.cities c where c.id = p.home_city_id) as city,
            p.restriction,
            p.created_at,
            (select count(*) from public.cars c where c.owner_id = p.id and c.deleted_at is null) as cars,
            (
                select e.granted from public.entitlement_overrides e
                 where e.owner_id = p.id and e.feature = 'PRO'
                   and e.deleted_at is null
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

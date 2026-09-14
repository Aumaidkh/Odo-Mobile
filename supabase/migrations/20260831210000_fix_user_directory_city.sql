-- Fixes `admin_list_users`, which named a column that does not exist.
--
-- It selected `profiles.city`. That column is on the *device* — SQLDelight's local `profiles`
-- table keeps a city name as text — while the server keeps `home_city_id`, a uuid referencing
-- `cities`. The two tables share a name and are not the same shape, and this is what that
-- costs: the function was created happily (plpgsql does not resolve column names until it
-- runs) and failed with 42703 the first time it was called.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.

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

-- `admin_find_user` never selected a city, so it is untouched.

-- Put this account's devices on the support screen.
--
-- Support could find an account but not the install behind it, so nobody could answer
-- "did they actually open the app" or look the person up in GA4. The rows already exist
-- (20260917100000); this is what puts them in front of a human.
--
-- Body is the 20260908120000 version with one key added. `create or replace` is enough:
-- the return type is still jsonb, and the panel's decoder ignores keys it does not know.

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
        ),
        -- Most recently seen first: the one support is being asked about is almost always
        -- the phone in the person's hand right now.
        'devices', coalesce(
            (
                select jsonb_agg(jsonb_build_object(
                    'install_id', d.install_id,
                    'app_instance_id', d.app_instance_id,
                    'platform', d.platform,
                    'app_version', d.app_version,
                    'os_version', d.os_version,
                    'model', d.model,
                    'first_seen_at', d.first_seen_at,
                    'last_seen_at', d.last_seen_at
                ) order by d.last_seen_at desc)
                  from public.user_devices d
                 where d.owner_id = v_row.id
            ),
            '[]'::jsonb
        )
    );
end;
$function$;

comment on function public.admin_find_user(text) is
    'One user by exact phone, email or id, with their entitlement overrides and devices. '
    'Exact rather than prefix on purpose: a support tool that lists everyone matching a few '
    'digits is an enumeration tool.';

revoke all on function public.admin_find_user(text) from public, anon;
grant execute on function public.admin_find_user(text) to authenticated;

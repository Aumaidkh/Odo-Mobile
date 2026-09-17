-- Give record_device_seen a return value.
--
-- `returns void` makes PostgREST answer 204 with an empty body, and the client's rpc()
-- decodes every answer it gets — an empty string is not JSON, so every call would have
-- thrown after the row was already written. Returning the stamped time makes the body a
-- value, and tells the caller what the server recorded.
--
-- Safe to drop: nothing calls this yet. A return type cannot be changed by
-- `create or replace`, so the drop is the only way.

drop function if exists public.record_device_seen(text, text, text, text, text, text);

create or replace function public.record_device_seen(
    p_install_id      text,
    p_platform        text,
    p_app_instance_id text default null,
    p_app_version     text default null,
    p_os_version      text default null,
    p_model           text default null
)
returns timestamptz
language plpgsql
volatile
security definer
set search_path = public
as $function$
declare
    v_owner uuid := auth.uid();
    v_seen  timestamptz;
begin
    if v_owner is null then
        raise exception 'not permitted' using errcode = '42501';
    end if;

    if coalesce(p_install_id, '') = '' or coalesce(p_platform, '') = '' then
        raise exception 'install_id and platform are required' using errcode = '22023';
    end if;

    insert into public.user_devices as d (
        owner_id, install_id, app_instance_id, platform, app_version, os_version, model
    )
    values (
        v_owner, p_install_id, p_app_instance_id, p_platform, p_app_version, p_os_version, p_model
    )
    on conflict (owner_id, install_id) do update set
        -- coalesce on the incoming value: a heartbeat sent before Firebase finished starting
        -- carries no instance id, and must not erase the one already recorded.
        app_instance_id = coalesce(excluded.app_instance_id, d.app_instance_id),
        platform        = excluded.platform,
        app_version     = coalesce(excluded.app_version, d.app_version),
        os_version      = coalesce(excluded.os_version, d.os_version),
        model           = coalesce(excluded.model, d.model),
        last_seen_at    = now()
    returning d.last_seen_at into v_seen;

    return v_seen;
end;
$function$;

comment on function public.record_device_seen(text, text, text, text, text, text) is
    'The device heartbeat. The only writer of user_devices, so owner_id comes from the '
    'session and the timestamps come from the server. Returns the time it stamped.';

revoke all on function public.record_device_seen(text, text, text, text, text, text) from public, anon;
grant execute on function public.record_device_seen(text, text, text, text, text, text) to authenticated;

-- Which devices an account is actually using. Issue #473 follow-up, epic: user identity.
--
-- Support can find an account in /admin but has no way to find that person in Google
-- Analytics, because nothing ties an account to an install. This table is that tie: it
-- carries Firebase's app instance id, which is the key GA4 reports on (`user_pseudo_id`),
-- so a support answer can be pasted straight into GA4 or BigQuery.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md.

create table if not exists public.user_devices (
    owner_id        uuid        not null references public.profiles (id) on delete cascade,
    -- The app's own id for this installation, stable until the app is reinstalled.
    install_id      text        not null,
    -- Firebase's app instance id. Null on a device whose Firebase never initialised, which
    -- is why it is not the primary key.
    app_instance_id text,
    -- 'android' or 'ios' today. Deliberately unconstrained: a check here would make a
    -- future platform's heartbeat fail rather than simply record an unknown value.
    platform        text        not null,
    app_version     text,
    os_version      text,
    model           text,
    first_seen_at   timestamptz not null default now(),
    last_seen_at    timestamptz not null default now(),
    primary key (owner_id, install_id)
);

comment on table public.user_devices is
    'One row per (account, installation). A reinstall produces a new row rather than '
    'overwriting the old one, so the history shows that it happened.';

-- The admin device list, newest first.
create index if not exists idx_user_devices_owner
    on public.user_devices (owner_id, last_seen_at desc);

-- The reverse lookup: support has a GA4 pseudo id and needs to know whose it is.
create index if not exists idx_user_devices_app_instance
    on public.user_devices (app_instance_id)
    where app_instance_id is not null;

alter table public.user_devices enable row level security;

-- The owner may read their own rows; the privacy screen shows them what is held.
drop policy if exists user_devices_read_own on public.user_devices;
create policy user_devices_read_own on public.user_devices
    for select to authenticated
    using (owner_id = (select auth.uid()));

-- Support reads every row. The same permission that opens the user directory, because this
-- is one more field on the screen support already has.
drop policy if exists user_devices_admin_read on public.user_devices;
create policy user_devices_admin_read on public.user_devices
    for select to authenticated
    using (public.admin_has('users.read'));

-- No write policy at all. Every write goes through record_device_seen below, so owner_id is
-- stamped from the session rather than sent by the client.

/*
 * Record that this device is alive, and who is on it.
 *
 * SECURITY DEFINER with owner_id taken from auth.uid(): a client that could send its own
 * owner_id could attach its install to somebody else's account.
 *
 * Timestamps are stamped here rather than sent. A client-supplied "now()" arrives as text
 * and fails the cast, and a client-supplied time is a client-chosen time.
 */
create or replace function public.record_device_seen(
    p_install_id      text,
    p_platform        text,
    p_app_instance_id text default null,
    p_app_version     text default null,
    p_os_version      text default null,
    p_model           text default null
)
returns void
language plpgsql
volatile
security definer
set search_path = public
as $function$
declare
    v_owner uuid := auth.uid();
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
        last_seen_at    = now();
end;
$function$;

comment on function public.record_device_seen(text, text, text, text, text, text) is
    'The device heartbeat. The only writer of user_devices, so owner_id comes from the '
    'session and the timestamps come from the server.';

revoke all on function public.record_device_seen(text, text, text, text, text, text) from public, anon;
grant execute on function public.record_device_seen(text, text, text, text, text, text) to authenticated;

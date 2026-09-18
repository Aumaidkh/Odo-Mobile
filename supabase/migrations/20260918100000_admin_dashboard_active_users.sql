-- Active users on the dashboard, from the device heartbeat.
--
-- `user_devices.last_seen_at` is written by `record_device_seen` on every launch, so the
-- distinct owners behind it over a window are the people who actually opened the app.
-- Counted by owner and not by install: two phones are one person, and a dashboard that says
-- otherwise overstates its own numbers every time somebody upgrades.
--
-- **These are signed-in actives, not all actives.** The heartbeat sits behind a session check
-- in the app, so an owner who has never verified a number never writes a row — and since setup
-- stopped asking for one, that is the ordinary state of a new install. Read this as "accounts
-- that opened the app", and read GA4 for the rest. The column names say so.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.

create or replace function public.admin_dashboard()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $function$
declare
    result jsonb;
begin
    -- `users.read` and not a coarser check: the dashboard is the landing page for support
    -- admins as well as super admins, and it is the one screen both are guaranteed to see.
    if not public.admin_has('users.read') then
        raise exception 'not permitted' using errcode = '42501';
    end if;

    select jsonb_build_object(
        'users',            (select count(*) from public.profiles),

        'users_7d',         (select count(*) from public.profiles where created_at >= now() - interval '7 days'),
        'users_prev_7d',    (select count(*) from public.profiles
                             where created_at >= now() - interval '14 days'
                               and created_at <  now() - interval '7 days'),

        'cars',             (select count(*) from public.cars),
        'service_logs',     (select count(*) from public.service_logs),
        'documents',        (select count(*) from public.documents),

        'subs_active',      (select count(*) from public.subscriptions where status = 'active'),
        'subs_past_due',    (select count(*) from public.subscriptions where status = 'past_due'),

        'tickets_open',     (select count(*) from public.support_tickets where status in ('open', 'pending')),
        'tickets_urgent',   (select count(*) from public.support_tickets
                             where status in ('open', 'pending') and priority = 'urgent'),

        'posts_published',  (select count(*) from public.blog_posts where status = 'published'),
        'posts_draft',      (select count(*) from public.blog_posts where status = 'draft'),

        'vehicle_pending',  (select count(*) from public.vehicle_catalog_submissions where status = 'pending'),
        'city_pending',     (select count(*) from public.city_submissions where status = 'pending'),

        -- Signed-in accounts that opened the app in the window. Rolling windows rather than
        -- calendar days: "today" on a dashboard read at 9am is a quarter of a day, and the
        -- number would climb all day and then appear to collapse at midnight.
        'active_1d',        (select count(distinct owner_id) from public.user_devices
                             where last_seen_at >= now() - interval '1 day'),
        'active_prev_1d',   (select count(distinct owner_id) from public.user_devices
                             where last_seen_at >= now() - interval '2 days'
                               and last_seen_at <  now() - interval '1 day'),
        'active_7d',        (select count(distinct owner_id) from public.user_devices
                             where last_seen_at >= now() - interval '7 days'),
        'active_30d',       (select count(distinct owner_id) from public.user_devices
                             where last_seen_at >= now() - interval '30 days'),

        'signups', (
            select coalesce(jsonb_agg(jsonb_build_object('d', to_char(day, 'YYYY-MM-DD'), 'n', n) order by day), '[]'::jsonb)
            from (
                select d::date as day,
                       (select count(*) from public.profiles p where p.created_at::date = d::date) as n
                from generate_series(now()::date - interval '13 days', now()::date, interval '1 day') d
            ) s
        ),

        -- The same fourteen days as the signup series, so the two charts sit on one axis.
        -- A day nobody opened the app is a zero here rather than a gap, for the reason the
        -- signup series gives: a series built only from days with rows rescales silently.
        'actives', (
            select coalesce(jsonb_agg(jsonb_build_object('d', to_char(day, 'YYYY-MM-DD'), 'n', n) order by day), '[]'::jsonb)
            from (
                select d::date as day,
                       (select count(distinct ud.owner_id) from public.user_devices ud
                        where ud.last_seen_at::date = d::date) as n
                from generate_series(now()::date - interval '13 days', now()::date, interval '1 day') d
            ) s
        ),

        'activity', (
            select coalesce(jsonb_agg(row_to_json(a) order by a.at desc), '[]'::jsonb)
            from (
                select l.action, l.subject_type, l.at, u.email as actor
                from public.admin_audit_log l
                left join auth.users u on u.id = l.actor_id
                order by l.at desc
                limit 8
              ) a
        )
    ) into result;

    return result;
end;
$function$;

comment on function public.admin_dashboard() is
    'Counts, a 14-day signup series, a 14-day signed-in-active series and the last few audit '
    'entries, for /admin/dashboard. Actives come from the device heartbeat and therefore count '
    'accounts, not anonymous installs. Numbers only — the panel owns the wording.';

revoke all on function public.admin_dashboard() from public, anon;
grant execute on function public.admin_dashboard() to authenticated;

-- The actives queries scan by date; without this every dashboard read is a sequential scan of
-- the whole table, and it is read on every visit to /admin.
create index if not exists idx_user_devices_last_seen
    on public.user_devices (last_seen_at desc);

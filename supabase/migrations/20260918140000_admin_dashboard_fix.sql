-- Fixes the dashboard, and adds active accounts to it.
--
-- `20260918100000_admin_dashboard_active_users.sql` rebuilt this function from a grep of the
-- original rather than from the file, and three things drifted. One was fatal: the activity
-- join was written as `left join auth.users u on u.id = l.actor_id`, and `admin_audit_log`
-- has no `actor_id` — it has `actor_admin_id`, referencing `admin_users`. The whole
-- `select ... into result` therefore threw on every call, which the panel showed as "That did
-- not work. Try again." on both dev and production.
--
-- The other two: `tickets_urgent` narrowed from `priority in ('high','urgent')` to
-- `= 'urgent'`, and the signup series' `generate_series` was rewritten for no reason. Both are
-- restored verbatim below.
--
-- This file is the original with the active-account keys inserted and nothing else touched.
--
-- Actives come from `user_devices.last_seen_at`, written on every launch. Counted by owner and
-- not by install: two phones are one person. Rolling windows rather than calendar days —
-- "today" read at 9am is a quarter of a day, and the number would climb all day and appear to
-- collapse at midnight.
--
-- **Signed-in accounts, not all actives.** The heartbeat sits behind a session check in the
-- app and setup no longer asks for a number, so an owner who never verified one is using Odo
-- and is absent here. Read GA4 for the rest.
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
        -- Signups in the last week, for the delta under the headline number.
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
                              where status in ('open', 'pending') and priority in ('high', 'urgent')),

        'posts_published',  (select count(*) from public.blog_posts where status = 'published'),
        'posts_draft',      (select count(*) from public.blog_posts where status = 'draft'),

        'vehicle_pending',  (select count(*) from public.vehicle_catalog_submissions where status = 'pending'),
        'city_pending',     (select count(*) from public.city_submissions where status = 'pending'),

        -- Fourteen days, including the ones with no signups. A series built from the rows that
        -- exist has gaps, and a bar chart with gaps silently rescales its own axis — the quiet
        -- day disappears instead of showing as zero.
        -- Signed-in accounts that opened the app in the window.
        'active_1d',        (select count(distinct owner_id) from public.user_devices
                             where last_seen_at >= now() - interval '1 day'),
        'active_prev_1d',   (select count(distinct owner_id) from public.user_devices
                             where last_seen_at >= now() - interval '2 days'
                               and last_seen_at <  now() - interval '1 day'),
        'active_7d',        (select count(distinct owner_id) from public.user_devices
                             where last_seen_at >= now() - interval '7 days'),
        'active_30d',       (select count(distinct owner_id) from public.user_devices
                             where last_seen_at >= now() - interval '30 days'),

        -- Fourteen days, on the same axis as the signup series. A day nobody opened the app is
        -- a zero rather than a gap, for the reason that series gives.
        'actives', (
            select coalesce(jsonb_agg(jsonb_build_object('d', to_char(day, 'YYYY-MM-DD'), 'n', n) order by day), '[]'::jsonb)
              from (
                select d::date as day,
                       (select count(distinct ud.owner_id) from public.user_devices ud
                         where ud.last_seen_at::date = d::date) as n
                  from generate_series(current_date - 13, current_date, interval '1 day') as d
              ) series
        ),

        'signups', (
            select coalesce(jsonb_agg(jsonb_build_object('d', to_char(day, 'YYYY-MM-DD'), 'n', n) order by day), '[]'::jsonb)
              from (
                select d::date as day,
                       (select count(*) from public.profiles p where p.created_at::date = d::date) as n
                  from generate_series(current_date - 13, current_date, interval '1 day') as d
              ) series
        ),

        'activity', (
            select coalesce(jsonb_agg(row_to_json(a) order by a.at desc), '[]'::jsonb)
              from (
                select l.action, l.subject_type, l.at, u.email as actor
                  from public.admin_audit_log l
                  left join public.admin_users u on u.id = l.actor_admin_id
                 order by l.at desc
                 limit 8
              ) a
        )
    ) into result;

    return result;
end;
$function$;

comment on function public.admin_dashboard() is
    'Counts, a 14-day signup series, a 14-day signed-in-active series and the last few '
    'audit entries, for /admin/dashboard. Actives count accounts, not anonymous installs. '
    'Numbers only — the panel owns the wording.';

revoke all on function public.admin_dashboard() from public, anon;
grant execute on function public.admin_dashboard() to authenticated;

-- The actives queries scan by date; without this every dashboard read is a sequential scan of
-- the whole table, and it is read on every visit to /admin.
create index if not exists idx_user_devices_last_seen
    on public.user_devices (last_seen_at desc);

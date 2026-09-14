-- Everything the dashboard draws, in one call.
--
-- One function rather than a dozen counted reads, for two reasons. A dashboard that fires
-- eleven queries shows eleven separate loading states and settles in a visibly staggered way.
-- And a count done by fetching rows and measuring the array in the browser reports the *page's*
-- count as the total — the same mistake `admin_billing_summary` exists to avoid.
--
-- Returns counts and raw series only. None of the wording is here: the panel composes
-- "3 tickets need a reply" from a number, because that sentence has to be translatable and SQL
-- is the wrong place to keep a string table.
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
    'Counts, a 14-day signup series and the last few audit entries, for /admin/dashboard. '
    'Numbers only — the panel owns the wording.';

revoke all on function public.admin_dashboard() from public, anon;
grant execute on function public.admin_dashboard() to authenticated;

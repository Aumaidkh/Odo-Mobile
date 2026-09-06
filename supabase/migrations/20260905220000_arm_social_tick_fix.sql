-- Two corrections to 20260905210000, both found by calling the thing.
--
-- **1. Anonymous callers could arm the scheduler.** Postgres grants EXECUTE on a function to
-- PUBLIC by default, and the previous body let a caller through when `auth.uid()` was null —
-- which is true of the migration role and equally true of `anon`. Verified from a shell with
-- nothing but the publishable key. It now asks for the permission and nothing else.
--
-- **2. Arming needed the SQL editor.** The values came from Vault, so turning the schedule on
-- meant creating two secrets by hand on every project. They are arguments now: the admin panel
-- already knows its own project URL and key, so the button can pass what it is already using
-- and the schedule is armed by a person who is signed in, from the screen that owns it.
--
-- Vault is no longer read. Nothing project-specific is committed either way — the difference
-- is only who supplies the two values, and now it is the caller.

drop function if exists public.arm_social_tick();

/**
 * Schedule social-tick against [p_url] with [p_key] as the bearer, every 15 minutes.
 *
 * Returns what happened rather than raising: the caller is a button, and a sentence tells
 * whoever pressed it more than an exception would.
 *
 * [p_key] should be the anon key. `tick` needs a caller PostgREST will accept and nothing
 * more; it does its own work with the service role from its own environment.
 */
create or replace function public.arm_social_tick(p_url text, p_key text)
returns text
language plpgsql
security definer
set search_path = public
as $function$
begin
    if not public.admin_has('blog.write') then
        raise exception 'not permitted';
    end if;
    if coalesce(trim(p_url), '') = '' or coalesce(trim(p_key), '') = '' then
        raise exception 'a project url and key are both required';
    end if;

    -- Unscheduled first so this can be called again to move the job rather than failing on
    -- the name — which is what happens when a key is rotated.
    if exists (select 1 from cron.job where jobname = 'social-tick') then
        perform cron.unschedule('social-tick');
    end if;

    perform cron.schedule(
        'social-tick',
        '*/15 * * * *',
        format(
            $job$ select net.http_post(
                     url     := %L,
                     headers := %L::jsonb,
                     body    := '{}'::jsonb) $job$,
            rtrim(p_url, '/') || '/functions/v1/tick',
            json_build_object(
                'Authorization', 'Bearer ' || p_key,
                'Content-Type', 'application/json'
            )::text
        )
    );

    return 'social-tick scheduled every 15 minutes';
end;
$function$;

/** Whether the schedule is running, for the button to say so. Never the command, which holds the key. */
create or replace function public.social_tick_status()
returns text
language plpgsql
security definer
set search_path = public
as $function$
declare v_schedule text;
begin
    if not public.admin_has('blog.write') then
        raise exception 'not permitted';
    end if;
    select schedule into v_schedule from cron.job where jobname = 'social-tick';
    return coalesce(v_schedule, '');
end;
$function$;

create or replace function public.disarm_social_tick()
returns text
language plpgsql
security definer
set search_path = public
as $function$
begin
    if not public.admin_has('blog.write') then
        raise exception 'not permitted';
    end if;
    if exists (select 1 from cron.job where jobname = 'social-tick') then
        perform cron.unschedule('social-tick');
        return 'social-tick stopped';
    end if;
    return 'social-tick was not running';
end;
$function$;

grant execute on function public.arm_social_tick(text, text) to authenticated;
grant execute on function public.social_tick_status() to authenticated;
grant execute on function public.disarm_social_tick() to authenticated;

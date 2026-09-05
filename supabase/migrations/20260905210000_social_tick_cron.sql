-- The one cron row the social pipeline needs, and the switch that arms it.
--
-- `social-tick` runs every 15 minutes, reads public.social_schedule, and calls `generate` for
-- each slot that is due. One row instead of three hard-coded `cron.schedule` calls, because a
-- schedule the admin panel can edit cannot live in a migration.
--
-- **Nothing here names a project.** A migration that carried a URL and a key would carry one
-- project's, and running it on the other would point production's cron at development. The
-- two values come from Vault instead, so this same file is correct everywhere.
--
-- **Arming is a function, not a migration step**, because the secrets usually arrive after the
-- migration does. Re-running a migration is not something anybody should have to do to turn a
-- schedule on:
--
--   select vault.create_secret('https://<ref>.supabase.co', 'social_tick_url');
--   select vault.create_secret('<anon key>', 'social_tick_key');
--   select public.arm_social_tick();
--
-- The anon key, not the service role one. `tick` needs a caller PostgREST will accept and
-- nothing more; it does its own work with the service role from its own environment.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.

create extension if not exists pg_cron with schema extensions;
create extension if not exists pg_net  with schema extensions;

/**
 * Schedule social-tick, or say why it could not be.
 *
 * Returns what happened rather than raising: this is called from a SQL editor by a person who
 * wants to know whether it worked, and an exception tells them less than a sentence does.
 */
create or replace function public.arm_social_tick()
returns text
language plpgsql
security definer
set search_path = public
as $function$
declare
    v_url text;
    v_key text;
begin
    if not public.admin_has('blog.write') and auth.uid() is not null then
        raise exception 'not permitted';
    end if;

    select decrypted_secret into v_url from vault.decrypted_secrets where name = 'social_tick_url';
    select decrypted_secret into v_key from vault.decrypted_secrets where name = 'social_tick_key';

    if v_url is null or v_key is null then
        return 'not armed: vault secrets social_tick_url / social_tick_key are missing';
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
            v_url || '/functions/v1/tick',
            json_build_object(
                'Authorization', 'Bearer ' || v_key,
                'Content-Type', 'application/json'
            )::text
        )
    );

    return 'social-tick scheduled every 15 minutes';
end;
$function$;

grant execute on function public.arm_social_tick() to authenticated;

-- Arms it now on a project whose secrets are already in place, and says so in the log
-- otherwise. Running as the migration role, `auth.uid()` is null, which is what lets this
-- call through the permission check above.
do $$
begin
    raise notice '%', public.arm_social_tick();
end $$;

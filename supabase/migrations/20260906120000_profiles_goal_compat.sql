-- Keep `profiles.onboarding_goal` working for installed builds, and stop it losing data.
--
-- `profile_answers` is where goals live now (#394), but v1.3.3.3 — what is on Play — still
-- sends and reads the column. Dropping it makes every one of those installations' profile
-- push fail with PGRST204, silently: the row stays PENDING and the owner is told nothing.
--
-- So the column stays, and a trigger forwards a change made through it into the new table.
-- Without this an old client's goal change is written to a column nothing reads any more.
-- The drop itself waits for a release without the field; this is what makes it a no-op.

create or replace function public.forward_onboarding_goal()
    returns trigger
    language plpgsql
    security definer
    set search_path = public
as $$
declare
    value text := upper(new.onboarding_goal::text);
begin
    -- Only values a client can read back. Anything else would become an answer nothing
    -- recognises, which is worse than leaving it in the column.
    if value is null or value not in ('SELL_SOON', 'TRACK_COSTS', 'NEVER_MISS_RENEWAL') then
        return new;
    end if;

    -- One live row per single-select question. The old goal is tombstoned rather than
    -- deleted, or a second device never learns it was replaced.
    update public.profile_answers
       set deleted_at = now(), updated_at = now()
     where owner_id = new.id
       and question_key = 'goal.v1'
       and answer_value <> value
       and deleted_at is null;

    -- The unique index is total, so re-selecting revives the existing row and keeps the id
    -- sync depends on. `updated_at` is the delta cursor: now(), or no device pulls this.
    insert into public.profile_answers (
        id, owner_id, question_key, answer_value, answered_at, created_at, updated_at
    )
    values (gen_random_uuid(), new.id, 'goal.v1', value, now(), now(), now())
    on conflict (owner_id, question_key, answer_value) do update
       set deleted_at = null, answered_at = now(), updated_at = now();

    return new;
end;
$$;

comment on function public.forward_onboarding_goal is
    'Mirrors a write of profiles.onboarding_goal into profile_answers, so a build that '
    'predates the questionnaire can still change a goal. Remove with the column.';

-- Only where the column is still there. Development has already dropped it, and a project
-- built from scratch never had a client writing to it — both want the function and no
-- trigger, so that dropping the column later needs no third migration.
do $do$
begin
    if not exists (
        select 1 from information_schema.columns
         where table_schema = 'public' and table_name = 'profiles'
           and column_name = 'onboarding_goal'
    ) then
        raise notice 'profiles.onboarding_goal is gone; no compatibility trigger needed';
        return;
    end if;

    -- Two triggers, not one: a WHEN clause may only name NEW and OLD, and OLD does not
    -- exist on an insert. `update of` narrows the second to writes that name the column,
    -- and its guard to writes that change it — PostgREST sends every field on every
    -- profile push, nulls included, so without the guard it would fire on all of them.
    execute $t$
        drop trigger if exists trg_profiles_forward_goal on public.profiles;
        drop trigger if exists trg_profiles_forward_goal_insert on public.profiles;
        drop trigger if exists trg_profiles_forward_goal_update on public.profiles;

        create trigger trg_profiles_forward_goal_insert
            after insert on public.profiles
            for each row
            when (new.onboarding_goal is not null)
            execute function public.forward_onboarding_goal();

        create trigger trg_profiles_forward_goal_update
            after update of onboarding_goal on public.profiles
            for each row
            when (new.onboarding_goal is not null
                  and new.onboarding_goal is distinct from old.onboarding_goal)
            execute function public.forward_onboarding_goal();

        comment on column public.profiles.onboarding_goal is
            'Deprecated — profile_answers holds goals now. Kept because installed builds still '
            'send it; a write here is forwarded by trg_profiles_forward_goal. Drop it with them.';
    $t$;
end;
$do$;

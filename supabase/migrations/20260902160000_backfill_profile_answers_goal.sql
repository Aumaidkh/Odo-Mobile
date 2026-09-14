-- Backfill `profiles.onboarding_goal` into `profile_answers` as `goal.v1` (#394).
--
-- Every owner who answered the goal question before the questionnaire existed has it in a
-- column. This copies it across so one place answers "what are this owner's goals" for
-- everybody. The column is NOT dropped here — that is a later release, so a rollback still
-- has somewhere to read from.
--
-- Idempotent, and safe to run before or after any device has synced.

do $do$
declare
    migrated integer;
    skipped  integer;
begin
    insert into public.profile_answers (
        id, owner_id, question_key, answer_value, answered_at, created_at, updated_at
    )
    select
        gen_random_uuid(),
        p.id,
        'goal.v1',
        -- The client lowercases on the way out and uppercases on the way in, so this column
        -- holds `sell_soon` while an answer holds the domain constant `SELL_SOON`. The cast
        -- is what makes this work whether the column is text or an enum type.
        upper(p.onboarding_goal::text),
        -- When they actually answered. Falls back to the row's creation for profiles that
        -- predate the completion stamp.
        coalesce(p.onboarding_completed_at, p.created_at),
        now(),
        -- **now(), deliberately not the historical time.** `updated_at` is the sync cursor:
        -- the delta pull is `updated_at > cursor`, so a row stamped in the past would be
        -- invisible to every device that had already synced this table, forever. The moment
        -- of the backfill is the moment the row became available to pull.
        now()
    from public.profiles p
    where p.onboarding_goal is not null
      and p.deleted_at is null
      -- Only values the app can read back. Anything else would become an answer no client
      -- recognises, which is worse than leaving it in the column.
      and upper(p.onboarding_goal::text) in ('SELL_SOON', 'TRACK_COSTS', 'NEVER_MISS_RENEWAL')
      -- Never overwrite what the questionnaire already stored.
      --
      -- Deliberately counts tombstoned rows too. An owner who opened the new screen and
      -- deselected every goal has a soft-deleted row and no live one; treating that as
      -- "unanswered" would resurrect an answer they had just removed.
      and not exists (
          select 1
          from public.profile_answers a
          where a.owner_id = p.id
            and a.question_key = 'goal.v1'
      );

    get diagnostics migrated = row_count;

    select count(*) into skipped
    from public.profiles p
    where p.onboarding_goal is not null
      and p.deleted_at is null
      and upper(p.onboarding_goal::text) not in ('SELL_SOON', 'TRACK_COSTS', 'NEVER_MISS_RENEWAL');

    raise notice 'profile_answers backfill: % migrated, % skipped as unrecognised', migrated, skipped;
end;
$do$;

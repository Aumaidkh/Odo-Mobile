-- Drop `profiles.onboarding_goal` (#394). `profile_answers` is now the only place goals live.
--
-- **Runs after 20260902160000, which copies the column into that table.** Migrations apply in
-- timestamp order, so a project catching up from scratch creates the table, backfills it, and
-- only then loses the column. A project that skipped the backfill would lose the data.
--
-- Idempotent: re-running finds no column and does nothing.
--
-- **Still not safe on production.** v1.3.3.3 is what is on Play and still sends the column,
-- so dropping it makes every one of those profile pushes fail with PGRST204, silently.
-- 20260906120000 keeps them working and forwards their writes into profile_answers; this
-- runs once a release without the field is live and adopted. The trigger goes with it.

alter table public.profiles drop column if exists onboarding_goal;
drop trigger if exists trg_profiles_forward_goal_insert on public.profiles;
drop trigger if exists trg_profiles_forward_goal_update on public.profiles;
drop function if exists public.forward_onboarding_goal();

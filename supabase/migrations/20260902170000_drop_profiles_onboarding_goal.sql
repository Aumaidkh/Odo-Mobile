-- Drop `profiles.onboarding_goal` (#394). `profile_answers` is now the only place goals live.
--
-- **Runs after 20260902160000, which copies the column into that table.** Migrations apply in
-- timestamp order, so a project catching up from scratch creates the table, backfills it, and
-- only then loses the column. A project that skipped the backfill would lose the data.
--
-- Idempotent: re-running finds no column and does nothing.

alter table public.profiles drop column if exists onboarding_goal;

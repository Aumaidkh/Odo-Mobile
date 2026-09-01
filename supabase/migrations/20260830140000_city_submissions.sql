-- `city_submissions` is the inbox "my city isn't listed" reports land in, mirroring
-- `vehicle_catalog_submissions` (20260829120000_vehicle_catalog.sql).
--
-- The `cities` table itself already exists (docs/SUPABASE_BOOTSTRAP.md §2) — this migration
-- only adds the submission inbox.
--
-- Unlike a vehicle submission, this one carries no make/model, just a city name: `state` and
-- `tier` are nullable because the app never asks for them, only a name ("Srinagar"). A reviewer
-- fills both in by hand alongside `status = 'accepted'` — `cities.state`/`cities.tier` are
-- `NOT NULL`, so a row with either missing can never be promoted.
--
-- Promotion here is a scheduled job (20260830140100_promote_city_submissions.sql), not a
-- trigger, and it deletes the row once it lands in `cities` — this table keeps no accepted-row
-- history, unlike `vehicle_catalog_submissions`.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.

create table if not exists public.city_submissions (
    id         uuid primary key default gen_random_uuid(),
    owner_id   uuid not null references public.profiles (id) on delete cascade,
    name       text not null,
    state      text,
    tier       smallint check (tier in (1, 2, 3)),
    status     text not null default 'pending' check (status in ('pending', 'accepted', 'rejected')),
    created_at timestamptz not null default now()
);

comment on table public.city_submissions is
    'Cities owners typed that were not in the cities lookup. Write-only from the client '
    '(insert own rows); state/tier are filled in by a reviewer alongside status before the '
    'promote job can move a row into cities — never auto-merged.';

alter table public.city_submissions enable row level security;

-- Insert-only from the client, and only under the caller's own id. No select/update/delete
-- policy at all: an owner cannot read back their own or anyone else's submissions, and the
-- only path to changing status (or filling in state/tier) is the SQL editor's service-role
-- access, which bypasses RLS.
drop policy if exists city_submissions_insert_own on public.city_submissions;
create policy city_submissions_insert_own on public.city_submissions
    for insert to authenticated
    with check (owner_id = auth.uid());

create index if not exists idx_city_submissions_pending
    on public.city_submissions (created_at) where status = 'pending';

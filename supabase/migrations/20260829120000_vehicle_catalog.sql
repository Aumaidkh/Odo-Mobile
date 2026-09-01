-- Shared vehicle catalog: `vehicle_makes` / `vehicle_models` back the make and model pickers
-- for every installed app, replacing the old "ship a bigger list in the next release" model.
-- `vehicle_catalog_submissions` is the inbox "my car isn't listed" reports land in.
--
-- Unlike every other table in this project, `vehicle_makes`/`vehicle_models` carry no
-- `owner_id` — they are public reference data everyone reads the same rows from, which is
-- also why they sit outside the Syncable/Synchronizer engine (SYNC_DESIGN's push/pull is
-- per-owner; VehicleCatalogRefresher does a plain unscoped fetch-and-replace instead).
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.

create table if not exists public.vehicle_makes (
    id            text primary key,
    name          text not null unique,
    display_order bigint not null
);

comment on table public.vehicle_makes is
    'Public reference data for the make picker. No owner_id: every account reads the same '
    'rows. Client apps only ever select from this table — rows are inserted/updated by the '
    'reviewer running seed_vehicle_catalog.sql or promoting a vehicle_catalog_submissions row, '
    'never by the app itself.';

create table if not exists public.vehicle_models (
    id            text primary key,
    make_id       text not null references public.vehicle_makes (id) on delete cascade,
    name          text not null,
    variant       text,
    display_order bigint not null
);

comment on column public.vehicle_models.variant is
    'Null for the trim-less row every model also gets locally (VehicleSeedData.kt) — an owner '
    'whose exact trim is missing can still name their car. The seed script mirrors that: one '
    'row with variant null per model, plus one row per trim.';

create index if not exists idx_vehicle_models_make on public.vehicle_models (make_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- Row-level security. Public, read-only from the client's side: every account may read every
-- row, and nobody may write — the catalog only ever changes from the SQL editor (a reviewer
-- running seed_vehicle_catalog.sql or promoting a submission), never from the app.
-- ─────────────────────────────────────────────────────────────────────────────

alter table public.vehicle_makes enable row level security;

drop policy if exists vehicle_makes_read_all on public.vehicle_makes;
create policy vehicle_makes_read_all on public.vehicle_makes
    for select to anon, authenticated
    using (true);

alter table public.vehicle_models enable row level security;

drop policy if exists vehicle_models_read_all on public.vehicle_models;
create policy vehicle_models_read_all on public.vehicle_models
    for select to anon, authenticated
    using (true);


-- ─────────────────────────────────────────────────────────────────────────────
-- vehicle_catalog_submissions — "my car isn't listed", held for review.
--
-- Deliberately not merged into vehicle_makes/vehicle_models automatically: an unreviewed
-- typo or duplicate spelling would otherwise be selectable by every other owner within a
-- refresh. A reviewer promotes a row by hand (insert into vehicle_makes/vehicle_models,
-- update status here) — there is no review UI in this pass.
-- ─────────────────────────────────────────────────────────────────────────────

create table if not exists public.vehicle_catalog_submissions (
    id         uuid primary key default gen_random_uuid(),
    owner_id   uuid not null references public.profiles (id) on delete cascade,
    make       text not null,
    model      text not null,
    variant    text,
    status     text not null default 'pending' check (status in ('pending', 'accepted', 'rejected')),
    created_at timestamptz not null default now()
);

comment on table public.vehicle_catalog_submissions is
    'Cars owners named that were not in vehicle_makes/vehicle_models. Write-only from the '
    'client (insert own rows); reviewed and promoted by hand from the SQL editor, never '
    'auto-merged into the live catalog.';

alter table public.vehicle_catalog_submissions enable row level security;

-- Insert-only from the client, and only under the caller's own id. No select/update/delete
-- policy at all: an owner cannot read back their own or anyone else's submissions, and the
-- only path to changing status is the SQL editor's service-role access, which bypasses RLS.
drop policy if exists vehicle_catalog_submissions_insert_own on public.vehicle_catalog_submissions;
create policy vehicle_catalog_submissions_insert_own on public.vehicle_catalog_submissions
    for insert to authenticated
    with check (owner_id = auth.uid());

create index if not exists idx_vehicle_catalog_submissions_pending
    on public.vehicle_catalog_submissions (created_at) where status = 'pending';

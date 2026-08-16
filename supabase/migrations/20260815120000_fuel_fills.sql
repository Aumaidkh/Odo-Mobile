-- `fuel_fills` — one tank of fuel, as the owner confirmed it.
--
-- The client half of this is `SyncEntity.FUEL_FILLS`, pushed after `CARS` because a fill
-- references a car and nothing references a fill. Until this table existed the rows sat
-- `PENDING` on the device forever: there was deliberately no `Syncable` for them, because one
-- posting to a table that is not there only manufactures failures.
--
-- **Only confirmed fills reach here.** A detection the owner has not answered lives in the
-- device's own `pending_fills`, which has no sync columns at all and mirrors nothing on this
-- server. Odo's guess about somebody's payment is not a record of anything and does not leave
-- the phone.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.

create table if not exists public.fuel_fills (
    -- Client-generated, which is what makes the push an idempotent upsert: a retry after a
    -- lost response updates the same row instead of creating a twin.
    id              uuid primary key,
    car_id          uuid not null references public.cars (id) on delete cascade,
    owner_id        uuid not null references public.profiles (id) on delete cascade,

    filled_on       date not null,

    -- Nullable, and this is the one column worth reading twice. A detected fill reaches the
    -- owner while they are still standing at the pump, where the dashboard reading is the one
    -- number out of reach — so the app stopped demanding it rather than losing the whole
    -- record to insist on one field. A fill without a reading still counts towards what fuel
    -- cost; only the measured mileage skips it.
    odometer_km     integer check (odometer_km is null or odometer_km >= 0),

    -- Thousandths of a unit: 32.45 litres is 32450. Integer for the same reason money is
    -- integer paise — a mileage divided out of a float quantity drifts.
    quantity_milli  bigint not null check (quantity_milli > 0),
    fuel_unit       text   not null check (fuel_unit in ('LITRE', 'KILOGRAM', 'KILOWATT_HOUR')),
    amount_paise    bigint not null check (amount_paise >= 0),

    station_name    text,
    transaction_ref text,

    -- Which capture channel produced the row. Plain text with a CHECK rather than a Postgres
    -- enum, so the client can send its Kotlin constant name unchanged and adding a channel is
    -- one statement rather than an enum migration. Defaulted because rows written before this
    -- column existed have no channel to name.
    entry_source    text not null default 'MANUAL'
                    check (entry_source in ('DETECTED', 'PUMP_OCR', 'PREFILLED', 'MANUAL')),

    created_at      timestamptz not null,
    updated_at      timestamptz not null,
    -- Soft delete. Tombstones are pulled, not filtered out: it is the only way a second device
    -- learns a fill was deleted on the first (SYNC_DESIGN §6).
    deleted_at      timestamptz
);

comment on table public.fuel_fills is
    'Confirmed fuel fills, synced from the app. Unanswered detections stay on the device in '
    'its local pending_fills and are never pushed here.';

comment on column public.fuel_fills.odometer_km is
    'Optional. Null means the owner confirmed the fill without a reading, which is normal for '
    'a fill detected at the pump. Not a zero, and not to be defaulted to one — a zero would '
    'silently poison the measured mileage.';

comment on column public.fuel_fills.updated_at is
    'Written by the client and never by this server. It is the sync cursor and the '
    'last-write-wins comparison on both sides, and the value the device stores as '
    'remote_version. A trigger that rewrote it here would make every pushed row look changed '
    'on the next pull. There is deliberately no updated_at trigger on this table.';


-- ─────────────────────────────────────────────────────────────────────────────
-- Row-level security. A fill belongs to exactly one account and is never shared.
-- ─────────────────────────────────────────────────────────────────────────────

alter table public.fuel_fills enable row level security;

drop policy if exists fuel_fills_select_own on public.fuel_fills;
create policy fuel_fills_select_own on public.fuel_fills
    for select to authenticated
    using (owner_id = auth.uid());

drop policy if exists fuel_fills_insert_own on public.fuel_fills;
create policy fuel_fills_insert_own on public.fuel_fills
    for insert to authenticated
    with check (owner_id = auth.uid());

-- `using` and `with check` both, on purpose. Without the `with check`, an owner could update
-- a row of their own and set `owner_id` to somebody else's on the way past.
drop policy if exists fuel_fills_update_own on public.fuel_fills;
create policy fuel_fills_update_own on public.fuel_fills
    for update to authenticated
    using (owner_id = auth.uid())
    with check (owner_id = auth.uid());

-- Hard delete is only used by the sign-out wipe. Ordinary removal is a tombstone, which is an
-- update.
drop policy if exists fuel_fills_delete_own on public.fuel_fills;
create policy fuel_fills_delete_own on public.fuel_fills
    for delete to authenticated
    using (owner_id = auth.uid());


-- ─────────────────────────────────────────────────────────────────────────────
-- Indexes. The delta pull is the only read: `car_id = ? and updated_at > cursor`, ordered by
-- `updated_at` so the cursor advances monotonically.
-- ─────────────────────────────────────────────────────────────────────────────

create index if not exists idx_fuel_fills_car_updated
    on public.fuel_fills (car_id, updated_at);

-- RLS runs `owner_id = auth.uid()` on every row the pull touches, so it wants an index of its
-- own rather than riding the one above.
create index if not exists idx_fuel_fills_owner
    on public.fuel_fills (owner_id);

-- `purchase_claims` + `credit_spends` — the consumables the owner bought one at a time.
--
-- Why they live here at all: the store reports a completed purchase forever, so a record of
-- what has been honoured that only lives on the device lets a reinstall honour the same
-- purchase again, without limit. Owner-scoped, a purchase is honoured once for the person who
-- paid for it.
--
-- **The balance is not stored.** It is what these two tables say: everything granted, minus
-- everything spent. A stored counter has no merge — two devices each holding "2 left" cannot
-- be reconciled — while rows union with no conflict at all.
--
-- The client writes both. It is the device that reads the purchase from the store, and making
-- the server the only writer would need a RevenueCat webhook and would stop an offline
-- purchase crediting until it landed. What that costs is stated plainly: a tampered client
-- can write itself a claim. It could already write itself the local balance, so this changes
-- the reinstall loop, not the threat model.
--
-- The free scan tally is deliberately not here. It stays device-local and still resets on
-- reinstall (PAYWALL_PLAN decision 4) — a commercial limit on a feature that costs nothing to
-- run is a different question from money that changed hands.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.

create table if not exists public.purchase_claims (
    -- Client-generated, so a retried push upserts instead of creating a twin.
    id             uuid primary key,
    owner_id       uuid not null references public.profiles (id) on delete cascade,

    -- The store's own transaction id, except for the one `legacy-…` row per kind that the
    -- client migration writes for a balance predating this table.
    transaction_id text not null check (length(transaction_id) > 0),

    -- What the purchase was worth when it was honoured, not what the product grants today.
    -- A release that changes what a pack contains must not change what an old purchase was
    -- worth.
    scan_checks    integer not null default 0 check (scan_checks >= 0),
    record_exports integer not null default 0 check (record_exports >= 0),

    claimed_at     timestamptz not null,
    created_at     timestamptz not null,
    updated_at     timestamptz not null,
    deleted_at     timestamptz
);

comment on table public.purchase_claims is
    'One row per store transaction this owner has honoured, carrying what it granted. Makes '
    'a consumable purchase creditable exactly once per owner rather than once per install.';

comment on column public.purchase_claims.updated_at is
    'Written by the client, never by this server. It is the sync cursor on both sides. A '
    'trigger that rewrote it would make every pushed row look changed on the next pull.';

-- What the whole table is for. Total rather than partial: a tombstone and a live row sharing
-- a transaction would let the same purchase be claimed again.
create unique index if not exists uq_purchase_claims_owner_transaction
    on public.purchase_claims (owner_id, transaction_id);

-- The delta pull is `owner_id = ? and updated_at > cursor`. The unique index leads with
-- `owner_id` but not `updated_at` second, so the pull needs its own.
create index if not exists idx_purchase_claims_owner_updated
    on public.purchase_claims (owner_id, updated_at);


create table if not exists public.credit_spends (
    id         uuid primary key,
    owner_id   uuid not null references public.profiles (id) on delete cascade,

    -- 'BILL_CHECK' or 'RECORD_EXPORT'. Text rather than an enum: the set changes with app
    -- releases and the database should not need a migration to keep up with one.
    kind       text not null check (length(kind) > 0),

    spent_at   timestamptz not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    deleted_at timestamptz
);

comment on table public.credit_spends is
    'One row per credit consumed. A row rather than a counter so two devices spending '
    'offline merge by union, where two counters would have to be reconciled and one would lose.';

comment on column public.credit_spends.updated_at is
    'Written by the client, never by this server. It is the sync cursor on both sides.';

-- No unique key beyond the primary one: a spend has no natural key, and two devices spending
-- offline are two different events that both count.
create index if not exists idx_credit_spends_owner_updated
    on public.credit_spends (owner_id, updated_at);


-- Row-level security. A purchase belongs to one account and is never shared.

alter table public.purchase_claims enable row level security;
alter table public.credit_spends enable row level security;

drop policy if exists purchase_claims_select_own on public.purchase_claims;
create policy purchase_claims_select_own on public.purchase_claims
    for select to authenticated
    using (owner_id = auth.uid());

drop policy if exists purchase_claims_insert_own on public.purchase_claims;
create policy purchase_claims_insert_own on public.purchase_claims
    for insert to authenticated
    with check (owner_id = auth.uid());

-- `using` and `with check` both: without the check, an owner could update their own row and
-- set `owner_id` to somebody else's on the way past.
drop policy if exists purchase_claims_update_own on public.purchase_claims;
create policy purchase_claims_update_own on public.purchase_claims
    for update to authenticated
    using (owner_id = auth.uid())
    with check (owner_id = auth.uid());

-- Hard delete is only the sign-out wipe, which runs on the device. Nothing on the server
-- deletes a claim: a purchase that stops being recorded is a purchase that can be claimed
-- again.
drop policy if exists purchase_claims_delete_own on public.purchase_claims;
create policy purchase_claims_delete_own on public.purchase_claims
    for delete to authenticated
    using (owner_id = auth.uid());

drop policy if exists credit_spends_select_own on public.credit_spends;
create policy credit_spends_select_own on public.credit_spends
    for select to authenticated
    using (owner_id = auth.uid());

drop policy if exists credit_spends_insert_own on public.credit_spends;
create policy credit_spends_insert_own on public.credit_spends
    for insert to authenticated
    with check (owner_id = auth.uid());

drop policy if exists credit_spends_update_own on public.credit_spends;
create policy credit_spends_update_own on public.credit_spends
    for update to authenticated
    using (owner_id = auth.uid())
    with check (owner_id = auth.uid());

drop policy if exists credit_spends_delete_own on public.credit_spends;
create policy credit_spends_delete_own on public.credit_spends
    for delete to authenticated
    using (owner_id = auth.uid());


-- Restriction (20260831170000_restriction_allows_reads.sql): a restricted account keeps its
-- reads and loses its writes. Three policies rather than one `for all`, because `for all`
-- includes SELECT and would turn `read_only` into a silent block. These tables carry their
-- own because that migration's loop has already run.

drop policy if exists purchase_claims_not_restricted_insert on public.purchase_claims;
create policy purchase_claims_not_restricted_insert on public.purchase_claims
    as restrictive for insert to authenticated
    with check (not public.is_restricted_writer(auth.uid()));

drop policy if exists purchase_claims_not_restricted_update on public.purchase_claims;
create policy purchase_claims_not_restricted_update on public.purchase_claims
    as restrictive for update to authenticated
    using (not public.is_restricted_writer(auth.uid()))
    with check (not public.is_restricted_writer(auth.uid()));

drop policy if exists purchase_claims_not_restricted_delete on public.purchase_claims;
create policy purchase_claims_not_restricted_delete on public.purchase_claims
    as restrictive for delete to authenticated
    using (not public.is_restricted_writer(auth.uid()));

drop policy if exists credit_spends_not_restricted_insert on public.credit_spends;
create policy credit_spends_not_restricted_insert on public.credit_spends
    as restrictive for insert to authenticated
    with check (not public.is_restricted_writer(auth.uid()));

drop policy if exists credit_spends_not_restricted_update on public.credit_spends;
create policy credit_spends_not_restricted_update on public.credit_spends
    as restrictive for update to authenticated
    using (not public.is_restricted_writer(auth.uid()))
    with check (not public.is_restricted_writer(auth.uid()));

drop policy if exists credit_spends_not_restricted_delete on public.credit_spends;
create policy credit_spends_not_restricted_delete on public.credit_spends
    as restrictive for delete to authenticated
    using (not public.is_restricted_writer(auth.uid()));

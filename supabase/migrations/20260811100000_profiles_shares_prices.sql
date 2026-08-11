-- "Share prices anonymously" — the one privacy switch that belongs to the account.
--
-- The other two the owner sees (keep trip routes, usage analytics) describe a phone and live
-- in the client's device-local `app_settings`, which mirrors nothing here. This one is
-- different: the city benchmark is aggregated on the server, from rows belonging to this
-- account, so the answer has to travel with the account.
--
-- Defaults to true, which is what the app ships with and what Privacy & permissions shows an
-- owner who has never touched it. Existing rows therefore keep contributing, which is the
-- same answer they were already giving.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.

alter table public.profiles
    add column if not exists shares_prices boolean not null default true;

comment on column public.profiles.shares_prices is
    'Whether this owner''s service-log prices may feed the city fairness benchmark. Written '
    'from the app''s Privacy & permissions screen and synced up. Enforced by the '
    'contribute_fairness RLS policy below, not by the read-side RPC.';


-- ─────────────────────────────────────────────────────────────────────────────
-- Enforcement.
--
-- **Not on the read side.** `fairness_data_points` is deliberately de-identified — no
-- owner_id, no car_id, no bill_id — which is exactly what makes the pool privacy-safe. There
-- is nothing on a data point to join back to a profile, so `get_fairness_estimate` cannot
-- filter by who contributed it and must not be changed to try.
--
-- The gate therefore belongs at the moment of contribution: an opted-out owner's price never
-- enters the pool in the first place. That is also the stronger guarantee — "we do not store
-- it" rather than "we store it and promise not to use it".
--
-- The trade-off, stated plainly: prices contributed *before* an owner opted out stay in the
-- pool, because nothing links them back to be removed. They are unlinkable averages by
-- construction, and the in-app copy promises what happens next rather than what has already
-- happened.
-- ─────────────────────────────────────────────────────────────────────────────

drop policy if exists contribute_fairness on public.fairness_data_points;
create policy contribute_fairness on public.fairness_data_points
    for insert to authenticated
    with check (
        exists (
            select 1
            from public.profiles p
            where p.id = auth.uid()
              and p.shares_prices
        )
    );

-- The policy runs per inserted row, so the lookup wants to be an index hit rather than a
-- scan. `profiles.id` is already the primary key, so this is a partial index on the answer
-- itself — small, and only covering the rows the policy can pass.
create index if not exists idx_profiles_shares_prices
    on public.profiles (id) where shares_prices;

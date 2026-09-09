-- The three sections the panel's design calls for and the schema did not yet support:
-- the service catalogue's intervals and benchmarks, the support queue, and billing.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.


-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Service catalogue.
--
-- `service_categories` already existed as a lookup — a slug, a display name and which fuel
-- types it applies to. What the panel's design shows, and what the fairness engine will want,
-- is the other half: how often a job is due and what it ought to cost.
--
-- Both intervals are nullable and both may be set. A service is due at whichever comes first —
-- "every 10,000 km or 12 months" is one rule, not two — and a column that could hold only one
-- of them would force every such item to lie about the other.
--
-- The benchmark is paise, like every other money column in this project. An integer, because a
-- benchmark divided out of a float drifts.
-- ─────────────────────────────────────────────────────────────────────────────

alter table public.service_categories
    add column if not exists interval_km       integer check (interval_km is null or interval_km > 0),
    add column if not exists interval_months   integer check (interval_months is null or interval_months > 0),
    add column if not exists benchmark_paise   bigint  check (benchmark_paise is null or benchmark_paise >= 0),
    add column if not exists notes             text;

comment on column public.service_categories.interval_km is
    'Due every N km. Null when the item is time-based only. Both this and interval_months may '
    'be set: a service due at whichever comes first is one rule, not two.';

comment on column public.service_categories.benchmark_paise is
    'What this job ought to cost, in paise. A reference figure the fairness verdict reads — '
    'not a price anybody is charged.';

drop policy if exists service_categories_admin_write on public.service_categories;
create policy service_categories_admin_write on public.service_categories
    for all to authenticated
    using (public.admin_has('fairness.write'))
    with check (public.admin_has('fairness.write'));

-- The existing read policy is `USING (is_active)`, which hides a retired item from the panel
-- that has to restore it. Same trap the cities catalog had, and the same fix — except this
-- table is not delta-synced, so the reason here is only the admin's own visibility.
drop policy if exists service_categories_admin_read on public.service_categories;
create policy service_categories_admin_read on public.service_categories
    for select to authenticated
    using (public.admin_has('fairness.write'));

drop trigger if exists trg_service_categories_audit on public.service_categories;
create trigger trg_service_categories_audit
    after update or delete on public.service_categories
    for each row execute function public.admin_audit();


-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Support tickets.
--
-- New. Nothing in the app files one yet — `:feature:support` sends email — so this is the
-- queue's shape rather than a mirror of something already running. It is deliberately small:
-- a subject, who it belongs to, a status and a priority. Replies are a table of their own when
-- there is something writing them.
--
-- `owner_id` is nullable: somebody can write in before they have an account, and refusing to
-- record that would lose the ticket rather than the account.
-- ─────────────────────────────────────────────────────────────────────────────

create table if not exists public.support_tickets (
    id          bigserial   primary key,
    owner_id    uuid        references public.profiles (id) on delete set null,

    -- Kept alongside owner_id rather than joined for it, because a ticket from somebody with
    -- no account has only this.
    contact     text        not null,

    subject     text        not null,
    body        text        not null default '',
    status      text        not null default 'open'
        check (status in ('open', 'pending', 'resolved', 'closed')),
    priority    text        not null default 'normal'
        check (priority in ('low', 'normal', 'high', 'urgent')),

    assigned_to uuid        references public.admin_users (id) on delete set null,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    resolved_at timestamptz
);

comment on table public.support_tickets is
    'The support queue. Nothing in the app files one yet — :feature:support sends email — so '
    'this is the queue''s shape rather than a mirror of a running system.';

create index if not exists idx_support_tickets_open
    on public.support_tickets (created_at) where status in ('open', 'pending');

alter table public.support_tickets enable row level security;

-- An owner may read their own, so the app can show "your tickets" when it grows one. Nobody
-- outside support may write: a client that could set its own priority to urgent would.
drop policy if exists support_tickets_read_own on public.support_tickets;
create policy support_tickets_read_own on public.support_tickets
    for select to authenticated
    using (owner_id = auth.uid());

drop policy if exists support_tickets_admin_all on public.support_tickets;
create policy support_tickets_admin_all on public.support_tickets
    for all to authenticated
    using (public.admin_has('users.read'))
    with check (public.admin_has('users.read'));

drop trigger if exists trg_support_tickets_updated on public.support_tickets;
create trigger trg_support_tickets_updated before insert or update on public.support_tickets
    for each row execute function public.set_updated_at();

drop trigger if exists trg_support_tickets_audit on public.support_tickets;
create trigger trg_support_tickets_audit
    after update or delete on public.support_tickets
    for each row execute function public.admin_audit();


-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Billing.
--
-- `subscriptions` already exists — owner, tier, status, period end, and a Razorpay id. What it
-- lacked was any way for the panel to read it: the only policies were the owner's own.
--
-- Read-only for the panel, and that is the whole of it. A subscription's truth lives with the
-- store, and an admin editing a row here would produce a number that disagrees with what the
-- owner was actually charged. Comping somebody is what `entitlement_overrides` is for, and it
-- is a different thing said in a different table.
-- ─────────────────────────────────────────────────────────────────────────────

drop policy if exists subscriptions_admin_read on public.subscriptions;
create policy subscriptions_admin_read on public.subscriptions
    for select to authenticated
    using (public.admin_has('users.entitlements.write'));

comment on table public.subscriptions is
    'What the store says somebody is paying for. Read-only from the admin panel: editing a row '
    'here would produce a figure that disagrees with what was actually charged. To grant '
    'access outside a subscription, use entitlement_overrides.';


-- ─────────────────────────────────────────────────────────────────────────────
-- 4. What the billing screen totals.
--
-- Summed in the database rather than by adding up a page of rows in the browser, which would
-- report the page's total as the month's.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function public.admin_billing_summary()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $function$
begin
    if not public.admin_has('users.entitlements.write') then
        raise exception 'not permitted' using errcode = '42501';
    end if;

    return (
        select jsonb_build_object(
            'active',    count(*) filter (where status = 'active'),
            'cancelled', count(*) filter (where status = 'cancelled'),
            'past_due',  count(*) filter (where status = 'past_due'),
            'total',     count(*),
            -- Renewals inside the next month, which is the number somebody planning a
            -- support rota actually wants.
            'renewing_30d', count(*) filter (
                where status = 'active'
                  and current_period_end between now() and now() + interval '30 days'
            )
        )
        from public.subscriptions
    );
end;
$function$;

revoke all on function public.admin_billing_summary() from public, anon;
grant execute on function public.admin_billing_summary() to authenticated;

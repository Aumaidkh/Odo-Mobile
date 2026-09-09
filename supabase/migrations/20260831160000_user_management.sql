-- User management (issue #369): entitlements granted outside a subscription, and restricting
-- an account.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.


-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Entitlements granted by hand.
--
-- Today entitlement is purely store-derived: RevenueCat says Pro or not, and there is no way
-- to give one person access for a support goodwill, a comp, or internal testing. This table
-- is the override, and the client treats it as beating the store in both directions.
--
-- `granted` rather than a bare presence check, so revoking is a row saying no rather than a
-- deleted row saying nothing. The difference matters when the store says yes: without it,
-- "this person has been cut off" is indistinguishable from "nobody has looked at this account".
--
-- `expires_at` null means forever. A comp with an end date is the common case and an override
-- nobody remembers granting is the thing that quietly becomes permanent.
-- ─────────────────────────────────────────────────────────────────────────────

create table if not exists public.entitlement_overrides (
    owner_id   uuid        not null references public.profiles (id) on delete cascade,

    -- 'PRO' for the whole plan, or a single capability name later. Text rather than an enum:
    -- the client's ProFeature list changes with app releases and the database should not need
    -- a migration to keep up with one.
    feature    text        not null,

    granted    boolean     not null,
    expires_at timestamptz,
    reason     text        not null,
    granted_by uuid        references public.admin_users (id) on delete set null,
    granted_at timestamptz not null default now(),
    primary key (owner_id, feature)
);

comment on table public.entitlement_overrides is
    'Entitlement granted or withheld outside the store. The client composes this over the '
    'RevenueCat answer, override winning. A row with granted = false is a deliberate revoke, '
    'which is not the same as no row at all.';

create index if not exists idx_entitlement_overrides_owner on public.entitlement_overrides (owner_id);

alter table public.entitlement_overrides enable row level security;

-- The owner reads their own, because the app has to know. They can never write one: an
-- entitlement a client could grant itself is not an entitlement.
drop policy if exists entitlement_overrides_read_own on public.entitlement_overrides;
create policy entitlement_overrides_read_own on public.entitlement_overrides
    for select to authenticated
    using (owner_id = auth.uid());

drop policy if exists entitlement_overrides_admin_read on public.entitlement_overrides;
create policy entitlement_overrides_admin_read on public.entitlement_overrides
    for select to authenticated
    using (public.admin_has('users.entitlements.write'));

drop policy if exists entitlement_overrides_admin_write on public.entitlement_overrides;
create policy entitlement_overrides_admin_write on public.entitlement_overrides
    for all to authenticated
    using (public.admin_has('users.entitlements.write'))
    with check (public.admin_has('users.entitlements.write'));

drop trigger if exists trg_entitlement_overrides_audit on public.entitlement_overrides;
create trigger trg_entitlement_overrides_audit
    after insert or update or delete on public.entitlement_overrides
    for each row execute function public.admin_audit();


-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Restricting an account.
--
-- Two levels, mirroring the global maintenance gate (docs/APP_STATUS_PLAN.md) because the
-- shapes are the same and one vocabulary is easier to reason about than two:
--
--   read_only  the server refuses their writes; the local app keeps working, with a banner.
--   blocked    firebase-session refuses to mint a session at all, so they cannot sign back in.
--
-- read_only exists for the case a hard block does not fit: an account pushing junk into the
-- shared fairness pool should stop writing to shared data without losing access to its own
-- car records.
-- ─────────────────────────────────────────────────────────────────────────────

alter table public.profiles
    add column if not exists restriction        text not null default 'none'
        check (restriction in ('none', 'read_only', 'blocked')),
    add column if not exists restriction_reason text,
    add column if not exists restricted_at      timestamptz,
    add column if not exists restricted_by      uuid references public.admin_users (id) on delete set null;

comment on column public.profiles.restriction is
    'none | read_only | blocked. Enforced by the restrictive policies below and, for blocked, '
    'by firebase-session refusing to mint a session. The app reads it to explain itself.';

create index if not exists idx_profiles_restricted
    on public.profiles (id) where restriction <> 'none';

create or replace function public.is_restricted_writer(p_owner uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $function$
    select exists (
        select 1 from public.profiles
         where id = p_owner and restriction in ('read_only', 'blocked')
    )
$function$;

comment on function public.is_restricted_writer(uuid) is
    'True when this owner may not write. Read by the restrictive policies below; security '
    'definer because profiles is itself behind RLS.';

revoke all on function public.is_restricted_writer(uuid) from public, anon;
grant execute on function public.is_restricted_writer(uuid) to authenticated;


-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Enforcing it, without touching a single existing policy.
--
-- `AS RESTRICTIVE` is the whole reason this is one short block rather than a rewrite of every
-- owner-scoped policy in the schema. Permissive policies OR together; a restrictive one ANDs
-- with the result. So this adds "...and you are not restricted" to whatever each table already
-- says, and the existing policies — which live in a pasted bootstrap document rather than in
-- these migrations — stay exactly as they are.
--
-- SELECT is deliberately untouched. read_only means read-only, and a blocked account never
-- gets a session to select with.
--
-- The list is every table an owner writes. A table added later needs a line here; there is no
-- way to make that automatic, which is why the comment on each is the same one sentence.
-- ─────────────────────────────────────────────────────────────────────────────

do $do$
declare
    t text;
    owned_tables text[] := array[
        'cars', 'service_logs', 'documents', 'fuel_fills', 'health_scores',
        'overcharge_reports', 'trips', 'bills', 'bill_line_items',
        'vehicle_catalog_submissions', 'city_submissions', 'fairness_data_points'
    ];
begin
    foreach t in array owned_tables loop
        -- Skip tables this project has not created yet rather than failing the migration.
        -- fairness_data_points in particular arrives with the fairness work.
        if to_regclass('public.' || t) is null then
            raise notice 'skipping %: not present', t;
            continue;
        end if;

        execute format('drop policy if exists %I on public.%I', t || '_not_restricted', t);
        execute format(
            'create policy %I on public.%I as restrictive for all to authenticated '
            'using (not public.is_restricted_writer(auth.uid())) '
            'with check (not public.is_restricted_writer(auth.uid()))',
            t || '_not_restricted', t
        );
    end loop;
end;
$do$;

-- `profiles` is handled on its own: the restrictive policy above would stop a restricted owner
-- updating their own row, which is right, but it must not stop an ADMIN from setting the
-- restriction in the first place. Restrictive policies AND with everything, admins included.
drop policy if exists profiles_not_restricted on public.profiles;
create policy profiles_not_restricted on public.profiles
    as restrictive for all to authenticated
    using (public.admin_has('users.restrict.write') or not public.is_restricted_writer(auth.uid()))
    with check (public.admin_has('users.restrict.write') or not public.is_restricted_writer(auth.uid()));

drop policy if exists profiles_admin_read on public.profiles;
create policy profiles_admin_read on public.profiles
    for select to authenticated
    using (public.admin_has('users.read'));

drop policy if exists profiles_admin_restrict on public.profiles;
create policy profiles_admin_restrict on public.profiles
    for update to authenticated
    using (public.admin_has('users.restrict.write'))
    with check (public.admin_has('users.restrict.write'));

drop trigger if exists trg_profiles_admin_audit on public.profiles;
create trigger trg_profiles_admin_audit
    after update on public.profiles
    for each row
    -- Only the restriction. Every owner edits their own profile constantly and auditing all of
    -- it would bury the one change this log exists to record.
    when (old.restriction is distinct from new.restriction)
    execute function public.admin_audit();


-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Finding somebody.
--
-- An RPC because the panel needs one row assembled from two places — `profiles` for the
-- restriction and `auth.users` for the address — and `auth.users` is readable by no client
-- role and must stay that way.
--
-- Matching is exact, not a prefix. A support surface that lists every account whose phone
-- starts with the digits somebody typed is an enumeration tool; this answers "who is this",
-- which is the question support actually has.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function public.admin_find_user(p_query text)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $function$
declare
    v_query text := btrim(coalesce(p_query, ''));
    v_row   record;
begin
    if not public.admin_has('users.read') then
        raise exception 'not permitted' using errcode = '42501';
    end if;
    if v_query = '' then
        return null;
    end if;

    select p.id, p.phone, p.restriction, p.restriction_reason, p.restricted_at, u.email, p.created_at
      into v_row
      from public.profiles p
      left join auth.users u on u.id = p.id
     where p.phone = v_query
        or lower(coalesce(u.email, '')) = lower(v_query)
        or p.id::text = v_query
     limit 1;

    if not found then
        return null;
    end if;

    return jsonb_build_object(
        'id', v_row.id,
        'phone', v_row.phone,
        'email', v_row.email,
        'restriction', v_row.restriction,
        'restriction_reason', v_row.restriction_reason,
        'restricted_at', v_row.restricted_at,
        'created_at', v_row.created_at,
        'entitlements', coalesce(
            (
                select jsonb_agg(jsonb_build_object(
                    'feature', e.feature,
                    'granted', e.granted,
                    'expires_at', e.expires_at,
                    'reason', e.reason,
                    'granted_at', e.granted_at
                ) order by e.feature)
                  from public.entitlement_overrides e
                 where e.owner_id = v_row.id
            ),
            '[]'::jsonb
        )
    );
end;
$function$;

comment on function public.admin_find_user(text) is
    'One user by exact phone, email or id, with their entitlement overrides. Exact rather '
    'than prefix on purpose: a support tool that lists everyone matching a few digits is an '
    'enumeration tool.';

revoke all on function public.admin_find_user(text) from public, anon;
grant execute on function public.admin_find_user(text) to authenticated;

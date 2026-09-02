-- Resolve a registration number to the vehicle behind it, from cars Odo already holds
-- (issue #392). Replaces the RTO integration that was planned and never built.
--
-- **This answers about other owners' cars, and it is reachable without a session.** That is
-- a deliberate product decision: the only people it helps are first-time owners, who by
-- definition have no account yet. Everything below exists to keep that decision from
-- becoming a plate-enumeration service — the shape of the function, the plate check, the
-- counter, and the app-side flag that turns it off.
--
-- What it returns is vehicle attributes only. `owner_id`, `nickname`, `current_odometer_km`
-- and every other column stay behind the fixed `returns table` below, which is the actual
-- security boundary here. Widening it is a privacy change.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.


-- ── The counter ─────────────────────────────────────────────────────────────
--
-- Callers are anonymous, so there is no account to rate-limit or suspend. The only handle
-- PostgREST forwards is the client address, and it is hashed with a per-day salt: this
-- table has to say "somebody asked 400 times today" without becoming a record of who
-- looked up what. The plate is never stored for the same reason.

create table if not exists public.plate_lookups (
    caller_hash text        not null,
    day         date        not null default current_date,
    count       integer     not null default 0,
    primary key (caller_hash, day)
);

comment on table public.plate_lookups is
    'Per-day call counts for resolve_plate, keyed on a salted hash of the client address. '
    'Holds no plates and no owner ids — it exists to make abuse visible and to cap it.';

alter table public.plate_lookups enable row level security;

-- No policies, so no client role reads or writes this. Only the definer function below
-- and service_role touch it.
revoke all on table public.plate_lookups from anon, authenticated;

-- The salt. A single row, rotated daily by the charge function, so yesterday's hashes
-- cannot be recomputed against today's addresses.
--
-- `gen_random_uuid` and `sha256` are core Postgres (pg_catalog), unlike pgcrypto's
-- `gen_random_bytes`/`digest` — which on Supabase live in the `extensions` schema and would
-- have to be qualified as such from a function with an empty search_path.
create table if not exists public.plate_lookup_salt (
    id   boolean     primary key default true check (id),
    day  date        not null default current_date,
    salt text        not null default (gen_random_uuid()::text || gen_random_uuid()::text)
);

insert into public.plate_lookup_salt (id) values (true) on conflict (id) do nothing;

alter table public.plate_lookup_salt enable row level security;
revoke all on table public.plate_lookup_salt from anon, authenticated;


-- ── Charging a call ─────────────────────────────────────────────────────────

create or replace function public.plate_lookup_charge()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    -- Enough for an owner correcting a typo a few times over; nowhere near enough to sweep
    -- a series. The global ceiling is the one that caps a spread-out attempt.
    c_per_caller constant integer := 60;
    c_global     constant integer := 20000;
    v_address text;
    v_salt    text;
    v_hash    text;
    v_count   integer;
    v_total   integer;
begin
    -- PostgREST forwards the request headers as JSON. Absent when the function is called
    -- from SQL rather than over HTTP, which is why this tolerates a null.
    v_address := coalesce(
        nullif(split_part(
            coalesce(current_setting('request.headers', true)::json ->> 'x-forwarded-for', ''),
            ',', 1), ''),
        'unknown');

    -- Rotate the salt on the first call of a new day, then read it back.
    update public.plate_lookup_salt
    set day = current_date, salt = gen_random_uuid()::text || gen_random_uuid()::text
    where day <> current_date;

    select salt into v_salt from public.plate_lookup_salt where id;
    v_hash := encode(sha256(convert_to(v_address || v_salt, 'UTF8')), 'hex');

    insert into public.plate_lookups (caller_hash, day, count)
    values (v_hash, current_date, 1)
    on conflict (caller_hash, day) do update
    set count = public.plate_lookups.count + 1
    returning count into v_count;

    -- Raising rolls the increment above back with it, so a blocked caller's row sits at the
    -- ceiling rather than climbing past it. The block still holds — every later call
    -- re-increments to the same number and raises again — but the count is a floor, not a
    -- total, and reading it as "exactly how many times they tried" would be wrong.
    if v_count > c_per_caller then
        raise exception 'plate lookup limit reached' using errcode = '53400';
    end if;

    -- The ceiling that matters. An address rotates and a per-address limit only slows one
    -- machine down; this is what caps a sweep spread across many.
    select sum(count) into v_total from public.plate_lookups where day = current_date;
    if v_total > c_global then
        raise exception 'plate lookup limit reached' using errcode = '53400';
    end if;
end;
$$;

revoke all on function public.plate_lookup_charge() from public, anon, authenticated;


-- ── The lookup ──────────────────────────────────────────────────────────────

create or replace function public.resolve_plate(p_plate text)
returns table (make text, model text, variant text, year smallint, fuel_type public.fuel_type)
language plpgsql
security definer
-- Empty search_path, so a schema planted on the caller's path cannot shadow public.cars.
-- Every object above and below is therefore schema-qualified.
set search_path = ''
as $$
declare
    v_plate text := upper(regexp_replace(coalesce(p_plate, ''), '[^A-Za-z0-9]', '', 'g'));
begin
    -- A partial plate is refused rather than answered. Prefix probing is the cheapest kind
    -- of enumeration, and this is the only place it can be stopped: eight to eleven
    -- characters is a whole Indian plate, matching RegistrationNumber on the client.
    if v_plate !~ '^[A-Z0-9]{8,11}$' then
        raise exception 'invalid registration number' using errcode = '22023';
    end if;

    perform public.plate_lookup_charge();

    return query
        select c.make, c.model, c.variant, c.year, c.fuel_type
        from public.cars c
        where c.registration_number = v_plate
          and c.deleted_at is null
        -- Newest edit wins, not newest row: an owner who corrected a wrong trim meant it.
        order by c.updated_at desc
        limit 1;
end;
$$;

comment on function public.resolve_plate(text) is
    'Vehicle attributes for a registration number, from cars Odo holds. Callable by anon by '
    'design (issue #392) — rate-limited by plate_lookup_charge and gated app-side by the '
    'plate_lookup_enabled flag. Returns no owner id and nothing about the person.';

revoke all on function public.resolve_plate(text) from public;
grant execute on function public.resolve_plate(text) to anon, authenticated;


-- ── The index it needs ──────────────────────────────────────────────────────
--
-- uq_cars_owner_reg is (owner_id, registration_number) and does not serve a plate-only
-- probe. Without this, every call is a sequential scan of cars, which is its own denial of
-- service.
create index if not exists idx_cars_registration
    on public.cars (registration_number)
    where registration_number is not null and deleted_at is null;

-- Reference data: what a job should cost, what an hour costs, and when the maker says a job
-- is due. Everything the fairness check and the AI advisory both resolve against.
--
-- These tables serve two readers, which is why there is one set of them and not two.
-- docs/AI_ADVISORY_PLAN.md S1 and docs/FAIRNESS_SYSTEM_DESIGN.md S1-S3 specified the same
-- schema separately; this migration is both. The name clash resolved to `job_prices`.
--
-- Today `fairness_data_points` is empty, so every fairness check in every shipped build
-- returns NoBenchmark. The modelled rung of the RPC below is what changes that: it answers
-- from these tables when the pool has nothing, which is the case for every user on day 1.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.


-- ─────────────────────────────────────────────────────────────────────────────
-- 1. The two axes. Neither existed anywhere before this migration.
--
-- Workshop tier is the one that makes a price answer honest. Without it a local garage
-- always reads "under" and an authorised centre always reads "over", so every verdict is
-- wrong in a predictable direction.
-- ─────────────────────────────────────────────────────────────────────────────

do $$ begin
    create type public.vehicle_segment as enum ('hatchback', 'sedan', 'suv', 'muv');
exception when duplicate_object then null;
end $$;

do $$ begin
    create type public.workshop_tier as enum ('authorised', 'multi_brand', 'local');
exception when duplicate_object then null;
end $$;

comment on type public.vehicle_segment is
    'The pricing axis. Swift, i20, Baleno and Tiago are one row - a ~1.2L petrol hatchback. '
    'Brand deliberately does not appear in any price table; it appears only in service_schedule.';


-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Labour rates: 9 rows, and 9 is the whole table.
--
-- Keyed by city *tier*, not by city. A rate per city would need a row for every city we
-- have and another for every city added later; cities.tier is already 1, 2 or 3, so three
-- tiers times three workshop tiers covers every city in the country, including ones nobody
-- on the team has heard of and ones Supabase gains next year.
--
-- This is the only term of a fair price that is local at all. Parts MRP and standard hours
-- are national.
-- ─────────────────────────────────────────────────────────────────────────────

create table if not exists public.labour_rates (
    city_tier      smallint      not null check (city_tier in (1, 2, 3)),
    workshop_tier  workshop_tier not null,
    paise_per_hour bigint        not null check (paise_per_hour > 0),

    source_url     text,
    source_note    text,
    verified_on    date,
    status         text          not null default 'draft' check (status in ('draft', 'approved')),
    updated_by     uuid          references public.admin_users (id) on delete set null,
    created_at     timestamptz   not null default now(),
    updated_at     timestamptz   not null default now(),

    primary key (city_tier, workshop_tier)
);

comment on table public.labour_rates is
    'Workshop labour cost per hour, by city tier x workshop tier. Nine rows, covering every '
    'city. Money is paise, like every other money column in this project.';


-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Job prices: what a job costs before labour, and how long it takes.
--
-- Read top-down off the OEM service-cost estimators, which already answer "Swift, 40,000 km,
-- Delhi -> Rs X, itemised". We do not build a job price up from a parts list.
--
-- `fuel_type` is null for the jobs that do not vary by fuel, which is most of them. A plain
-- unique constraint treats two nulls as distinct and would let the same row be entered twice,
-- so the index is declared NULLS NOT DISTINCT (Postgres 15+; both projects run 17).
--
-- Not an expression index over `coalesce(fuel_type::text, '*')`: an enum-to-text cast is
-- STABLE rather than IMMUTABLE, because enum labels can be renamed, and Postgres refuses it
-- in an index expression.
-- ─────────────────────────────────────────────────────────────────────────────

create table if not exists public.job_prices (
    id                  uuid            primary key default gen_random_uuid(),
    service_category_id uuid            not null references public.service_categories (id) on delete restrict,
    segment             vehicle_segment not null,
    fuel_type           fuel_type,
    parts_paise         bigint          not null check (parts_paise >= 0),
    labour_hours        numeric(4, 2)   not null check (labour_hours > 0),

    source_url          text,
    source_note         text,
    verified_on         date,
    status              text            not null default 'draft' check (status in ('draft', 'approved')),
    updated_by          uuid            references public.admin_users (id) on delete set null,
    created_at          timestamptz     not null default now(),
    updated_at          timestamptz     not null default now()
);

create unique index if not exists uq_job_prices_key
    on public.job_prices (service_category_id, segment, fuel_type) nulls not distinct;

comment on column public.job_prices.fuel_type is
    'Null means the price does not vary by fuel, which is true of most jobs. Only oil and '
    'filter work genuinely splits.';


-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Part MRP.
--
-- Only for the handful of jobs the estimators do not itemise. This is an input to modelling
-- a job price, never a lookup the app performs on its own.
-- ─────────────────────────────────────────────────────────────────────────────

create table if not exists public.part_prices (
    id          uuid            primary key default gen_random_uuid(),
    part_slug   text            not null,
    segment     vehicle_segment,
    fuel_type   fuel_type,
    unit        text            not null check (unit in ('litre', 'piece', 'set')),
    mrp_paise   bigint          not null check (mrp_paise > 0),

    source_url  text,
    source_note text,
    verified_on date,
    status      text            not null default 'draft' check (status in ('draft', 'approved')),
    updated_by  uuid            references public.admin_users (id) on delete set null,
    created_at  timestamptz     not null default now(),
    updated_at  timestamptz     not null default now()
);

create unique index if not exists uq_part_prices_key
    on public.part_prices (part_slug, segment, fuel_type) nulls not distinct;

comment on column public.part_prices.segment is
    'Null means the part costs the same across segments. An air filter largely does; a battery '
    'does not, which is why batteries get no benchmark at all.';


-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Service schedule: the only table where brand appears.
--
-- A null brand is the default rule set. Maruti, Hyundai and Tata are roughly two thirds of
-- the market and their schedules are about 90% identical anyway, so a brand with no exception
-- row falls back to the default and the answer is still right most of the time.
--
-- This is what makes a pre-service checklist work for a user who installed five minutes ago:
-- the schedule is a fact about the manufacturer, not about the owner.
--
-- Both intervals may be set. "Every 10,000 km or 12 months" is one rule, not two.
-- ─────────────────────────────────────────────────────────────────────────────

create table if not exists public.service_schedule (
    id           uuid        primary key default gen_random_uuid(),
    brand        text,
    item_slug    text        not null,
    display_name text        not null,
    due_km       integer     check (due_km is null or due_km > 0),
    due_months   integer     check (due_months is null or due_months > 0),

    source_url   text,
    source_note  text,
    verified_on  date,
    status       text        not null default 'draft' check (status in ('draft', 'approved')),
    updated_by   uuid        references public.admin_users (id) on delete set null,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),

    constraint service_schedule_has_an_interval check (due_km is not null or due_months is not null)
);

-- Brand is plain text, so coalesce would be immutable here and would work. Declared the same
-- way as the two above anyway: one idiom for "a null is a value" beats two.
create unique index if not exists uq_service_schedule_key
    on public.service_schedule (brand, item_slug) nulls not distinct;

comment on column public.service_schedule.brand is
    'Null is the default rule set, used for every brand with no exception row of its own. '
    'The only place a brand name appears in the reference data.';


-- ─────────────────────────────────────────────────────────────────────────────
-- 6. The pool gains the two axes it was missing.
--
-- Nullable, because rows already in the pool have no tier and rows from an owner who skipped
-- the question never will. A null tier is included when the query does not filter by tier and
-- excluded when it does.
-- ─────────────────────────────────────────────────────────────────────────────

alter table public.fairness_data_points
    add column if not exists workshop_tier workshop_tier,
    add column if not exists segment       vehicle_segment,
    add column if not exists source_url    text;

create index if not exists idx_fairness_lookup_tier
    on public.fairness_data_points (service_category_id, city_id, segment, workshop_tier);


-- ─────────────────────────────────────────────────────────────────────────────
-- 7. The flat columns this supersedes.
--
-- `service_categories` gained interval_km, interval_months and benchmark_paise in
-- 20260831220000. They are the same three facts as the tables above with every axis removed:
-- one benchmark for a category cannot say what a Swift costs versus a Fortuner, or an
-- authorised centre versus a local garage, which is the entire question.
--
-- Nothing outside the admin panel reads them - no app code, no edge function - so they are a
-- data-entry surface with no consumer. Left in place rather than dropped: the Catalogue
-- screen still edits them, and dropping a column to make a point is not worth a migration
-- that cannot be undone. Marked here so the next person does not type into the wrong one.
-- ─────────────────────────────────────────────────────────────────────────────

comment on column public.service_categories.benchmark_paise is
    'SUPERSEDED by job_prices, which keys the same figure by segment and resolves labour '
    'through labour_rates. Kept because the Catalogue screen still edits it. Nothing reads it.';

comment on column public.service_categories.interval_km is
    'SUPERSEDED by service_schedule, which adds the brand axis this column cannot express.';


-- ─────────────────────────────────────────────────────────────────────────────
-- 8. Who last touched a row.
--
-- Separate from the audit trigger on purpose: the log is append-only history, this is the
-- row's own state, and the panel's "verified by" column reads this one. Same shape as
-- app_config_stamp_admin.
--
-- Defined before the triggers below attach it. CREATE TRIGGER resolves the function at
-- creation time, so the order here is load-bearing rather than tidiness.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function public.reference_data_stamp_admin()
returns trigger
language plpgsql
security definer
set search_path = public
as $function$
begin
    new.updated_by := public.current_admin_id();
    return new;
end;
$function$;


-- ─────────────────────────────────────────────────────────────────────────────
-- 9. RLS.
--
-- Write is staff-only, on the permission the Catalogue section already uses.
--
-- Read is public. These are published service prices; there is nothing in a row an anonymous
-- reader should not see, and the app has to read them signed out for the pre-signup answers.
--
-- The read policy is deliberately NOT filtered on `status`. 20260831140000_cities_admin.sql
-- had to be written to undo exactly that mistake on `cities`: a row that stops satisfying the
-- policy is absent from a delta pull rather than present-and-retired, so a device keeps its
-- old copy forever. Serving only approved rows is the query's job, and the query does it.
-- ─────────────────────────────────────────────────────────────────────────────

do $$
declare t text;
begin
    foreach t in array array['labour_rates', 'job_prices', 'part_prices', 'service_schedule'] loop
        execute format('alter table public.%I enable row level security', t);

        execute format('drop policy if exists %I on public.%I', t || '_read', t);
        execute format(
            'create policy %I on public.%I for select to authenticated, anon using (true)',
            t || '_read', t);

        execute format('drop policy if exists %I on public.%I', t || '_admin_write', t);
        execute format(
            'create policy %I on public.%I for all to authenticated '
            'using (public.admin_has(''fairness.write'')) '
            'with check (public.admin_has(''fairness.write''))',
            t || '_admin_write', t);

        execute format('drop trigger if exists %I on public.%I', 'trg_' || t || '_audit', t);
        execute format(
            'create trigger %I after insert or update or delete on public.%I '
            'for each row execute function public.admin_audit()',
            'trg_' || t || '_audit', t);

        execute format('drop trigger if exists %I on public.%I', 'trg_' || t || '_updated', t);
        execute format(
            'create trigger %I before update on public.%I '
            'for each row execute function public.set_updated_at()',
            'trg_' || t || '_updated', t);

        execute format('drop trigger if exists %I on public.%I', 'trg_' || t || '_stamp', t);
        execute format(
            'create trigger %I before insert or update on public.%I '
            'for each row execute function public.reference_data_stamp_admin()',
            'trg_' || t || '_stamp', t);
    end loop;
end $$;


-- ─────────────────────────────────────────────────────────────────────────────
-- 10. Coverage.
--
-- What the admin section's meter reads. Counts only approved rows, because a draft row is
-- not servable and counting it would report the data as ready when it is not.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace view public.reference_data_coverage as
    select 'labour_rates'    as table_name, count(*) as approved_rows, 9  as expected_rows
      from public.labour_rates where status = 'approved'
    union all
    select 'job_prices',     count(*), 30 from public.job_prices     where status = 'approved'
    union all
    select 'part_prices',    count(*), 15 from public.part_prices    where status = 'approved'
    union all
    select 'service_schedule', count(*), 25 from public.service_schedule where status = 'approved';

comment on view public.reference_data_coverage is
    'Approved row counts against the target from docs/AI_ADVISORY_PLAN.md. The expected '
    'figures are the hand-entry budget, not a constraint - passing them is fine.';


-- ─────────────────────────────────────────────────────────────────────────────
-- 11. Serving: one RPC with a widening ladder.
--
-- get_fairness_estimate answers one narrow question and returns nothing when it misses,
-- which is every call in every shipped build today. This one walks outward and reports which
-- rung answered, so the UI can widen the band and say why rather than going silent.
--
-- Rung 6 is the change that matters. It computes a price from the reference tables when the
-- pool has no rows at all, so a brand new install gets a real answer on day 1. NoBenchmark
-- becomes reachable only when a category has no job price entered - which is the correct
-- answer for clutch, tyres, battery, brake discs and bodywork, where no segment average is
-- honest and we would rather say nothing than guess.
--
-- SECURITY DEFINER because fairness_data_points is de-identified and not client-readable.
-- The RPC returns aggregates and a sample size, never a row.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function public.get_fairness_benchmark(
    p_category text,
    p_city     text,
    p_segment  vehicle_segment default null,
    p_fuel     fuel_type       default null,
    p_tier     workshop_tier   default null)
returns table (
    avg_paise   bigint,
    p25         bigint,
    p75         bigint,
    sample_size bigint,
    scope       text,
    basis       text)
language plpgsql
stable
security definer
set search_path = public
as $function$
declare
    v_category_id uuid;
    v_city_id     uuid;
    v_city_tier   smallint;
    v_parts       bigint;
    v_hours       numeric(4, 2);
    v_rate        bigint;
    v_point       bigint;
begin
    select id into v_category_id from service_categories where slug = p_category;
    if v_category_id is null then
        return;                                  -- an unknown category is not a wide band, it is a bug
    end if;

    select id, tier into v_city_id, v_city_tier from cities where lower(name) = lower(p_city);

    -- Rungs 1-5, narrowest first, stopping at the first with enough rows to mean anything.
    -- Five is the floor the PRD sets for showing a figure without a low-confidence label.
    --
    -- The inner aliases are v_-prefixed rather than named after the output columns. A query
    -- inside plpgsql that references a name which is both a column and an output parameter
    -- raises "column reference is ambiguous" at runtime, not at creation, so it would have
    -- failed on the first real call rather than on deploy.
    select r.v_scope, r.v_avg, r.v_p25, r.v_p75, r.v_n
      into scope, avg_paise, p25, p75, sample_size
      from (
        select 'CITY_TIER_SEGMENT' as v_scope, 1 as v_rung,
               round(avg(f.amount_paise))::bigint as v_avg,
               percentile_cont(0.25) within group (order by f.amount_paise::double precision)::bigint as v_p25,
               percentile_cont(0.75) within group (order by f.amount_paise::double precision)::bigint as v_p75,
               count(*)::bigint as v_n
          from fairness_data_points f
         where f.service_category_id = v_category_id
           and f.city_id = v_city_id
           and (p_tier    is null or f.workshop_tier = p_tier)
           and (p_segment is null or f.segment       = p_segment)
           and (p_fuel    is null or f.fuel_type     = p_fuel)
        union all
        select 'CITY_TIER', 2,
               round(avg(f.amount_paise))::bigint,
               percentile_cont(0.25) within group (order by f.amount_paise::double precision)::bigint,
               percentile_cont(0.75) within group (order by f.amount_paise::double precision)::bigint,
               count(*)::bigint
          from fairness_data_points f
         where f.service_category_id = v_category_id
           and f.city_id = v_city_id
           and (p_tier is null or f.workshop_tier = p_tier)
        union all
        select 'CITY', 3,
               round(avg(f.amount_paise))::bigint,
               percentile_cont(0.25) within group (order by f.amount_paise::double precision)::bigint,
               percentile_cont(0.75) within group (order by f.amount_paise::double precision)::bigint,
               count(*)::bigint
          from fairness_data_points f
         where f.service_category_id = v_category_id
           and f.city_id = v_city_id
        union all
        -- Tier-1 cities only. A Mumbai price says more about a Pune one than a national
        -- average does, and both are metros.
        select 'METRO_TIER', 4,
               round(avg(f.amount_paise))::bigint,
               percentile_cont(0.25) within group (order by f.amount_paise::double precision)::bigint,
               percentile_cont(0.75) within group (order by f.amount_paise::double precision)::bigint,
               count(*)::bigint
          from fairness_data_points f
          join cities c on c.id = f.city_id
         where f.service_category_id = v_category_id
           and c.tier = 1
           and (p_tier is null or f.workshop_tier = p_tier)
        union all
        select 'NATIONAL_TIER', 5,
               round(avg(f.amount_paise))::bigint,
               percentile_cont(0.25) within group (order by f.amount_paise::double precision)::bigint,
               percentile_cont(0.75) within group (order by f.amount_paise::double precision)::bigint,
               count(*)::bigint
          from fairness_data_points f
         where f.service_category_id = v_category_id
           and (p_tier is null or f.workshop_tier = p_tier)
      ) r
     where r.v_n >= 5
     order by r.v_rung
     limit 1;

    if sample_size is not null then
        basis := 'observed';
        return next;
        return;
    end if;

    -- Rung 6. Parts and labour from the tables, never from a model.
    -- Only approved rows are ever served; that filter lives here, in the query, and not in
    -- the RLS policy, for the reason section 9 gives.
    select j.parts_paise, j.labour_hours into v_parts, v_hours
      from job_prices j
     where j.service_category_id = v_category_id
       and j.status = 'approved'
       and (p_segment is null or j.segment = p_segment)
       and (p_fuel is null or j.fuel_type is null or j.fuel_type = p_fuel)
     order by (j.segment = p_segment) desc nulls last,
              (j.fuel_type is not null) desc
     limit 1;

    if v_parts is null then
        return;                                  -- no job price entered: say nothing, per D12
    end if;

    select l.paise_per_hour into v_rate
      from labour_rates l
     where l.city_tier = coalesce(v_city_tier, 2)
       and l.workshop_tier = coalesce(p_tier, 'multi_brand')
       and l.status = 'approved';

    if v_rate is null then
        return;                                  -- a job price with no rate to apply is half an answer
    end if;

    v_point := v_parts + round(v_hours * v_rate)::bigint;

    avg_paise   := v_point;
    -- +/-15% to reflect that shops differ. The one derived band this design allows, and it is
    -- labelled a reference range rather than a percentile of real bills, because it is not one.
    p25         := round(v_point * 0.85)::bigint;
    p75         := round(v_point * 1.15)::bigint;
    sample_size := 0;
    scope       := 'MODELLED';
    basis       := 'modelled';
    return next;
end;
$function$;

comment on function public.get_fairness_benchmark(text, text, vehicle_segment, fuel_type, workshop_tier) is
    'The fairness lookup. Walks six rungs outward from city+tier+segment to a modelled figure '
    'and reports which one answered, so the client can state its confidence honestly. Returns '
    'no row only when the category has no approved job price - the deliberate silence for '
    'clutch, tyres, battery, brake discs and bodywork. get_fairness_estimate stays deployed '
    'until the client stops calling it.';

grant execute on function public.get_fairness_benchmark(text, text, vehicle_segment, fuel_type, workshop_tier)
    to authenticated, anon;

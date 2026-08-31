-- The vehicle catalog, managed from `/admin` instead of the SQL editor (issue #366).
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.
--
--
-- Why this table set retires differently from `cities`
-- ────────────────────────────────────────────────────
-- `cities` is pulled as a delta on `updated_at`, so a deleted row is invisible to a client
-- that already has it — the delta simply never mentions it again. That is why a city is
-- retired with `is_active` and the flag has to travel (20260831140000_cities_admin.sql).
--
-- `vehicle_makes`/`vehicle_models` are not synced that way. `VehicleCatalogRefresher` does a
-- plain fetch-and-replace of the whole catalog, so a row that stops being fetched stops
-- existing locally on the next refresh — which is exactly what a delete should do. And
-- `cars.make`/`cars.model` are plain strings with no foreign key into these tables, so
-- removing a reference row can never touch a car somebody has already saved.
--
-- So: cities retire, vehicles delete. Adding an `is_active` here would need a client change
-- to filter on it and would buy nothing the delete does not already do correctly.


-- ─────────────────────────────────────────────────────────────────────────────
-- 1. One place that knows how a make/model/trim becomes catalog rows.
--
-- This is the body that has lived inside `promote_vehicle_catalog_submission()` since
-- 20260830130000. It is lifted out unchanged so the admin panel's "add a vehicle" and the
-- queue's "approve" are the same code rather than two implementations that agree until one
-- of them is edited. The id algorithm in particular has to keep matching
-- VehicleSeedData.kt's `slug()`, and one copy is easier to keep matching than two.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function public.upsert_vehicle_catalog_entry(
    p_make text,
    p_model text,
    p_variant text
)
returns void
language plpgsql
as $function$
declare
    v_make_id    text;
    v_model_id   text;
    v_next_order bigint;
begin
    -- The make, matched case-insensitively so "tata" and "Tata" land on the same row rather
    -- than spawning a near-duplicate the picker would show twice.
    select id into v_make_id from public.vehicle_makes where lower(name) = lower(p_make);

    if v_make_id is null then
        v_make_id := 'make-' || public.vehicle_catalog_slug(p_make);
        select coalesce(max(display_order), -1) + 1 into v_next_order from public.vehicle_makes;
        insert into public.vehicle_makes (id, name, display_order)
        values (v_make_id, p_make, v_next_order)
        on conflict (id) do nothing;
    end if;

    -- The trim-less base row for the model. VehicleSeedData.kt inserts one of these for every
    -- model precisely so an owner who does not know their exact trim can still pick just the
    -- model — a promoted or hand-added entry needs the same fallback row, not only the trim.
    v_model_id := 'model-' || public.vehicle_catalog_slug(p_make) || '-' || public.vehicle_catalog_slug(p_model);
    if not exists (select 1 from public.vehicle_models where id = v_model_id) then
        select coalesce(max(display_order), -1) + 1 into v_next_order
          from public.vehicle_models where make_id = v_make_id;
        insert into public.vehicle_models (id, make_id, name, variant, display_order)
        values (v_model_id, v_make_id, p_model, null, v_next_order)
        on conflict (id) do nothing;
    end if;

    -- The named trim itself, only when one was actually given.
    if p_variant is not null and btrim(p_variant) <> '' then
        v_model_id := v_model_id || '-' || public.vehicle_catalog_slug(p_variant);
        if not exists (select 1 from public.vehicle_models where id = v_model_id) then
            select coalesce(max(display_order), -1) + 1 into v_next_order
              from public.vehicle_models where make_id = v_make_id;
            insert into public.vehicle_models (id, make_id, name, variant, display_order)
            values (v_model_id, v_make_id, p_model, p_variant, v_next_order)
            on conflict (id) do nothing;
        end if;
    end if;
end;
$function$;

comment on function public.upsert_vehicle_catalog_entry(text, text, text) is
    'Adds a make (if new), its trim-less model row (if new) and a named trim (if given and '
    'new). The one place that knows how the catalog''s text ids are built, so a hand-added '
    'entry lands exactly where a promoted submission or a bundled seed row would.';

-- The trigger keeps its own job — deciding *when* — and delegates the mechanics.
create or replace function public.promote_vehicle_catalog_submission()
returns trigger
language plpgsql
as $function$
begin
    perform public.upsert_vehicle_catalog_entry(new.make, new.model, new.variant);
    delete from public.vehicle_catalog_submissions where id = new.id;
    return null;
end;
$function$;

-- Unchanged, and re-asserted because the function above was replaced.
drop trigger if exists trg_promote_vehicle_catalog_submission on public.vehicle_catalog_submissions;
create trigger trg_promote_vehicle_catalog_submission
    after update of status on public.vehicle_catalog_submissions
    for each row
    when (new.status = 'accepted' and old.status is distinct from 'accepted')
    execute function public.promote_vehicle_catalog_submission();


-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Adding a vehicle from the panel.
--
-- An RPC rather than three inserts from the browser, because adding one entry can touch three
-- rows and they belong in one transaction — and because the ids have to be built by
-- `vehicle_catalog_slug`, which a client replicating the algorithm would eventually get wrong.
--
-- `security definer` so it can write tables the caller's own policies gate, which means the
-- permission check has to be here, explicitly, and has to be the first thing it does.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function public.admin_add_vehicle(
    p_make text,
    p_model text,
    p_variant text default null
)
returns void
language plpgsql
security definer
set search_path = public
as $function$
begin
    if not public.admin_has('catalog.vehicles.write') then
        raise exception 'not permitted' using errcode = '42501';
    end if;
    if btrim(coalesce(p_make, '')) = '' or btrim(coalesce(p_model, '')) = '' then
        raise exception 'a make and a model are required' using errcode = '22023';
    end if;
    perform public.upsert_vehicle_catalog_entry(btrim(p_make), btrim(p_model), nullif(btrim(coalesce(p_variant, '')), ''));
end;
$function$;

revoke all on function public.admin_add_vehicle(text, text, text) from public, anon;
grant execute on function public.admin_add_vehicle(text, text, text) to authenticated;


-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Editing and removing what is already there.
--
-- Delete rather than a retire flag, for the reason at the top of this file. Deleting a make
-- cascades to its models — that is the existing foreign key, not something added here, and
-- the panel warns before doing it.
-- ─────────────────────────────────────────────────────────────────────────────

drop policy if exists vehicle_makes_admin_write on public.vehicle_makes;
create policy vehicle_makes_admin_write on public.vehicle_makes
    for all to authenticated
    using (public.admin_has('catalog.vehicles.write'))
    with check (public.admin_has('catalog.vehicles.write'));

drop policy if exists vehicle_models_admin_write on public.vehicle_models;
create policy vehicle_models_admin_write on public.vehicle_models
    for all to authenticated
    using (public.admin_has('catalog.vehicles.write'))
    with check (public.admin_has('catalog.vehicles.write'));


-- ─────────────────────────────────────────────────────────────────────────────
-- 4. The submission queue becomes reviewable.
--
-- Same shape as the cities queue. `vehicle_catalog_submissions` has had one policy since it
-- was created — owners insert their own rows, nobody reads them back — which was right while
-- the only reviewer had service-role access, and left the queue invisible to the panel now
-- built to work it.
-- ─────────────────────────────────────────────────────────────────────────────

drop policy if exists vehicle_submissions_admin_read on public.vehicle_catalog_submissions;
create policy vehicle_submissions_admin_read on public.vehicle_catalog_submissions
    for select to authenticated
    using (public.admin_has('catalog.vehicles.write'));

drop policy if exists vehicle_submissions_admin_update on public.vehicle_catalog_submissions;
create policy vehicle_submissions_admin_update on public.vehicle_catalog_submissions
    for update to authenticated
    using (public.admin_has('catalog.vehicles.write'))
    with check (public.admin_has('catalog.vehicles.write'));

drop policy if exists vehicle_submissions_admin_delete on public.vehicle_catalog_submissions;
create policy vehicle_submissions_admin_delete on public.vehicle_catalog_submissions
    for delete to authenticated
    using (public.admin_has('catalog.vehicles.write'));


-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Audit.
--
-- Every op on the queue. On the catalog itself, updates and deletes only — an insert is
-- self-evident in the table it lands in, and auditing inserts would put a row in the log for
-- every line of seed_vehicle_catalog.sql, which is thousands.
-- ─────────────────────────────────────────────────────────────────────────────

drop trigger if exists trg_vehicle_makes_audit on public.vehicle_makes;
create trigger trg_vehicle_makes_audit
    after update or delete on public.vehicle_makes
    for each row execute function public.admin_audit();

drop trigger if exists trg_vehicle_models_audit on public.vehicle_models;
create trigger trg_vehicle_models_audit
    after update or delete on public.vehicle_models
    for each row execute function public.admin_audit();

drop trigger if exists trg_vehicle_submissions_audit on public.vehicle_catalog_submissions;
create trigger trg_vehicle_submissions_audit
    after insert or update or delete on public.vehicle_catalog_submissions
    for each row execute function public.admin_audit();

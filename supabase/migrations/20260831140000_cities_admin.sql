-- The cities catalog, managed from `/admin` instead of the SQL editor (issue #367).
--
-- Three things, and the first is a bug fix rather than a feature.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.


-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Retiring a city has never reached a phone.
--
-- `cities` is pulled by the ordinary sync engine as a delta on `updated_at`
-- (SupabaseCityRemoteDataSource), and the client already models the retire flag
-- end to end: `CityDto.is_active` is stored by `CitySyncTable` and the picker
-- reads `WHERE is_active = 1`. Every piece is in place.
--
-- Except the read policy was `USING (is_active)`. A retired row stops satisfying
-- it, so the delta pull does not return the row *at all* — it returns nothing
-- where it should return "this one is now inactive". The device keeps its old
-- copy with is_active = 1 and goes on offering the city forever. Retiring
-- appeared to work on the server and changed nothing anywhere else.
--
-- RLS filters rows; it cannot express "and tell them this one is gone". So the
-- filter has to come off and the flag has to travel. `cities` is public
-- reference data — the names of Indian cities — and there is nothing in a
-- retired row that an anonymous reader should not see.
--
-- The same shape exists on `service_categories` and is left alone: nothing syncs
-- that table as a delta today, so it does not have this bug yet.
-- ─────────────────────────────────────────────────────────────────────────────

drop policy if exists read_cities on public.cities;
create policy read_cities on public.cities
    for select to authenticated, anon
    using (true);

comment on column public.cities.is_active is
    'Soft retire. Deliberately NOT filtered by RLS: the client pulls this table as a delta on '
    'updated_at and needs to receive the row in order to learn it was retired. Filtering here '
    'means a retired city is simply absent from the delta and stays in every picker forever. '
    'The picker filters locally (City.sq), which is where the filtering belongs.';


-- ─────────────────────────────────────────────────────────────────────────────
-- 2. The catalog is writable by an admin who holds `catalog.cities.write`.
--
-- No delete policy. A city with rows pointing at it — a profile, a labour rate —
-- should be retired, not removed, and `is_active` is what the client already
-- understands. Nothing needs a hard delete, so nothing is granted one.
-- ─────────────────────────────────────────────────────────────────────────────

drop policy if exists cities_admin_insert on public.cities;
create policy cities_admin_insert on public.cities
    for insert to authenticated
    with check (public.admin_has('catalog.cities.write'));

drop policy if exists cities_admin_update on public.cities;
create policy cities_admin_update on public.cities
    for update to authenticated
    using (public.admin_has('catalog.cities.write'))
    with check (public.admin_has('catalog.cities.write'));

-- The server clock owns updated_at, which is what makes the sync cursor
-- trustworthy. Re-asserted here rather than assumed: this migration's whole
-- premise is that a retire reaches the client, and it reaches the client only
-- because the UPDATE moves updated_at forward. Both are idempotent.
create or replace function public.set_updated_at()
returns trigger language plpgsql as $function$
begin
    new.updated_at := now();
    return new;
end;
$function$;

drop trigger if exists trg_cities_updated on public.cities;
create trigger trg_cities_updated before insert or update on public.cities
    for each row execute function public.set_updated_at();


-- ─────────────────────────────────────────────────────────────────────────────
-- 3. The submission queue becomes reviewable.
--
-- `city_submissions` has had exactly one policy since it was created: owners may
-- insert their own rows and nobody may read them back. That was right when the
-- only reviewer was somebody in the SQL editor with service-role access; it also
-- meant the queue was invisible to the panel that now exists to work it.
--
-- Delete as well as update, for the junk that a free-text field attracts. An
-- accepted row is deleted by the existing promote trigger; a rejected one is
-- kept so the same city is not re-reviewed from scratch every week, and a
-- nonsense one should just go.
-- ─────────────────────────────────────────────────────────────────────────────

drop policy if exists city_submissions_admin_read on public.city_submissions;
create policy city_submissions_admin_read on public.city_submissions
    for select to authenticated
    using (public.admin_has('catalog.cities.write'));

drop policy if exists city_submissions_admin_update on public.city_submissions;
create policy city_submissions_admin_update on public.city_submissions
    for update to authenticated
    using (public.admin_has('catalog.cities.write'))
    with check (public.admin_has('catalog.cities.write'));

drop policy if exists city_submissions_admin_delete on public.city_submissions;
create policy city_submissions_admin_delete on public.city_submissions
    for delete to authenticated
    using (public.admin_has('catalog.cities.write'));


-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Audit.
--
-- Every op on the queue, because approve and reject are decisions somebody may
-- have to explain later.
--
-- Only UPDATE and DELETE on `cities` itself. An insert is self-evident — the row
-- is right there in the catalog — while an edit or a retire is the change nobody
-- can see afterwards, and it is the one that generates the support question.
-- Skipping inserts also keeps seed_cities.sql from writing several hundred
-- unattributed rows into the log and burying the changes worth reading.
-- ─────────────────────────────────────────────────────────────────────────────

drop trigger if exists trg_cities_audit on public.cities;
create trigger trg_cities_audit
    after update or delete on public.cities
    for each row execute function public.admin_audit();

drop trigger if exists trg_city_submissions_audit on public.city_submissions;
create trigger trg_city_submissions_audit
    after insert or update or delete on public.city_submissions
    for each row execute function public.admin_audit();

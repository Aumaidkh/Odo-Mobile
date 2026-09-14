-- Fixes the restriction added in 20260831160000: `read_only` was silently a hard block.
--
-- Those policies were written `AS RESTRICTIVE FOR ALL`, and `FOR ALL` includes SELECT. A
-- restricted owner therefore could not read their own rows — not their cars, not their service
-- history, not even their own profile. That is not "read only", it is "blocked without saying
-- so", and it would have reached a phone as an app that had apparently lost all its data.
--
-- Postgres has no "every command except SELECT", so each table gets three restrictive policies
-- instead of one. INSERT takes only a WITH CHECK, DELETE only a USING, UPDATE takes both —
-- which is why they cannot be collapsed back into a single statement.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.

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
        if to_regclass('public.' || t) is null then
            raise notice 'skipping %: not present', t;
            continue;
        end if;

        -- The over-broad one from the previous migration.
        execute format('drop policy if exists %I on public.%I', t || '_not_restricted', t);

        execute format('drop policy if exists %I on public.%I', t || '_not_restricted_insert', t);
        execute format(
            'create policy %I on public.%I as restrictive for insert to authenticated '
            'with check (not public.is_restricted_writer(auth.uid()))',
            t || '_not_restricted_insert', t
        );

        execute format('drop policy if exists %I on public.%I', t || '_not_restricted_update', t);
        execute format(
            'create policy %I on public.%I as restrictive for update to authenticated '
            'using (not public.is_restricted_writer(auth.uid())) '
            'with check (not public.is_restricted_writer(auth.uid()))',
            t || '_not_restricted_update', t
        );

        execute format('drop policy if exists %I on public.%I', t || '_not_restricted_delete', t);
        execute format(
            'create policy %I on public.%I as restrictive for delete to authenticated '
            'using (not public.is_restricted_writer(auth.uid()))',
            t || '_not_restricted_delete', t
        );
    end loop;
end;
$do$;

-- `profiles` again, with the same split. The admin escape hatch stays: a restrictive policy
-- ANDs with everything, admins included, so without it an admin could not lift a restriction
-- they had just applied — and the account would be stuck restricted forever.
drop policy if exists profiles_not_restricted on public.profiles;

drop policy if exists profiles_not_restricted_insert on public.profiles;
create policy profiles_not_restricted_insert on public.profiles
    as restrictive for insert to authenticated
    with check (public.admin_has('users.restrict.write') or not public.is_restricted_writer(auth.uid()));

drop policy if exists profiles_not_restricted_update on public.profiles;
create policy profiles_not_restricted_update on public.profiles
    as restrictive for update to authenticated
    using (public.admin_has('users.restrict.write') or not public.is_restricted_writer(auth.uid()))
    with check (public.admin_has('users.restrict.write') or not public.is_restricted_writer(auth.uid()));

drop policy if exists profiles_not_restricted_delete on public.profiles;
create policy profiles_not_restricted_delete on public.profiles
    as restrictive for delete to authenticated
    using (public.admin_has('users.restrict.write') or not public.is_restricted_writer(auth.uid()));

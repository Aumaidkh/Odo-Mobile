-- `DECLARED` — a service the owner remembers, with no bill and no total behind it.
--
-- Setup's "when was your last service?" step has to store its answer somewhere, and nothing
-- holds a last service on its own: the health score, the reminders and the pre-service
-- checklist all read the newest `service_logs` row. So the answer becomes one, under a third
-- source that says the owner reported it rather than proved it.
--
-- A no-op where `source` is already text. `add value if not exists` cannot run inside a
-- transaction block on older servers, so it is guarded rather than wrapped.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.

do $$
begin
    if exists (select 1 from pg_type where typname = 'log_source' and typtype = 'e') then
        -- Casing follows the Kotlin constant name: the client stores `LogSource.name`.
        alter type public.log_source add value if not exists 'DECLARED';
    end if;
end
$$;

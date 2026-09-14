-- `declared` — a service the owner remembers, with no bill and no total behind it.
--
-- Setup's "when was your last service?" step has to store its answer somewhere, and nothing
-- holds a last service on its own: the health score, the reminders and the pre-service
-- checklist all read the newest `service_logs` row. So the answer becomes one, under a third
-- source that says the owner reported it rather than proved it.
--
-- **Lowercase, like the labels beside it.** The local database keeps Kotlin constant names
-- (`DECLARED`) and `ServiceLogSyncTable` folds case at the sync boundary — `.lowercase()`
-- on the way out, `.uppercase()` on the way back. An uppercase label here would add a second
-- value the client never sends, and every push of this row would still be refused.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.

do $$
begin
    if exists (select 1 from pg_type where typname = 'log_source' and typtype = 'e') then
        alter type public.log_source add value if not exists 'declared';
    end if;
end
$$;

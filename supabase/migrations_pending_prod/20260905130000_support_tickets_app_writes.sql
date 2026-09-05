-- Letting an owner actually file a ticket.
--
-- The previous migration added the columns the app needs and stopped there, which left the
-- table readable by its owner and writable by nobody but an admin. Every push would have come
-- back `42501`, and a 403 is permanent — the sync marks the row CONFLICT and never retries, so
-- each report would have died on the device with the screen saying it was sent.
--
-- Four things were wrong, and all four fail the same silent way.

-- 1. No write policy. Reading your own ticket was allowed; creating one was not.
--    Split rather than `for all`: an owner may file a ticket and correct one that has not
--    left yet, and may never delete one — a deleted ticket is a support conversation that
--    vanishes from one side.
create policy support_tickets_insert_own on public.support_tickets
    for insert to authenticated
    with check (owner_id = (select auth.uid()));

create policy support_tickets_update_own on public.support_tickets
    for update to authenticated
    using (owner_id = (select auth.uid()))
    with check (owner_id = (select auth.uid()));

-- 2. `contact` and `subject` are NOT NULL with no default, and predate the app writing here.
--    Defaulted rather than made nullable: the panel's list draws both, and a null subject is a
--    blank row in a queue somebody has to work.
alter table public.support_tickets
    alter column contact set default '',
    alter column subject set default '';

-- 3. The unique index was partial (`where client_id is not null`), and PostgREST's
--    `on_conflict=client_id` emits `ON CONFLICT (client_id) DO UPDATE` with no predicate.
--    Postgres only infers a partial index when the statement's own predicate implies the
--    index's, so every upsert raised `42P10`. Unconditional is also correct on its own terms:
--    Postgres allows any number of NULLs in a unique index, which is exactly what the legacy
--    rows need.
drop index if exists public.support_tickets_client_id_key;
create unique index if not exists support_tickets_client_id_key
    on public.support_tickets (client_id);

-- 4. `reply_to` was never added, and does not need to be: `contact` is the queue's own column
--    for where an answer goes, the panel already replies to it, and a second column holding
--    the same address is a second thing to keep in step. The app's local table keeps its own
--    name for it and the adapter maps between them.
comment on column public.support_tickets.contact is
    'Where the answer goes. The app''s local column for the same thing is called reply_to.';

comment on column public.support_tickets.status is
    'open | pending | resolved | closed. Lowercase — the app sends the same values the panel does.';

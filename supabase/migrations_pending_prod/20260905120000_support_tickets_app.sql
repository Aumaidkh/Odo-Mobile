-- The app writing into the support queue the panel already reads.
--
-- `support_tickets` exists and the panel works it; what it cannot do is take a row the app
-- named. Its `id` is a bigserial the server assigns, so an offline device has no way to name
-- one — and naming a row before it is sent is the whole of what offline-first means here.
--
-- `client_id` is that name. The primary key is left alone: the panel reads `id`, eleven rows
-- already carry one, and rewriting the key of a live table buys nothing this column does not.

alter table public.support_tickets
    add column if not exists client_id uuid,
    -- Which of the three forms it came from. Defaulted so the rows already there stay valid.
    add column if not exists kind text not null default 'PROBLEM',
    -- What the form collected in fields of its own — the area, the disputed band, what was
    -- paid. Named values, so the panel can route and filter without reading prose.
    add column if not exists details jsonb not null default '{}'::jsonb,
    -- [{storage_key, name}]. The files themselves go to storage; this is what points at them.
    add column if not exists attachments jsonb not null default '[]'::jsonb,
    -- The diagnostics upload that travelled with it, when the owner asked for one.
    add column if not exists diagnostics_reference text,
    add column if not exists deleted_at timestamptz;

-- One row per client id. This is what a re-push resolves on: without it, a retry inserts a
-- second copy of the same ticket instead of updating the first.
create unique index if not exists support_tickets_client_id_key
    on public.support_tickets (client_id)
    where client_id is not null;

comment on column public.support_tickets.client_id is
    'The id the app generated. Null on rows filed before the app could write here.';

/*
 * The curated ideas, and who voted for them.
 *
 * The catalogue is the panel's to write and everybody's to read — no owner column, because it
 * is the same list for every owner. `votes` is the server's tally rather than something a
 * device counts: a count assembled from one phone is not a count.
 */
create table if not exists public.feature_ideas (
    id          uuid primary key default gen_random_uuid(),
    title       text not null,
    status      text not null default 'UNDER_REVIEW'
                check (status in ('UNDER_REVIEW', 'IN_PROGRESS', 'SHIPPING', 'SHIPPED')),
    votes       integer not null default 0,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    deleted_at  timestamptz
);

alter table public.feature_ideas enable row level security;

-- Readable by anyone signed in, and written by nobody but the panel's service role. An idea
-- a device could write would be an idea with a vote count it chose.
create policy feature_ideas_read on public.feature_ideas
    for select to authenticated
    using (deleted_at is null);

/*
 * One owner's vote on one idea.
 *
 * Keyed on the pair, so pressing the pill twice on two devices ends as one row. Withdrawing a
 * vote is a soft delete rather than a removal: the server has to hear that it was withdrawn,
 * and a row that is gone has nothing to say.
 */
create table if not exists public.idea_votes (
    idea_id     uuid not null references public.feature_ideas(id) on delete cascade,
    owner_id    uuid not null references auth.users(id) on delete cascade,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    deleted_at  timestamptz,
    primary key (idea_id, owner_id)
);

alter table public.idea_votes enable row level security;

-- The flat owner check every other user-owned table uses, never a join subquery.
create policy idea_votes_own on public.idea_votes
    for all to authenticated
    using (owner_id = (select auth.uid()))
    with check (owner_id = (select auth.uid()));

/*
 * The count, kept by the database rather than by anything that could disagree with it.
 *
 * A tally maintained by the panel or recomputed on read would drift from the rows the moment
 * two votes landed at once. This runs inside the same transaction as the vote.
 */
create or replace function public.idea_votes_recount()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    update public.feature_ideas
    set votes = (
        select count(*)
        from public.idea_votes
        where idea_id = coalesce(new.idea_id, old.idea_id)
          and deleted_at is null
    )
    where id = coalesce(new.idea_id, old.idea_id);
    return null;
end;
$$;

drop trigger if exists idea_votes_recount on public.idea_votes;
create trigger idea_votes_recount
    after insert or update or delete on public.idea_votes
    for each row execute function public.idea_votes_recount();

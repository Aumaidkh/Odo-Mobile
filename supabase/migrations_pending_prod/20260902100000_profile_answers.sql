-- `profile_answers` — the owner's questionnaire answers (#394), one row per selected option.
--
-- Replaces `profiles.onboarding_goal`. A column per question means a migration per question,
-- and a `text` column cannot hold a multi-select answer at all. Here a new question is a new
-- `question_key` and a second selection is a second row. The cost, accepted in
-- docs/QUESTIONNAIRE_PLAN.md D1, is that `answer_value` is unvalidated text.
--
-- `profiles.onboarding_goal` stays and is still written. Backfill and drop are later slices.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.

create table if not exists public.profile_answers (
    -- Client-generated, so a retried push upserts instead of creating a twin.
    id            uuid primary key,
    owner_id      uuid not null references public.profiles (id) on delete cascade,

    -- Versioned: `goal.v1`, not `goal`. A changed question gets a new version so old answers
    -- stop being read.
    question_key  text not null check (length(question_key) > 0),

    -- The domain constant name (`TRACK_COSTS`), not the label on the card.
    answer_value  text not null check (length(answer_value) > 0),

    -- When the owner answered. Not `updated_at`, which moves on a tombstone or a revive.
    answered_at   timestamptz not null,

    created_at    timestamptz not null,
    updated_at    timestamptz not null,
    -- Deselecting tombstones the row. A second device learns of it no other way.
    deleted_at    timestamptz
);

comment on table public.profile_answers is
    'Owner answers to the questionnaire (#394). One row per selected option, so a '
    'multi-select answer is several rows and a new question needs no migration.';

comment on column public.profile_answers.question_key is
    'Versioned key from the Kotlin question registry, e.g. goal.v1.';

comment on column public.profile_answers.answer_value is
    'The domain constant name of the selected option, not its label. Unvalidated by design.';

comment on column public.profile_answers.updated_at is
    'Written by the client, never by this server. It is the sync cursor on both sides. A '
    'trigger that rewrote it would make every pushed row look changed on the next pull.';


-- On the triple, not on (owner_id, question_key): a multi-select answer is several live rows
-- for one key.
--
-- No `where deleted_at is null`, deliberately. A partial index would let a tombstone and a
-- live row share the triple, so re-selecting a deselected option would add a row every time.
-- Total means re-selecting revives the existing row and keeps the id sync depends on.
create unique index if not exists uq_profile_answers_owner_key_value
    on public.profile_answers (owner_id, question_key, answer_value);


-- Row-level security. An answer belongs to one account and is never shared.

alter table public.profile_answers enable row level security;

drop policy if exists profile_answers_select_own on public.profile_answers;
create policy profile_answers_select_own on public.profile_answers
    for select to authenticated
    using (owner_id = auth.uid());

drop policy if exists profile_answers_insert_own on public.profile_answers;
create policy profile_answers_insert_own on public.profile_answers
    for insert to authenticated
    with check (owner_id = auth.uid());

-- `using` and `with check` both: without the check, an owner could update their own row and
-- set `owner_id` to somebody else's on the way past.
drop policy if exists profile_answers_update_own on public.profile_answers;
create policy profile_answers_update_own on public.profile_answers
    for update to authenticated
    using (owner_id = auth.uid())
    with check (owner_id = auth.uid());

-- Hard delete is only the sign-out wipe. Deselecting is a tombstone, which is an update.
drop policy if exists profile_answers_delete_own on public.profile_answers;
create policy profile_answers_delete_own on public.profile_answers
    for delete to authenticated
    using (owner_id = auth.uid());


-- Restriction (20260831170000_restriction_allows_reads.sql): a restricted account keeps its
-- reads and loses its writes. Three policies rather than one `for all`, because `for all`
-- includes SELECT and would turn `read_only` into a silent block. This table carries its own
-- because that migration's loop has already run.

drop policy if exists profile_answers_not_restricted_insert on public.profile_answers;
create policy profile_answers_not_restricted_insert on public.profile_answers
    as restrictive for insert to authenticated
    with check (not public.is_restricted_writer(auth.uid()));

drop policy if exists profile_answers_not_restricted_update on public.profile_answers;
create policy profile_answers_not_restricted_update on public.profile_answers
    as restrictive for update to authenticated
    using (not public.is_restricted_writer(auth.uid()))
    with check (not public.is_restricted_writer(auth.uid()));

drop policy if exists profile_answers_not_restricted_delete on public.profile_answers;
create policy profile_answers_not_restricted_delete on public.profile_answers
    as restrictive for delete to authenticated
    using (not public.is_restricted_writer(auth.uid()));


-- The delta pull is `owner_id = ? and updated_at > cursor`. The unique index leads with
-- `owner_id` but not `updated_at` second, so the pull needs its own.
create index if not exists idx_profile_answers_owner_updated
    on public.profile_answers (owner_id, updated_at);

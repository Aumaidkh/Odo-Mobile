-- The model fallback for bill lines the rule table cannot name.
--
-- Two tables, and neither holds a price. The model classifies; the number always comes from
-- `job_prices` and `labour_rates` (AI_ADVISORY_PLAN §2.7). A hallucinated band read aloud at a
-- service counter is the one output we could be held to.
--
--   `bill_line_classifications` — the cache, and the review queue. Bill wording repeats across
--   owners far more than it varies, so the second owner charged for "throttle body cleaning"
--   costs nothing. A row is also what a human reads before promoting a phrase into
--   `BillLineMatcher`'s rules, which is where every answer should end up.
--
--   `advisory_call_meter` — what stops a bug costing money. Two caps: per owner per day, and
--   across the whole project per day.

create table if not exists public.bill_line_classifications (
    -- The line lower-cased with punctuation flattened, exactly as `BillLineMatcher` normalises
    -- it. Two workshops printing "A/C Service" and "ac service" are one row and one call.
    label_key       text primary key,
    -- One raw example, for the human reading this as a review queue.
    label_sample    text not null,
    -- Null is an answer: the model looked and this is no job we price. Cached like any other,
    -- or every unpriceable line re-asks forever.
    category_slug   text,
    source          text not null default 'model' check (source in ('model', 'human')),
    hits            integer not null default 1,
    created_at      timestamptz not null default now(),
    -- Set when a person has read the row. A promoted phrase leaves a reviewed row behind.
    reviewed_at     timestamptz
);

comment on table public.bill_line_classifications is
    'Bill line wording to service category. Filled by the model, read by a human, promoted into the app rules.';

-- Deny-all, and no policy follows. The Edge Function reaches this with the service role; a
-- client that could write it could name its own line whatever priced it best.
alter table public.bill_line_classifications enable row level security;

create table if not exists public.advisory_call_meter (
    day        date not null default current_date,
    owner_id   uuid not null,
    calls      integer not null default 0,
    lines      integer not null default 0,
    primary key (day, owner_id)
);

comment on table public.advisory_call_meter is
    'Model calls per owner per day. The cap that makes a runaway client cost nothing.';

alter table public.advisory_call_meter enable row level security;

/*
 * Take one call from today's budget, or refuse.
 *
 * Refusing is not an error — the caller answers from the cache alone and the lines it could
 * not name stay unchecked, which is what the screen already does for a line the rules miss.
 *
 * The per-owner update carries the row lock, so two concurrent requests from one device
 * cannot both pass the last slot. The project-wide total is read without one: a few calls
 * over a daily cap costs a rupee, and locking every owner's row to be exact costs throughput.
 */
create or replace function public.advisory_meter_take(
    p_owner uuid,
    p_lines integer,
    p_owner_cap integer,
    p_daily_cap integer
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
    today_total integer;
    taken integer;
begin
    select coalesce(sum(calls), 0) into today_total
    from public.advisory_call_meter
    where day = current_date;

    if today_total >= p_daily_cap then
        return false;
    end if;

    insert into public.advisory_call_meter (day, owner_id, calls, lines)
    values (current_date, p_owner, 0, 0)
    on conflict (day, owner_id) do nothing;

    update public.advisory_call_meter
    set calls = calls + 1,
        lines = lines + p_lines
    where day = current_date
      and owner_id = p_owner
      and calls < p_owner_cap
    returning calls into taken;

    return taken is not null;
end;
$$;

-- Only the function's service role spends the budget. A client that could call this could
-- spend somebody else's.
revoke all on function public.advisory_meter_take(uuid, integer, integer, integer)
    from public, anon, authenticated;

/*
 * Count a cache hit against each key.
 *
 * What the review queue sorts by: the wording that saves the most calls is the wording worth
 * promoting into the app's rule table, where it costs nothing at all.
 */
create or replace function public.advisory_cache_hit(p_keys text[])
returns void
language sql
security definer
set search_path = public
as $$
    update public.bill_line_classifications
    set hits = hits + 1
    where label_key = any(p_keys);
$$;

revoke all on function public.advisory_cache_hit(text[]) from public, anon, authenticated;

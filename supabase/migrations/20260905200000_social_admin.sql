-- The social pipeline, made configurable from the admin panel.
--
-- Everything the panel touches is reached through `public`, because `webCore`'s Postgrest
-- sends no Accept-Profile header and can only see that schema.
--
-- Exposing `social` to PostgREST would not have been enough on its own, and the first draft
-- of this comment said it would be dangerous, which was wrong: those tables have RLS on with
-- no policies, so an exposed schema hands a browser nothing. The actual problem is the same
-- fact — no policy means no rows, for the panel too. A `public` view carrying an explicit
-- permission check is what makes them readable by an admin and by nobody else.
--
-- Exposure is still required for the *functions*, which reach `social` over PostgREST with
-- the service role. That is a project setting rather than a migration: production has
-- `public, graphql_public, social`, and any project running the pipeline needs to match it.
--
-- So: configuration lives in `public.social_*` with RLS on `admin_has('blog.write')`, the
-- pipeline's own tables are read through views that carry the same check, writes into them
-- go through SECURITY DEFINER functions, and secrets are readable by nobody but the service
-- role the edge functions run as.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.

create schema if not exists social;

-- ─────────────────────────────────────────────────────────────────────────────
-- 0. The pipeline's own tables, if this project has never had them.
--
-- They are created by social-automation/supabase/schema.sql, which is deployed by hand and
-- has only ever been run where the pipeline runs. The views further down read them, and a
-- view over a missing table fails the whole migration — so this file stands on its own
-- rather than on somebody having remembered.
--
-- Bodies match that file exactly. Where they already exist, every statement here is a no-op.
-- ─────────────────────────────────────────────────────────────────────────────

create table if not exists social.content_bank (
  id            bigint generated always as identity primary key,
  category      text not null,
  fact          text not null,
  stats         jsonb,
  screenshot    text,
  cta           text not null default 'Odo — free to start',
  last_used_at  timestamptz,
  created_at    timestamptz not null default now()
);

create table if not exists social.content_queue (
  id             bigint generated always as identity primary key,
  bank_id        bigint references social.content_bank(id),
  status         text not null default 'draft',
  variant        text not null default 'stat',
  include_story  boolean not null default true,
  copy           jsonb not null,
  post_image_url text,
  story_image_url text,
  telegram_message_id bigint,
  error          text,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);

create table if not exists social.post_log (
  id            bigint generated always as identity primary key,
  queue_id      bigint references social.content_queue(id),
  ig_media_id   text,
  ig_story_id   text,
  published_at  timestamptz not null default now()
);

create table if not exists social.app_config (
  key        text primary key,
  value      text not null,
  updated_at timestamptz not null default now()
);

alter table social.content_bank  enable row level security;
alter table social.content_queue enable row level security;
alter table social.post_log      enable row level security;
alter table social.app_config    enable row level security;

-- What a queued post carries now that a schedule can produce it.
--
-- `approval` is stamped when the post is made rather than read when it is published: the
-- mode can be changed between those two moments, and a post that already asked a person
-- must not silently become one that did not.
alter table social.content_queue
    add column if not exists approval  text   not null default 'manual'
                             check (approval in ('manual', 'auto')),
    add column if not exists slot_id   uuid,
    add column if not exists platforms text[] not null default '{}';


-- ─────────────────────────────────────────────────────────────────────────────
-- 1. How the pipeline behaves.
--
-- One row, id = true, so there is nowhere for a second one to hide. `posting_mode` is the
-- owner's three options:
--
--   auto      — whatever is generated is published. No approval anywhere.
--   custom    — nothing runs on its own. A post is created on demand from the panel and
--               always waits for a person.
--   scheduled — social_schedule drives it, and each slot carries its own approval.
--
-- Per-slot approval therefore lives on the slot and not here: under `auto` and `custom` the
-- answer is already decided, and a second setting saying otherwise would be a setting that
-- can disagree with itself.
-- ─────────────────────────────────────────────────────────────────────────────

create table if not exists public.social_settings (
    id            boolean     primary key default true check (id),
    posting_mode  text        not null default 'scheduled'
                              check (posting_mode in ('auto', 'custom', 'scheduled')),
    -- Checked by generate, by the renderer's dispatch and by every publish. One switch that
    -- stops the whole pipeline is the first thing wanted when something is going wrong, and
    -- it must not mean deleting the schedule.
    paused        boolean     not null default false,
    -- Slots are written in this zone, which is not the server's.
    timezone      text        not null default 'Asia/Kolkata',
    updated_by    uuid        references public.admin_users (id) on delete set null,
    updated_at    timestamptz not null default now()
);

insert into public.social_settings (id) values (true) on conflict (id) do nothing;


-- ─────────────────────────────────────────────────────────────────────────────
-- 2. When to post.
--
-- Read by the `social-tick` function, which pg_cron runs every 15 minutes. One cron row
-- instead of three, because a schedule the panel can edit cannot be three hard-coded
-- `cron.schedule` calls.
--
-- `days_of_week` is ISO (1 = Monday), and empty means every day. `day_of_month` is for the
-- monthly slots; null means it does not apply. Both may be set, and then both must match.
-- ─────────────────────────────────────────────────────────────────────────────

create table if not exists public.social_schedule (
    id            uuid        primary key default gen_random_uuid(),
    label         text        not null,
    -- Local to social_settings.timezone. Matched to the nearest tick, not to the second.
    time_of_day   time        not null,
    days_of_week  smallint[]  not null default '{}',
    day_of_month  smallint    check (day_of_month is null or day_of_month between 1 and 31),
    -- Which accounts this slot goes to. Empty means every connected account.
    platforms     text[]      not null default '{}',
    variant       text        not null default 'stat' check (variant in ('stat', 'screenshot')),
    include_story boolean     not null default false,
    -- Only consulted under posting_mode = 'scheduled'.
    approval      text        not null default 'manual' check (approval in ('manual', 'auto')),
    enabled       boolean     not null default true,
    -- Stamped by the tick so a slot cannot fire twice inside one window, and so the panel
    -- can show when a slot last did anything.
    last_fired_at timestamptz,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);

create index if not exists idx_social_schedule_enabled on public.social_schedule (enabled, time_of_day);


-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Who hears about a post, and who may act on one.
--
-- `telegram-webhook` authorises nobody today: it checks the webhook secret, which proves the
-- request came from Telegram rather than who pressed the button, and then acts on whatever
-- chat the callback arrived from. This table is what closes that.
--
-- `can_approve` is separate from `notify` on purpose — somebody may need to see what is
-- going out without being able to send it.
-- ─────────────────────────────────────────────────────────────────────────────

create table if not exists public.social_telegram_recipients (
    chat_id     bigint      primary key,
    name        text        not null,
    notify      boolean     not null default true,
    can_approve boolean     not null default false,
    added_by    uuid        references public.admin_users (id) on delete set null,
    created_at  timestamptz not null default now()
);

comment on table public.social_telegram_recipients is
    'Telegram chats the approval message goes to, and which of them may act on it. Left '
    'empty on purpose: the webhook falls back to TELEGRAM_CHAT_ID from its own env while '
    'this table has no rows, so nobody is locked out of a pipeline that is already running. '
    'The first row added takes over, and the fallback stops applying.';


-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Connected accounts, and the secrets that are not in them.
--
-- `social_accounts` holds what a screen may show: the platform, what it is called, the id
-- the API is addressed by, and when its token stops working. It holds no token.
--
-- `social_credentials` holds the tokens and the Gemini key. It is deny-all with no view over
-- it and no read RPC, so the panel can write one and never read one back. The functions read
-- it with the service role, which is not subject to RLS.
-- ─────────────────────────────────────────────────────────────────────────────

create table if not exists public.social_accounts (
    id            uuid        primary key default gen_random_uuid(),
    platform      text        not null check (platform in ('instagram', 'facebook', 'telegram', 'other')),
    display_name  text        not null,
    -- The IG business user id, the FB page id — whatever the platform is addressed by.
    external_id   text        not null,
    enabled       boolean     not null default true,
    -- IG's long-lived token lasts about 60 days. Null means the platform does not expire one.
    token_expires_at timestamptz,
    connected_at  timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    unique (platform, external_id)
);

create table if not exists public.social_credentials (
    -- 'gemini_api_key', 'telegram_bot_token', or 'account:<uuid>' for a connected account.
    key        text        primary key,
    value      text        not null,
    updated_by uuid        references public.admin_users (id) on delete set null,
    updated_at timestamptz not null default now()
);

comment on table public.social_credentials is
    'Write-only from the panel: set through set_social_credential(), never selected back. '
    'Read by the edge functions, which run as the service role.';


-- ─────────────────────────────────────────────────────────────────────────────
-- 5. RLS.
--
-- The configuration tables answer to the content permission, which is what the owner asked
-- for. Credentials answer to nobody — no policy at all, so every authenticated select
-- returns nothing and the only way in is the function below.
-- ─────────────────────────────────────────────────────────────────────────────

do $$
declare t text;
begin
    foreach t in array array[
        'social_settings', 'social_schedule', 'social_telegram_recipients', 'social_accounts'
    ] loop
        execute format('alter table public.%I enable row level security', t);
        execute format('drop policy if exists %I on public.%I', t || '_admin', t);
        execute format(
            'create policy %I on public.%I for all to authenticated '
            'using (public.admin_has(''blog.write'')) '
            'with check (public.admin_has(''blog.write''))',
            t || '_admin', t);
        execute format('drop trigger if exists %I on public.%I', 'trg_' || t || '_updated', t);
    end loop;
end $$;

alter table public.social_credentials enable row level security;
-- Deliberately no policy. See the table comment.

drop trigger if exists trg_social_settings_updated on public.social_settings;
create trigger trg_social_settings_updated before update on public.social_settings
    for each row execute function public.set_updated_at();

drop trigger if exists trg_social_schedule_updated on public.social_schedule;
create trigger trg_social_schedule_updated before update on public.social_schedule
    for each row execute function public.set_updated_at();

drop trigger if exists trg_social_accounts_updated on public.social_accounts;
create trigger trg_social_accounts_updated before update on public.social_accounts
    for each row execute function public.set_updated_at();


-- ─────────────────────────────────────────────────────────────────────────────
-- 6. Setting a secret.
--
-- Takes a value and returns nothing. There is no counterpart that reads one, and that
-- asymmetry is the point: a panel that could show a token back is a panel that leaks every
-- token to anyone who reaches an admin session.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function public.set_social_credential(p_key text, p_value text)
returns void
language plpgsql
security definer
set search_path = public
as $function$
begin
    if not public.admin_has('blog.write') then
        raise exception 'not permitted';
    end if;
    if coalesce(trim(p_value), '') = '' then
        raise exception 'a credential cannot be blank';
    end if;

    insert into public.social_credentials (key, value, updated_by, updated_at)
    values (p_key, p_value, (select id from public.admin_users where user_id = auth.uid()), now())
    on conflict (key) do update
       set value = excluded.value, updated_by = excluded.updated_by, updated_at = now();
end;
$function$;

-- Which secrets exist and when they changed. Never the values.
create or replace view public.social_credential_status as
    select key, updated_at
      from public.social_credentials
     where public.admin_has('blog.write');

grant select on public.social_credential_status to authenticated;
grant execute on function public.set_social_credential(text, text) to authenticated;

create or replace function public.clear_social_credential(p_key text)
returns void
language plpgsql
security definer
set search_path = public
as $function$
begin
    if not public.admin_has('blog.write') then
        raise exception 'not permitted';
    end if;
    delete from public.social_credentials where key = p_key;
end;
$function$;

grant execute on function public.clear_social_credential(text) to authenticated;


-- ─────────────────────────────────────────────────────────────────────────────
-- 7. The pipeline's own tables, seen from `public`.
--
-- Plain views, owned by the migration's role, so they read past the deny-all RLS on the
-- `social` tables. The permission check is inside each one — a view with no WHERE here would
-- hand the queue to every authenticated user in the project.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace view public.social_queue as
    select q.id,
           q.bank_id,
           q.status,
           q.variant,
           q.approval,
           q.include_story,
           q.copy,
           q.post_image_url,
           q.story_image_url,
           q.error,
           q.created_at,
           q.updated_at
      from social.content_queue q
     where public.admin_has('blog.write');

create or replace view public.social_post_log as
    select p.id,
           p.queue_id,
           p.ig_media_id,
           p.ig_story_id,
           p.published_at
      from social.post_log p
     where public.admin_has('blog.write');

create or replace view public.social_content_bank as
    select b.id,
           b.category,
           b.fact,
           b.stats,
           b.screenshot,
           b.cta,
           b.last_used_at,
           b.created_at
      from social.content_bank b
     where public.admin_has('blog.write');

grant select on public.social_queue, public.social_post_log, public.social_content_bank to authenticated;


-- ─────────────────────────────────────────────────────────────────────────────
-- 8. Writing into `social` from the panel.
--
-- One function per action rather than a grant on the tables: the panel may move a queue item
-- between the states the pipeline understands, and may edit the fact bank, and nothing else.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function public.set_social_queue_status(p_id bigint, p_status text)
returns void
language plpgsql
security definer
set search_path = public
as $function$
begin
    if not public.admin_has('blog.write') then
        raise exception 'not permitted';
    end if;
    -- 'approved' is what the publisher picks up; the pipeline sets the rest itself.
    if p_status not in ('approved', 'rejected', 'draft') then
        raise exception 'unsupported status %', p_status;
    end if;

    update social.content_queue
       set status = p_status, updated_at = now()
     where id = p_id;
end;
$function$;

create or replace function public.upsert_social_fact(
    p_id bigint,
    p_category text,
    p_fact text,
    p_stats jsonb,
    p_cta text)
returns bigint
language plpgsql
security definer
set search_path = public
as $function$
declare v_id bigint;
begin
    if not public.admin_has('blog.write') then
        raise exception 'not permitted';
    end if;

    if p_id is null then
        insert into social.content_bank (category, fact, stats, cta)
        values (p_category, p_fact, p_stats, coalesce(nullif(p_cta, ''), 'Odo — free to start'))
        returning id into v_id;
    else
        update social.content_bank
           set category = p_category,
               fact = p_fact,
               stats = p_stats,
               cta = coalesce(nullif(p_cta, ''), cta)
         where id = p_id
        returning id into v_id;
    end if;

    return v_id;
end;
$function$;

create or replace function public.delete_social_fact(p_id bigint)
returns void
language plpgsql
security definer
set search_path = public
as $function$
begin
    if not public.admin_has('blog.write') then
        raise exception 'not permitted';
    end if;
    delete from social.content_bank where id = p_id;
end;
$function$;

grant execute on function public.set_social_queue_status(bigint, text) to authenticated;
grant execute on function public.upsert_social_fact(bigint, text, text, jsonb, text) to authenticated;
grant execute on function public.delete_social_fact(bigint) to authenticated;


-- ─────────────────────────────────────────────────────────────────────────────
-- 9. Audit.
--
-- The same trigger every other admin-written table carries, so a posting mode flipped at
-- 2am has a name against it.
-- ─────────────────────────────────────────────────────────────────────────────

do $$
declare t text;
begin
    foreach t in array array[
        'social_settings', 'social_schedule', 'social_telegram_recipients',
        'social_accounts', 'social_credentials'
    ] loop
        execute format('drop trigger if exists %I on public.%I', 'trg_' || t || '_audit', t);
        execute format(
            'create trigger %I after insert or update or delete on public.%I '
            'for each row execute function public.admin_audit()',
            'trg_' || t || '_audit', t);
    end loop;
end $$;

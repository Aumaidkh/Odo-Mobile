-- The blog and its CMS.
--
-- Everything here is prefixed `blog_`. It shares a database with the app because
-- it shares a Supabase project, and nothing else: no table below references a
-- car, a profile or an owner. The join between the two worlds is one row in
-- `blog_authors` whose email matches an `auth.users` account.
--
-- Reads are public. `blog_posts` is the only table with anything to hide, and
-- what it hides is drafts — the policy below is the whole of it. Writes need an
-- account carrying `blog_author` in its app_metadata, which only the
-- `blog-session` edge function issues.

-- ── Categories ──────────────────────────────────────────────────────────────
-- A short, hand-curated list. The slug is the primary key because it is what the
-- URL carries and what a post points at; a surrogate id would mean a join to
-- render a nav that never changes.

create table if not exists public.blog_categories (
  slug        text primary key,
  name        text        not null,
  blurb       text        not null default '',
  -- The order the nav and the filter chips show them in. Not alphabetical: the
  -- first chip should be the one most people came for.
  position    integer     not null default 0,
  created_at  timestamptz not null default now()
);

-- ── Authors ─────────────────────────────────────────────────────────────────
-- `email` is the join to Firebase and to `auth.users`. Unique, because two
-- author rows for one mailbox would make "who wrote this" ambiguous.

create table if not exists public.blog_authors (
  id           uuid        primary key default gen_random_uuid(),
  slug         text        not null unique,
  email        text        not null unique,
  name         text        not null,
  -- What the avatar circle draws until there is a photograph to draw instead.
  initial      text        not null default '',
  bio          text        not null default '',
  topics       text        not null default '',
  -- "March 2025". A label, not a date: nothing computes on it.
  since_label  text        not null default '',
  created_at   timestamptz not null default now()
);

-- ── Posts ───────────────────────────────────────────────────────────────────
--
-- The body is `jsonb`, holding the same block list the app already models. A
-- normalised block table would be queryable, and nothing wants to query inside a
-- paragraph — what gets queried is the title and the dek, which have their own
-- index below. One row per post also means a draft saves in one round trip
-- rather than a delete-and-reinsert of its blocks.
--
-- `slug` is nullable and unique. A draft has no URL yet, and the design says so
-- in as many words; null is the only value that cannot be confused with a slug
-- somebody chose.

create table if not exists public.blog_posts (
  id               uuid        primary key default gen_random_uuid(),
  slug             text        unique,
  title            text        not null default '',
  dek              text        not null default '',
  category_slug    text        references public.blog_categories (slug) on delete set null,
  author_id        uuid        references public.blog_authors (id) on delete set null,
  status           text        not null default 'draft' check (status in ('draft', 'published')),
  body             jsonb       not null default '[]'::jsonb,
  seo_title        text        not null default '',
  meta_description text        not null default '',
  word_count       integer     not null default 0,
  reading_minutes  integer     not null default 1,
  published_on     date,
  -- Lifetime total. The 30-day window comes from blog_post_daily_views.
  views            bigint      not null default 0,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

-- A published post must have the two things a URL and a search result need.
alter table public.blog_posts
  drop constraint if exists blog_posts_published_needs_slug;
alter table public.blog_posts
  add constraint blog_posts_published_needs_slug
  check (status = 'draft' or (slug is not null and published_on is not null));

-- The index page and every category page are this ordering.
create index if not exists blog_posts_published_idx
  on public.blog_posts (published_on desc)
  where status = 'published';

create index if not exists blog_posts_category_idx
  on public.blog_posts (category_slug, published_on desc)
  where status = 'published';

-- Search.
--
-- A generated column rather than an expression index, so PostgREST can filter on
-- it directly (`search_vector=fts(simple).challan`) without the client knowing
-- how the vector is built. `simple` and not `english`: these posts are Indian
-- English with Hindi words in them, and the English stemmer mangles those while
-- adding nothing for a corpus this size.
alter table public.blog_posts
  add column if not exists search_vector tsvector
  generated always as (
    to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(dek, ''))
  ) stored;

create index if not exists blog_posts_search_idx
  on public.blog_posts using gin (search_vector);

-- ── Daily views ─────────────────────────────────────────────────────────────
--
-- One row per post per day, so "last 30 days" is a real number rather than a
-- lifetime total with a misleading label. Bounded by design: a post that has run
-- for three years holds about a thousand rows.
--
-- `from_search` is counted separately because it is the number that says whether
-- any of this is working. It comes from `document.referrer`, which the browser
-- gives away for free and which nothing else here records.

create table if not exists public.blog_post_daily_views (
  post_id      uuid   not null references public.blog_posts (id) on delete cascade,
  day          date   not null default current_date,
  views        bigint not null default 0,
  from_search  bigint not null default 0,
  primary key (post_id, day)
);

-- ── Media ───────────────────────────────────────────────────────────────────
-- The row is the index; the file itself lives in the `blog-media` storage
-- bucket. `path` is what is appended to the bucket's public URL.

create table if not exists public.blog_media (
  id          uuid        primary key default gen_random_uuid(),
  name        text        not null,
  path        text        not null unique,
  alt_text    text        not null default '',
  created_at  timestamptz not null default now()
);

-- ── Inbound from readers ────────────────────────────────────────────────────
--
-- Anybody may write a row; nobody but an author may read one. That asymmetry is
-- the point — these hold addresses that were given to us for one purpose.

create table if not exists public.blog_subscribers (
  email       text        primary key,
  created_at  timestamptz not null default now()
);

create table if not exists public.blog_topic_requests (
  id          uuid        primary key default gen_random_uuid(),
  email       text        not null,
  -- What they searched for and did not find. Without it this is a mailing-list
  -- signup rather than a request for anything.
  query       text        not null default '',
  created_at  timestamptz not null default now()
);

-- ── Who counts as an author ─────────────────────────────────────────────────
--
-- The claim is stamped by the `blog-session` edge function and nowhere else, so
-- an ordinary app account — every one of which signs in by phone — can never
-- carry it. `app_metadata` and not `user_metadata`: the second is writable by the
-- user it belongs to.

create or replace function public.is_blog_author()
returns boolean
language sql
stable
as $$
  select coalesce((auth.jwt() -> 'app_metadata' ->> 'blog_author')::boolean, false)
$$;

-- ── Row level security ──────────────────────────────────────────────────────

alter table public.blog_categories       enable row level security;
alter table public.blog_authors          enable row level security;
alter table public.blog_posts            enable row level security;
alter table public.blog_post_daily_views enable row level security;
alter table public.blog_media            enable row level security;
alter table public.blog_subscribers      enable row level security;
alter table public.blog_topic_requests   enable row level security;

drop policy if exists blog_categories_read on public.blog_categories;
create policy blog_categories_read on public.blog_categories
  for select using (true);

drop policy if exists blog_categories_write on public.blog_categories;
create policy blog_categories_write on public.blog_categories
  for all using (public.is_blog_author()) with check (public.is_blog_author());

-- The bio and the name are on every byline; the email is not selected by the
-- client, and RLS is per row rather than per column, so the address of an author
-- is readable by anybody who asks for it directly. That is the same exposure a
-- byline already has, and it is worth knowing rather than assuming otherwise.
drop policy if exists blog_authors_read on public.blog_authors;
create policy blog_authors_read on public.blog_authors
  for select using (true);

drop policy if exists blog_authors_write on public.blog_authors;
create policy blog_authors_write on public.blog_authors
  for all using (public.is_blog_author()) with check (public.is_blog_author());

-- The whole of what the public side may see: published posts, and nothing else.
-- A draft is invisible even to somebody who guesses its id.
drop policy if exists blog_posts_read_published on public.blog_posts;
create policy blog_posts_read_published on public.blog_posts
  for select using (status = 'published');

drop policy if exists blog_posts_author_all on public.blog_posts;
create policy blog_posts_author_all on public.blog_posts
  for all using (public.is_blog_author()) with check (public.is_blog_author());

-- Counts are written by a security-definer function, never directly.
drop policy if exists blog_daily_views_author_read on public.blog_post_daily_views;
create policy blog_daily_views_author_read on public.blog_post_daily_views
  for select using (public.is_blog_author());

drop policy if exists blog_media_read on public.blog_media;
create policy blog_media_read on public.blog_media
  for select using (true);

drop policy if exists blog_media_author_write on public.blog_media;
create policy blog_media_author_write on public.blog_media
  for all using (public.is_blog_author()) with check (public.is_blog_author());

drop policy if exists blog_subscribers_insert on public.blog_subscribers;
create policy blog_subscribers_insert on public.blog_subscribers
  for insert with check (true);

drop policy if exists blog_subscribers_author_read on public.blog_subscribers;
create policy blog_subscribers_author_read on public.blog_subscribers
  for select using (public.is_blog_author());

drop policy if exists blog_topic_requests_insert on public.blog_topic_requests;
create policy blog_topic_requests_insert on public.blog_topic_requests
  for insert with check (true);

drop policy if exists blog_topic_requests_author_read on public.blog_topic_requests;
create policy blog_topic_requests_author_read on public.blog_topic_requests
  for select using (public.is_blog_author());

-- ── Counting a read ─────────────────────────────────────────────────────────
--
-- Security definer, because the reader has no rights on either table and should
-- not be given any: the only write anonymous traffic can make is +1 on a post
-- that is already published, through this one door.
--
-- It is deliberately not idempotent. A refresh counts twice, the same way it
-- does in every analytics product that measures page views, and pretending
-- otherwise would need a session identity that nothing here has.

create or replace function public.blog_record_view(p_slug text, p_from_search boolean default false)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_post_id uuid;
begin
  select id into v_post_id
  from public.blog_posts
  where slug = p_slug and status = 'published';

  if v_post_id is null then
    return;
  end if;

  update public.blog_posts
  set views = views + 1
  where id = v_post_id;

  insert into public.blog_post_daily_views (post_id, day, views, from_search)
  values (v_post_id, current_date, 1, case when p_from_search then 1 else 0 end)
  on conflict (post_id, day) do update
  set views = public.blog_post_daily_views.views + 1,
      from_search = public.blog_post_daily_views.from_search
        + case when p_from_search then 1 else 0 end;
end;
$$;

revoke all on function public.blog_record_view(text, boolean) from public;
grant execute on function public.blog_record_view(text, boolean) to anon, authenticated;

-- ── The thirty-day window ───────────────────────────────────────────────────
--
-- A function rather than a view, so PostgREST exposes it as one RPC returning
-- one row. The analytics screen makes a single call for its three numbers
-- instead of three round trips it would then have to line up.

create or replace function public.blog_analytics(p_days integer default 30)
returns table (views bigint, from_search bigint, days integer)
language sql
stable
security definer
set search_path = public
as $$
  select
    coalesce(sum(v.views), 0)::bigint,
    coalesce(sum(v.from_search), 0)::bigint,
    p_days
  from public.blog_post_daily_views v
  where v.day > current_date - p_days
$$;

revoke all on function public.blog_analytics(integer) from public;
grant execute on function public.blog_analytics(integer) to authenticated;

-- ── Keeping updated_at honest ───────────────────────────────────────────────

create or replace function public.blog_touch_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists blog_posts_touch on public.blog_posts;
create trigger blog_posts_touch
  before update on public.blog_posts
  for each row execute function public.blog_touch_updated_at();

-- ── Looking an author's account up ──────────────────────────────────────────
--
-- The same shape as `auth_user_id_by_phone`, for the same reason: the admin SDK
-- has no get-by-email, and `listUsers` pages through every account in the project
-- and gets slower with every signup. This is one indexed lookup.
--
-- SECURITY DEFINER because `auth.users` is readable by no client role, and must
-- stay that way — this returns an id and nothing else, so it cannot be used to
-- enumerate addresses.

create or replace function public.auth_user_id_by_email(p_email text)
returns uuid
language sql
security definer
-- Empty search_path, so a schema planted on the caller's path cannot shadow
-- auth.users. Every object below is therefore schema-qualified.
set search_path = ''
stable
as $$
  select u.id
  from auth.users u
  where lower(u.email) = lower(p_email)
  limit 1;
$$;

-- The default grant on a new function is to PUBLIC, which would put it on the
-- anon role's PostgREST surface — an unauthenticated "does this address have an
-- account?" oracle. Only the Edge Function may call it.
revoke all on function public.auth_user_id_by_email(text) from public;
revoke all on function public.auth_user_id_by_email(text) from anon;
revoke all on function public.auth_user_id_by_email(text) from authenticated;
grant execute on function public.auth_user_id_by_email(text) to service_role;

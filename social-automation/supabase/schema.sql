-- IG automation schema. Runs in the app's Supabase project, isolated in its own
-- schema. All tables are service-role only: RLS enabled, no policies.

create schema if not exists social;

-- Verified facts the AI is allowed to write copy around. The AI never invents
-- numbers; every stat in a post traces back to a row here.
create table if not exists social.content_bank (
  id            bigint generated always as identity primary key,
  category      text not null,             -- fine | money_saved | demo | educational | seasonal | resale
  fact          text not null,             -- the verified claim, plain Hinglish
  stats         jsonb,                     -- up to 3 {label, value} pairs shown on the card, pre-verified
  screenshot    text,                      -- optional filename in renderer/templates/screenshots/
  cta           text not null default 'Odo — free to start',
  last_used_at  timestamptz,
  created_at    timestamptz not null default now()
);

create table if not exists social.content_queue (
  id             bigint generated always as identity primary key,
  bank_id        bigint references social.content_bank(id),
  status         text not null default 'draft',  -- draft | rendered | published | rejected | failed
  variant        text not null default 'stat',   -- stat | screenshot
  include_story  boolean not null default true,  -- din ki pehli slot story bhi le jati hai, baaki sirf post
  copy           jsonb not null,                 -- Gemini output: headline, stats, footer, story_*, caption, hashtags
  post_image_url text,                           -- set by renderer (public storage URL, 1080x1350)
  story_image_url text,                          -- set by renderer (1080x1920, also the WA-status image)
  telegram_message_id bigint,                    -- approval message, for edit-on-result
  error          text,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);

-- What actually went live, with IG media ids.
create table if not exists social.post_log (
  id            bigint generated always as identity primary key,
  queue_id      bigint references social.content_queue(id),
  ig_media_id   text,
  ig_story_id   text,
  published_at  timestamptz not null default now()
);

-- Runtime config the functions can write back (IG token gets refreshed here;
-- env vars can't be rewritten from a function).
create table if not exists social.app_config (
  key        text primary key,
  value      text not null,
  updated_at timestamptz not null default now()
);

alter table social.content_bank  enable row level security;
alter table social.content_queue enable row level security;
alter table social.post_log      enable row level security;
alter table social.app_config    enable row level security;

-- Storage bucket for rendered images (public read so IG can fetch image_url).
insert into storage.buckets (id, name, public)
values ('social-posts', 'social-posts', true)
on conflict (id) do nothing;

-- ---------------------------------------------------------------------------
-- pg_cron schedules — uncomment after functions are deployed and secrets set.
-- Replace YOUR_PROJECT_REF and YOUR_SERVICE_ROLE_KEY (or use vault).
-- ---------------------------------------------------------------------------
-- 3 posts/day: 09:00 IST (with story), 14:00 IST, 19:00 IST.
-- select cron.schedule(
--   'social-generate-morning', '30 3 * * *',
--   $$ select net.http_post(
--        url     := 'https://YOUR_PROJECT_REF.supabase.co/functions/v1/generate',
--        headers := '{"Authorization": "Bearer YOUR_ANON_KEY", "Content-Type": "application/json"}'::jsonb,
--        body    := '{"story": true}'::jsonb) $$);
-- select cron.schedule(
--   'social-generate-afternoon', '30 8 * * *',
--   $$ ... body := '{"story": false}'::jsonb ... $$);
-- select cron.schedule(
--   'social-generate-evening', '30 13 * * *',
--   $$ ... body := '{"story": false}'::jsonb ... $$);
--
-- select cron.schedule(
--   'social-refresh-ig-token', '0 4 */50 * *',
--   $$ select net.http_post(
--        url     := 'https://YOUR_PROJECT_REF.supabase.co/functions/v1/refresh-ig-token',
--        headers := '{"Authorization": "Bearer YOUR_SERVICE_ROLE_KEY"}'::jsonb
--      ) $$);

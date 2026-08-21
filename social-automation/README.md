# Odo Social Automation

AI-generated Instagram feed posts + stories for @odo.app. One post a day, owner
approves on Telegram before anything goes live. Runs entirely on free tiers.
Plan and locked decisions: `docs/IG_AUTOMATION_PLAN.md` (local-only).

```
pg_cron (daily) → generate fn (Gemini writes copy around a verified fact)
              → GitHub Actions renderer (Puppeteer renders the design templates)
              → Telegram preview [Approve] [Reject]
              → telegram-webhook fn publishes via IG Graph API
```

Hard rule baked into the pipeline: the AI never invents numbers. Every stat on a
card comes from `social.content_bank`, which the owner curates.

## Layout

| Path | What |
|---|---|
| `supabase/schema.sql` | `social` schema (content_bank, content_queue, post_log, app_config) + storage bucket + pg_cron templates |
| `supabase/seed_content_bank.sql` | Starter facts (growth-plan hooks) |
| `supabase/functions/generate` | Daily draft creation (Gemini free tier) |
| `supabase/functions/telegram-webhook` | Approve/Reject handler + IG publish |
| `supabase/functions/refresh-ig-token` | Rotates the ~60-day IG token every ~50 days |
| `renderer/` | Puppeteer worker + design templates (extracted from Social Templates) + `render-workflow.yml` (copy to `.github/workflows/`) |
| `.env.example` | Every secret this system needs, as placeholders |

## Setup (one-time)

All secrets are placeholders (`YOUR_..._HERE`) — nothing here works until filled.

**1. Meta / Instagram (owner, ~30 min)**
- Convert @odo.app to a Professional (Business) account.
- Create an app at developers.facebook.com → add the "Instagram" product with
  Instagram Login → connect the @odo.app account.
- Generate a long-lived access token (~60 days) and note the IG user id.
- Needed values: `IG_USER_ID`, `IG_ACCESS_TOKEN`.

**2. Gemini** — create a free API key at aistudio.google.com/apikey → `GEMINI_API_KEY`.

**3. Telegram** — message @BotFather → `/newbot` → `TELEGRAM_BOT_TOKEN`. Message
@userinfobot for your `TELEGRAM_CHAT_ID`. Pick any random string as
`TELEGRAM_WEBHOOK_SECRET`. Send your bot one message so it can reply to you.

**4. Supabase (app's existing project)**
- Run `supabase/schema.sql`, then `supabase/seed_content_bank.sql` (SQL editor).
- Dashboard → Settings → API → **Exposed schemas** → add `social` (renderer and
  functions talk to PostgREST with the `social` profile).
- Set function secrets:
  `supabase secrets set GEMINI_API_KEY=... TELEGRAM_BOT_TOKEN=... TELEGRAM_WEBHOOK_SECRET=... IG_USER_ID=... IG_ACCESS_TOKEN=...`
- Deploy functions (from `social-automation/supabase`):
  `supabase functions deploy generate refresh-ig-token`
  `supabase functions deploy telegram-webhook --no-verify-jwt`
- Register the Telegram webhook (curl in `telegram-webhook/index.ts` header).
- Uncomment + fill the two `cron.schedule` blocks in `schema.sql` and run them.

**5. Renderer (GitHub Actions)**
- Copy `renderer/render-workflow.yml` → `.github/workflows/social-render.yml`.
- Add repo secrets: `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`,
  `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`.
- Drop real app screenshots (PNG) into `renderer/templates/screenshots/` and
  reference filenames from `content_bank.screenshot` for screenshot-variant posts.

## Test without waiting for cron

```sh
# 1. Create a draft
curl -X POST "https://YOUR_PROJECT_REF.supabase.co/functions/v1/generate" \
  -H "Authorization: Bearer YOUR_SERVICE_ROLE_KEY"

# 2. Render + get the Telegram preview (or run the workflow manually on GitHub)
cd renderer && npm install && \
SUPABASE_URL=... SUPABASE_SERVICE_ROLE_KEY=... \
TELEGRAM_BOT_TOKEN=... TELEGRAM_CHAT_ID=... node render.js

# 3. Tap Approve in Telegram → post goes live, log lands in social.post_log.
```

## Notes

- WhatsApp status has no API — the story PNG doubles as the status image; post it
  manually from the Telegram message.
- Reels are out of scope by decision: the Graph API can't attach trending audio,
  so reels stay in the human editor pipeline.
- IG API limit is ~50 posts/day; this system does 1–2.
- Templates render at design size and scale up (400×500 @2.7 → 1080×1350,
  360×640 @3 → 1080×1920), so the card CSS stays byte-identical to the design
  export in `Social Templates.dc.html`.

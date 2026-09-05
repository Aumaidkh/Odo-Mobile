// Renderer worker. Runs in GitHub Actions on a schedule:
//   drafts (social.content_queue) → template PNG (Puppeteer, exact design HTML)
//   → Supabase Storage (public URL) → Telegram preview with Approve/Reject.
//
// Env (GitHub Actions secrets): SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY,
//                               TELEGRAM_BOT_TOKEN, TELEGRAM_CHAT_ID

import puppeteer from "puppeteer";
import { readFileSync, existsSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));

const SUPABASE_URL = process.env.SUPABASE_URL ?? "https://YOUR_PROJECT_REF.supabase.co";
const SERVICE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY ?? "YOUR_SERVICE_ROLE_KEY_HERE";
const BOT_TOKEN = process.env.TELEGRAM_BOT_TOKEN ?? "YOUR_TELEGRAM_BOT_TOKEN_HERE";
const CHAT_ID = process.env.TELEGRAM_CHAT_ID ?? "YOUR_TELEGRAM_CHAT_ID_HERE";

const rest = (path) => `${SUPABASE_URL}/rest/v1/${path}`;
// Legacy JWT service keys go in Authorization; new sb_secret_ keys must use only
// the apikey header (storage rejects non-JWT Authorization with "Invalid Compact JWS").
const authHeaders = () => ({
  apikey: SERVICE_KEY,
  ...(SERVICE_KEY.startsWith("sb_") ? {} : { Authorization: `Bearer ${SERVICE_KEY}` }),
});
const dbHeaders = (write = false) => ({
  ...authHeaders(),
  "Content-Type": "application/json",
  // content_queue lives in the "social" schema (must be in Supabase API "Exposed schemas")
  [write ? "Content-Profile" : "Accept-Profile"]: "social",
});

const esc = (s) => String(s ?? "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

function fill(template, tokens) {
  return Object.entries(tokens).reduce(
    (html, [key, value]) => html.replaceAll(`{{${key}}}`, value),
    template,
  );
}

function statsChips(stats) {
  const chips = (stats ?? []).slice(0, 3);
  return chips
    .map((s, i) => {
      const last = i === chips.length - 1 && chips.length > 1;
      const bg = last ? "background:#FFFFFF" : "background:#141414;border:1px solid #262626";
      const label = last ? "color:#374151" : "color:#9CA3AF";
      const value = last ? "color:#000000" : "color:#FFFFFF";
      return `<div style="flex:1;${bg};border-radius:14px;padding:13px 14px;display:flex;flex-direction:column;gap:3px"><span style="font-size:9.5px;font-weight:700;letter-spacing:1px;${label}">${esc(s.label)}</span><span style="font-size:20px;font-weight:700;letter-spacing:-0.7px;${value}">${esc(s.value)}</span></div>`;
    })
    .join("");
}

function screenshotDataUri(name) {
  const file = join(here, "templates", "screenshots", name ?? "");
  if (!name || !existsSync(file)) return null;
  return `data:image/png;base64,${readFileSync(file).toString("base64")}`;
}

async function renderCard(page, html, selector, width, height, scale) {
  await page.setViewport({ width, height, deviceScaleFactor: scale });
  await page.setContent(html, { waitUntil: "load", timeout: 60000 });
  await page.evaluate(() => document.fonts.ready);
  const el = await page.$(selector);
  return await el.screenshot({ type: "png" });
}

async function upload(name, png) {
  const res = await fetch(`${SUPABASE_URL}/storage/v1/object/social-posts/${name}`, {
    method: "POST",
    headers: { ...authHeaders(), "Content-Type": "image/png", "x-upsert": "true" },
    body: png,
  });
  if (!res.ok) throw new Error(`storage upload ${name}: ${res.status} ${await res.text()}`);
  return `${SUPABASE_URL}/storage/v1/object/public/social-posts/${name}`;
}

async function tg(method, body) {
  const res = await fetch(`https://api.telegram.org/bot${BOT_TOKEN}/${method}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const json = await res.json();
  if (!json.ok) throw new Error(`telegram ${method}: ${JSON.stringify(json)}`);
  return json.result;
}

/**
 * What the admin panel has set. Read once per run.
 *
 * The pause has to be honoured here as well as in `generate`: stopping new drafts while the
 * renderer keeps pushing already-queued ones to Telegram is not a pause, and this is the
 * switch somebody reaches for when something is going wrong.
 */
async function settings() {
  // `public`, not `social`: the panel's tables live there, and dbHeaders() pins this
  // renderer's requests to the pipeline's own schema.
  const res = await fetch(rest("social_settings?select=paused&limit=1"), {
    headers: { ...dbHeaders(), "Accept-Profile": "public" },
  });
  if (!res.ok) return { paused: false };
  const rows = await res.json();
  return rows[0] ?? { paused: false };
}

async function main() {
  const { paused } = await settings();
  if (paused) {
    console.log("Pipeline is paused — rendering nothing.");
    return;
  }

  const draftsRes = await fetch(rest("content_queue?status=eq.draft&select=*,content_bank(screenshot)"), { headers: dbHeaders() });
  if (!draftsRes.ok) throw new Error(`fetch drafts: ${draftsRes.status} ${await draftsRes.text()}`);
  const drafts = await draftsRes.json();
  if (drafts.length === 0) {
    console.log("No drafts to render.");
    return;
  }

  const postTpl = readFileSync(join(here, "templates", "ig-post.html"), "utf8");
  const storyTpl = readFileSync(join(here, "templates", "ig-story.html"), "utf8");

  const browser = await puppeteer.launch({ args: ["--no-sandbox", "--font-render-hinting=none"] });
  const page = await browser.newPage();

  for (const draft of drafts) {
    const c = draft.copy;
    console.log(`Rendering #${draft.id} (${draft.variant})…`);

    const shot = screenshotDataUri(draft.content_bank?.screenshot);
    const useShotVariant = draft.variant === "screenshot" && shot;
    const postHtml = fill(postTpl, {
      HEADLINE: esc(c.headline),
      FOOTER: esc(c.footer),
      CTA: esc(c.cta),
      STATS_CHIPS: statsChips(c.stats),
      SCREENSHOT_SRC: shot ?? "",
    });
    // 400×500 @2.7 = 1080×1350 · 360×640 @3 = 1080×1920 (design sizes from Social Templates)
    const postPng = await renderCard(page, postHtml, useShotVariant ? "#shot-card" : "#stat-card", 420, 520, 2.7);

    let storyUrl = null;
    if (draft.include_story) {
      const storyHtml = fill(storyTpl, {
        STORY_KICKER: esc(c.story_kicker),
        STORY_NUMBER: esc(c.story_number),
        STORY_UNIT: esc(c.story_unit),
        STORY_CAPTION: esc(c.story_caption),
        CTA: esc(c.cta),
      });
      const storyPng = await renderCard(page, storyHtml, "#story-card", 380, 660, 3);
      storyUrl = await upload(`${draft.id}-story.png`, storyPng);
    }
    const postUrl = await upload(`${draft.id}-post.png`, postPng);

    // Auto posts are not offered to anybody — that is the whole of the mode. They still go
    // to Telegram, because a post that went out with nobody told is a post nobody can catch,
    // but without the buttons and marked `approved`, which is what the tick publishes from.
    const auto = draft.approval === "auto";

    const preview = await tg("sendPhoto", {
      chat_id: CHAT_ID,
      photo: postUrl,
      caption: auto
        ? `#${draft.id} · going out automatically\n\n${c.caption}\n\n${c.hashtags}`
        : `#${draft.id} · ${useShotVariant ? "screenshot" : "stat"} card\n\n${c.caption}\n\n${c.hashtags}`,
      ...(auto ? {} : {
        reply_markup: {
          inline_keyboard: [[
            { text: "✅ Approve", callback_data: `approve:${draft.id}` },
            { text: "❌ Reject", callback_data: `reject:${draft.id}` },
          ]],
        },
      }),
    });
    if (storyUrl) {
      await tg("sendPhoto", { chat_id: CHAT_ID, photo: storyUrl, caption: `#${draft.id} · IG story` });
    }

    const patch = await fetch(rest(`content_queue?id=eq.${draft.id}`), {
      method: "PATCH",
      headers: dbHeaders(true),
      body: JSON.stringify({
        status: auto ? "approved" : "rendered",
        post_image_url: postUrl,
        story_image_url: storyUrl,
        telegram_message_id: preview.message_id,
        updated_at: new Date().toISOString(),
      }),
    });
    if (!patch.ok) throw new Error(`patch #${draft.id}: ${patch.status} ${await patch.text()}`);
    console.log(`#${draft.id} rendered → ${auto ? "queued to publish automatically" : "Telegram preview sent"}.`);
  }

  await browser.close();
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});

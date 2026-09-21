// Renderer worker. Runs in GitHub Actions on a schedule:
//   drafts (social.content_queue) → template PNG(s) (Puppeteer, exact design HTML)
//   → Supabase Storage (public URL) → Telegram preview with Approve/Reject.
//
// Formats:
//   single   — one feed card (+ optional IG story) → photo preview with buttons
//   carousel — 4–7 slides → album preview + a button message
//   wa_story — WhatsApp status PNG → delivered on Telegram, posted manually
//              (WhatsApp has no status API), no approval step
//
// Templates load from the local templates/ dir when present, else from the
// social-renderer Storage bucket — so adding a template never needs a workflow
// (default-branch) change, only `sh social-automation/upload-renderer.sh`.
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

async function loadTemplate(name) {
  const file = join(here, "templates", name);
  if (existsSync(file)) return readFileSync(file, "utf8");
  const res = await fetch(`${SUPABASE_URL}/storage/v1/object/social-renderer/templates/${name}`, {
    headers: authHeaders(),
  });
  if (!res.ok) throw new Error(`template ${name}: ${res.status} ${await res.text()}`);
  return await res.text();
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

// Rows card used on dark + odo carousel slides. Dark slides highlight values in
// amber (the problem), the odo slide answers in white (the fix) — per the design.
function rowsCard(rows, valueColor) {
  const list = (rows ?? []).slice(0, 3);
  if (list.length === 0) return "";
  const inner = list
    .map((r) => `<div style="display:flex;gap:11px;align-items:baseline"><span style="font-size:13px;color:#9CA3AF;flex:1">${esc(r.label)}</span><span style="font-size:13px;font-weight:700;color:${valueColor}">${esc(r.value)}</span></div>`)
    .join(`<div style="height:1px;background:#262626"></div>`);
  return `<div style="display:flex;flex-direction:column;gap:11px;background:#141414;border:1px solid #262626;border-radius:14px;padding:16px 17px">${inner}</div>`;
}

function stepsList(steps) {
  return (steps ?? []).slice(0, 3)
    .map((step, i) => `<div style="display:flex;gap:11px;align-items:baseline"><span style="font-size:12px;font-weight:700;color:#6B7280;width:14px">${i + 1}</span><span style="font-size:15px;color:#000000;flex:1;line-height:1.4">${esc(step)}</span></div>`)
    .join("");
}

function progressBar(total, active) {
  return Array.from({ length: total }, (_, i) =>
    i === active
      ? `<span style="width:18px;height:3px;border-radius:2px;background:#FFFFFF"></span>`
      : `<span style="width:8px;height:3px;border-radius:2px;background:#262626"></span>`,
  ).join("");
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

function multipart(body, files) {
  const form = new FormData();
  for (const [key, value] of Object.entries(body)) {
    form.append(key, typeof value === "object" ? JSON.stringify(value) : String(value));
  }
  for (const [name, png] of Object.entries(files)) {
    form.append(name, new Blob([png], { type: "image/png" }), `${name}.png`);
  }
  return form;
}

async function tg(method, body, files = null) {
  const res = await fetch(`https://api.telegram.org/bot${BOT_TOKEN}/${method}`, {
    method: "POST",
    ...(files
      ? { body: multipart(body, files) }
      : { headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }),
  });
  const json = await res.json();
  if (!json.ok) throw new Error(`telegram ${method}: ${JSON.stringify(json)}`);
  return json.result;
}

// Telegram gets the PNG bytes, never the Storage URL. Once its fetcher fails on a URL it
// keeps failing for that URL, and the renderer re-uploads under the same name every run,
// so one bad fetch (draft #170) blocks that draft forever.
const sendPhoto = (chat, png, caption, keyboard = null) =>
  tg("sendPhoto", { chat_id: chat, caption, ...(keyboard ? { reply_markup: keyboard } : {}) }, { photo: png });

const sendAlbum = (chat, pngs, caption) =>
  tg(
    "sendMediaGroup",
    {
      chat_id: chat,
      media: pngs.map((_, i) => ({ type: "photo", media: `attach://p${i}`, ...(i === 0 ? { caption } : {}) })),
    },
    Object.fromEntries(pngs.map((png, i) => [`p${i}`, png])),
  );

const approvalKeyboard = (id) => ({
  inline_keyboard: [[
    { text: "✅ Approve", callback_data: `approve:${id}` },
    { text: "❌ Reject", callback_data: `reject:${id}` },
  ]],
});

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

// FB copies skip the owner's approval gate (owner's call): they go straight to
// the FB uploader's chat at render time. When a FB Page is configured
// (app_config.fb_page_id + token), the copy carries its own
// [✅ FB pe post karo] [❌ Reject] buttons and the webhook publishes to the
// Page on tap; without a Page it's a plain delivery for manual upload.
// Falls back to the approver chat if the send fails (user not /start-ed yet).
async function fbConfig() {
  const res = await fetch(rest("app_config?key=in.(fb_telegram_chat_id,fb_page_id)&select=key,value"), { headers: dbHeaders() });
  const rows = res.ok ? await res.json() : [];
  const map = Object.fromEntries(rows.map((r) => [r.key, r.value]));
  return { chat: map.fb_telegram_chat_id ?? CHAT_ID, pageConfigured: Boolean(map.fb_page_id) };
}

// Returns true when the copy went out with publish buttons (fb_status → pending).
async function sendFbCopy(fb, draftId, pngs, caption) {
  const header = `📘 #${draftId} · Facebook post\n\n${caption}`;
  const keyboard = {
    inline_keyboard: [[
      { text: "✅ FB pe post karo", callback_data: `fb_approve:${draftId}` },
      { text: "❌ Reject", callback_data: `fb_reject:${draftId}` },
    ]],
  };
  const send = async (chat) => {
    if (!fb.pageConfigured) {
      // No Page token yet — plain delivery, manual upload.
      if (pngs.length === 1) await sendPhoto(chat, pngs[0], header);
      else await sendAlbum(chat, pngs, header);
    } else if (pngs.length === 1) {
      await sendPhoto(chat, pngs[0], header, keyboard);
    } else {
      await sendAlbum(chat, pngs, header);
      await tg("sendMessage", { chat_id: chat, text: `#${draftId} · FB Page pe post karein?`, reply_markup: keyboard });
    }
  };
  try {
    await send(fb.chat);
  } catch (e) {
    if (String(fb.chat) === String(CHAT_ID)) throw e;
    await tg("sendMessage", { chat_id: CHAT_ID, text: `⚠️ FB user (${fb.chat}) tak copy nahi gayi — usne bot start nahi kiya? Copy neeche:` });
    await send(CHAT_ID);
  }
  return fb.pageConfigured;
}

// Slide type → card selector in ig-carousel.html.
const SLIDE_SELECTOR = {
  cover: "#cover-card",
  dark: "#dark-card",
  stats: "#stats-card",
  action: "#action-card",
  odo: "#odo-card",
};

function slideTokens(slide, index, total, cta) {
  return {
    C_HEADLINE: esc(slide.headline),
    C_SUB: esc(slide.sub),
    C_FOOTER: esc(slide.footer),
    SLIDE_NO: String(index + 1).padStart(2, "0"),
    PROGRESS: progressBar(total, index),
    ROWS: rowsCard(slide.rows, slide.type === "odo" ? "#FFFFFF" : "#D97706"),
    STEPS: stepsList(slide.steps),
    K1: esc(slide.kicker1),
    V1: esc(slide.value1),
    K2: esc(slide.kicker2),
    V2: esc(slide.value2),
    CTA: esc(cta),
  };
}

async function renderStory(page, storyTpl, c) {
  const storyHtml = fill(storyTpl, {
    STORY_KICKER: esc(c.story_kicker),
    STORY_NUMBER: esc(c.story_number),
    STORY_UNIT: esc(c.story_unit),
    STORY_CAPTION: esc(c.story_caption),
    CTA: esc(c.cta),
  });
  return await renderCard(page, storyHtml, "#story-card", 380, 660, 3);
}

async function patchDraft(id, patchBody) {
  const patch = await fetch(rest(`content_queue?id=eq.${id}`), {
    method: "PATCH",
    headers: dbHeaders(true),
    body: JSON.stringify({ ...patchBody, updated_at: new Date().toISOString() }),
  });
  if (!patch.ok) throw new Error(`patch #${id}: ${patch.status} ${await patch.text()}`);
}

async function renderDraft(page, tpl, fb, draft) {
  const c = draft.copy;
  const format = draft.format ?? "single";
  console.log(`Rendering #${draft.id} (${format})…`);
  const fbNote = draft.crosspost_fb ? "\n📘 FB copy uploader ko bhej di gayi hai." : "";
  // Auto posts are not offered to anybody — that is the whole of the mode. They still go to
  // Telegram, because a post that went out with nobody told is a post nobody can catch, but
  // without the buttons and marked `approved`, which is what the tick publishes from.
  const auto = draft.approval === "auto";

  if (format === "wa_story") {
    const waHtml = fill(tpl.wa, {
      STORY_KICKER: esc(c.story_kicker),
      STORY_NUMBER: esc(c.story_number),
      STORY_UNIT: esc(c.story_unit),
      STORY_CAPTION: esc(c.story_caption),
      CTA: esc(c.cta),
    });
    const waPng = await renderCard(page, waHtml, "#wa-card", 380, 660, 3);
    const waUrl = await upload(`${draft.id}-wa-story.png`, waPng);
    await sendPhoto(CHAT_ID, waPng, `#${draft.id} · 📱 WhatsApp story — manually post karo\n(full-res: ${waUrl})`);
    await patchDraft(draft.id, { status: "delivered", post_image_url: waUrl });
    return `#${draft.id} WA story delivered on Telegram.`;
  }

  if (format === "carousel") {
    const total = c.slides.length;
    const slidePngs = [];
    const slideUrls = [];
    for (let i = 0; i < total; i++) {
      const slide = c.slides[i];
      const html = fill(tpl.carousel, slideTokens(slide, i, total, c.cta));
      // 400×500 @2.7 = 1080×1350 (design size from Social Templates)
      const png = await renderCard(page, html, SLIDE_SELECTOR[slide.type], 420, 520, 2.7);
      slidePngs.push(png);
      slideUrls.push(await upload(`${draft.id}-slide-${i + 1}.png`, png));
    }

    let storyUrl = null;
    let storyPng = null;
    if (draft.include_story) {
      storyPng = await renderStory(page, tpl.story, c);
      storyUrl = await upload(`${draft.id}-story.png`, storyPng);
    }

    // An album can't carry inline buttons, so the approval buttons ride a
    // follow-up message; the webhook edits that message with the result.
    const album = await sendAlbum(
      CHAT_ID,
      slidePngs,
      `#${draft.id} · carousel (${total} slides)${auto ? " · apne aap ja raha hai" : ""}\n\n${c.caption}\n\n${c.hashtags}`,
    );
    if (storyPng) {
      await sendPhoto(CHAT_ID, storyPng, `#${draft.id} · IG story${auto ? "" : " (approve pe saath publish hogi)"}`);
    }
    let messageId = album[0].message_id;
    if (!auto) {
      const buttons = await tg("sendMessage", {
        chat_id: CHAT_ID,
        text: `#${draft.id} · carousel — publish karein?${fbNote}`,
        reply_markup: approvalKeyboard(draft.id),
      });
      messageId = buttons.message_id;
    }
    let fbPending = false;
    if (draft.crosspost_fb) {
      fbPending = await sendFbCopy(fb, draft.id, slidePngs, `${c.caption}\n\n${c.hashtags}`);
    }

    await patchDraft(draft.id, {
      status: auto ? "approved" : "rendered",
      carousel_urls: slideUrls,
      post_image_url: slideUrls[0],
      story_image_url: storyUrl,
      telegram_message_id: messageId,
      ...(fbPending ? { fb_status: "pending" } : {}),
    });
    return `#${draft.id} carousel rendered (${total} slides) → ${auto ? "queued to publish automatically" : "Telegram preview sent"}.`;
  }

  // format === "single"
  const shot = screenshotDataUri(draft.content_bank?.screenshot);
  const useShotVariant = draft.variant === "screenshot" && shot;
  const postHtml = fill(tpl.post, {
    HEADLINE: esc(c.headline),
    FOOTER: esc(c.footer),
    CTA: esc(c.cta),
    STATS_CHIPS: statsChips(c.stats),
    SCREENSHOT_SRC: shot ?? "",
  });
  // 400×500 @2.7 = 1080×1350 · 360×640 @3 = 1080×1920 (design sizes from Social Templates)
  const postPng = await renderCard(page, postHtml, useShotVariant ? "#shot-card" : "#stat-card", 420, 520, 2.7);

  let storyUrl = null;
  let storyPng = null;
  if (draft.include_story) {
    storyPng = await renderStory(page, tpl.story, c);
    storyUrl = await upload(`${draft.id}-story.png`, storyPng);
  }
  const postUrl = await upload(`${draft.id}-post.png`, postPng);

  const preview = await sendPhoto(
    CHAT_ID,
    postPng,
    auto
      ? `#${draft.id} · going out automatically${fbNote}\n\n${c.caption}\n\n${c.hashtags}`
      : `#${draft.id} · ${useShotVariant ? "screenshot" : "stat"} card${fbNote}\n\n${c.caption}\n\n${c.hashtags}`,
    auto ? null : approvalKeyboard(draft.id),
  );
  if (storyPng) {
    await sendPhoto(CHAT_ID, storyPng, `#${draft.id} · IG story`);
  }
  let fbPending = false;
  if (draft.crosspost_fb) {
    fbPending = await sendFbCopy(fb, draft.id, [postPng], `${c.caption}\n\n${c.hashtags}`);
  }

  await patchDraft(draft.id, {
    status: auto ? "approved" : "rendered",
    post_image_url: postUrl,
    story_image_url: storyUrl,
    telegram_message_id: preview.message_id,
    ...(fbPending ? { fb_status: "pending" } : {}),
  });
  return `#${draft.id} rendered → ${auto ? "queued to publish automatically" : "Telegram preview sent"}.`;
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

  const tpl = {
    post: await loadTemplate("ig-post.html"),
    story: await loadTemplate("ig-story.html"),
    carousel: await loadTemplate("ig-carousel.html"),
    wa: await loadTemplate("wa-story.html"),
  };

  const browser = await puppeteer.launch({ args: ["--no-sandbox", "--font-render-hinting=none"] });
  const page = await browser.newPage();
  const fb = await fbConfig();

  // One draft must not hold up the rest: a draft that throws is parked as `failed` with the
  // reason, the run carries on, and the job still ends red so the failure is not missed.
  const failed = [];
  for (const draft of drafts) {
    try {
      console.log(await renderDraft(page, tpl, fb, draft));
    } catch (e) {
      failed.push(draft.id);
      console.error(`#${draft.id} failed: ${e.message}`);
      await patchDraft(draft.id, { status: "failed", error: String(e.message).slice(0, 500) })
        .catch((p) => console.error(`#${draft.id} could not be parked: ${p.message}`));
    }
  }

  await browser.close();
  if (failed.length > 0) throw new Error(`drafts failed: ${failed.join(", ")}`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});

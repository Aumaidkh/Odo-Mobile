// Telegram approval webhook. The renderer sends the owner a preview with
// [Approve] / [Reject] inline buttons; Telegram calls this function on tap.
// Approve → publish feed post/carousel (+ story) via IG Graph API → log.
// Reject → mark. If the item is flagged crosspost_fb, a ready-to-upload
// Facebook message (caption + image links) follows a successful publish —
// the owner posts it to the FB page manually.
//
// Register once (after deploy):
//   curl "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/setWebhook" \
//     -d "url=https://YOUR_PROJECT_REF.supabase.co/functions/v1/telegram-webhook" \
//     -d "secret_token=$TELEGRAM_WEBHOOK_SECRET"
// Deploy with --no-verify-jwt (Telegram can't send a Supabase JWT).

import { createClient } from "jsr:@supabase/supabase-js@2";

const BOT_TOKEN = Deno.env.get("TELEGRAM_BOT_TOKEN") ?? "YOUR_TELEGRAM_BOT_TOKEN_HERE";
const WEBHOOK_SECRET = Deno.env.get("TELEGRAM_WEBHOOK_SECRET") ?? "YOUR_RANDOM_WEBHOOK_SECRET_HERE";
const IG_USER_ID = Deno.env.get("IG_USER_ID") ?? "YOUR_INSTAGRAM_BUSINESS_USER_ID_HERE";
const TG = `https://api.telegram.org/bot${BOT_TOKEN}`;
const IG = "https://graph.instagram.com/v23.0";

// The pipeline's own schema.
const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  { db: { schema: "social" } },
);

// The same project addressed at `public`, where the panel's tables live. A second client
// rather than schema-qualified queries: supabase-js pins one schema per client.
const config = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

async function igToken(): Promise<string> {
  const { data } = await supabase.from("app_config").select("value").eq("key", "ig_access_token").single();
  return data?.value ?? Deno.env.get("IG_ACCESS_TOKEN") ?? "YOUR_LONG_LIVED_IG_TOKEN_HERE";
}

async function createContainer(token: string, fields: Record<string, string>): Promise<string> {
  const params = new URLSearchParams({ ...fields, access_token: token });
  let json: Record<string, unknown> = {};
  for (let attempt = 0; attempt < 3; attempt++) {
    const res = await fetch(`${IG}/${IG_USER_ID}/media`, { method: "POST", body: params });
    json = await res.json();
    if (res.ok) return json.id as string;
    // 9004/2207052 = IG couldn't fetch the image URL. Flaky in practice even
    // when the URL serves fine (it claims is_transient:false) — retry.
    const err = (json as { error?: { code?: number; error_subcode?: number } }).error;
    const fetchFlake = err?.code === 9004 || err?.error_subcode === 2207052;
    if (!fetchFlake) break;
    await new Promise((r) => setTimeout(r, 3000));
  }
  throw new Error(`container: ${JSON.stringify(json)}`);
}

// Publishing straight away races container processing (error 9007 / subcode
// 2207027 "media not available"). Wait for FINISHED before moving on.
async function waitFinished(token: string, containerId: string) {
  for (let i = 0; i < 15; i++) {
    const res = await fetch(`${IG}/${containerId}?fields=status_code&access_token=${token}`);
    const status = await res.json();
    if (status.status_code === "FINISHED") return;
    if (status.status_code === "ERROR" || status.status_code === "EXPIRED") {
      throw new Error(`container status: ${JSON.stringify(status)}`);
    }
    await new Promise((r) => setTimeout(r, 2000));
  }
}

async function publishContainer(token: string, containerId: string): Promise<string> {
  let published: Record<string, unknown> = {};
  for (let attempt = 0; attempt < 4; attempt++) {
    const res = await fetch(`${IG}/${IG_USER_ID}/media_publish`, {
      method: "POST",
      body: new URLSearchParams({ creation_id: containerId, access_token: token }),
    });
    published = await res.json();
    if (res.ok) return published.id as string;
    const err = (published as { error?: { code?: number; error_subcode?: number } }).error;
    const notReady = err?.code === 9007 || err?.error_subcode === 2207027;
    if (!notReady) break;
    await new Promise((r) => setTimeout(r, 3000));
  }
  throw new Error(`publish: ${JSON.stringify(published)}`);
}

async function igPublishSingle(imageUrl: string, caption: string | null, story: boolean): Promise<string> {
  const token = await igToken();
  const fields: Record<string, string> = { image_url: imageUrl };
  if (story) fields.media_type = "STORIES";
  if (caption) fields.caption = caption;
  const id = await createContainer(token, fields);
  await waitFinished(token, id);
  return await publishContainer(token, id);
}

// ── Facebook Page publishing ──
// The IG-Login token can't touch a FB Page; a separate Page access token
// (from a long-lived user token of a Page admin) lives in app_config.
const FB = "https://graph.facebook.com/v23.0";

async function fbCreds(): Promise<{ pageId: string; token: string }> {
  const { data } = await supabase.from("app_config").select("key, value").in("key", ["fb_page_id", "fb_page_token"]);
  const map = Object.fromEntries((data ?? []).map((r: { key: string; value: string }) => [r.key, r.value]));
  return {
    pageId: map.fb_page_id ?? Deno.env.get("FB_PAGE_ID") ?? "",
    token: map.fb_page_token ?? Deno.env.get("FB_PAGE_TOKEN") ?? "",
  };
}

async function fbPublish(imageUrls: string[], message: string): Promise<string> {
  const { pageId, token } = await fbCreds();
  if (!pageId || !token) throw new Error("fb_page_id / fb_page_token missing in social.app_config");

  // Single image: one photo post carrying the caption.
  if (imageUrls.length === 1) {
    const res = await fetch(`${FB}/${pageId}/photos`, {
      method: "POST",
      body: new URLSearchParams({ url: imageUrls[0], caption: message, access_token: token }),
    });
    const json = await res.json();
    if (!res.ok) throw new Error(`fb photo: ${JSON.stringify(json)}`);
    return (json.post_id ?? json.id) as string;
  }

  // Multi image: upload each unpublished, then one feed post attaching them all.
  const ids: string[] = [];
  for (const url of imageUrls) {
    const res = await fetch(`${FB}/${pageId}/photos`, {
      method: "POST",
      body: new URLSearchParams({ url, published: "false", access_token: token }),
    });
    const json = await res.json();
    if (!res.ok) throw new Error(`fb unpublished photo: ${JSON.stringify(json)}`);
    ids.push(json.id as string);
  }
  const params = new URLSearchParams({ message, access_token: token });
  ids.forEach((id, i) => params.set(`attached_media[${i}]`, JSON.stringify({ media_fbid: id })));
  const res = await fetch(`${FB}/${pageId}/feed`, { method: "POST", body: params });
  const json = await res.json();
  if (!res.ok) throw new Error(`fb feed: ${JSON.stringify(json)}`);
  return json.id as string;
}

async function igPublishCarousel(imageUrls: string[], caption: string): Promise<string> {
  const token = await igToken();
  const children: string[] = [];
  for (const url of imageUrls) {
    children.push(await createContainer(token, { image_url: url, is_carousel_item: "true" }));
  }
  for (const child of children) await waitFinished(token, child);
  const parent = await createContainer(token, {
    media_type: "CAROUSEL",
    children: children.join(","),
    caption,
  });
  await waitFinished(token, parent);
  return await publishContainer(token, parent);
}

async function tg(method: string, body: Record<string, unknown>): Promise<{ ok: boolean }> {
  const res = await fetch(`${TG}/${method}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  return await res.json();
}

// Singles' approval message is a photo (caption edit); carousels' is a plain
// text message under the album (text edit). Editing without reply_markup drops
// the inline keyboard — pass keepButtons on failures so the owner can retry.
async function editResult(
  item: { id: number; format?: string },
  chatId: number,
  messageId: number,
  text: string,
  keepButtons = false,
  prefix = "", // "" = IG actions, "fb_" = FB Page actions
) {
  const markup = keepButtons
    ? {
      reply_markup: {
        inline_keyboard: [[
          { text: "🔁 Retry", callback_data: `${prefix}approve:${item.id}` },
          { text: "❌ Reject", callback_data: `${prefix}reject:${item.id}` },
        ]],
      },
    }
    : {};
  if ((item.format ?? "single") === "carousel") {
    await tg("editMessageText", { chat_id: chatId, message_id: messageId, text, ...markup });
  } else {
    await tg("editMessageCaption", { chat_id: chatId, message_id: messageId, caption: text, ...markup });
  }
}

/**
 * Whether this chat may act on a post.
 *
 * The webhook secret proves the request came from Telegram. It says nothing about who pressed
 * the button — and before this, nothing did: anyone who found the bot could publish to the
 * company's Instagram.
 *
 * While `social_telegram_recipients` is empty the env's own chat id is the answer. That is
 * deliberate: an empty table must not lock the owner out of a pipeline that is already
 * running, and the first row added takes over.
 */
async function mayApprove(chatId: number): Promise<boolean> {
  const fallback = Deno.env.get("TELEGRAM_CHAT_ID");
  const isFallback = !!fallback && String(chatId) === fallback;

  const { data, error } = await config
    .from("social_telegram_recipients")
    .select("chat_id, can_approve");

  // A missing table is the case the fallback exists for — this function deployed ahead of its
  // migration, which is the ordering a staged rollout produces. Failing closed here would
  // lock the owner out at exactly the moment the safety net was supposed to hold.
  if (error) return isFallback;
  if (!data || data.length === 0) return isFallback;

  return data.some((r) => Number(r.chat_id) === chatId && r.can_approve);
}

/** Whether the whole pipeline is stopped. Checked before anything is published. */
async function isPaused(): Promise<boolean> {
  const { data } = await config.from("social_settings").select("paused").limit(1).maybeSingle();
  return data?.paused === true;
}

/**
 * Publish a post sitting at `approved`, and tell the recipients it went out.
 *
 * The same Instagram calls the button path makes. What differs is that nobody is watching a
 * message, so the result is sent as a new one rather than edited into an old one.
 */
async function publishApproved(queueId: number): Promise<Response> {
  if (await isPaused()) {
    return Response.json({ ok: true, skipped: "paused" });
  }

  const { data: item } = await supabase.from("content_queue").select("*").eq("id", queueId).single();
  if (!item || item.status !== "approved") {
    return Response.json({ ok: true, skipped: "not approved" });
  }

  try {
    const caption = `${item.copy.caption}\n\n${item.copy.hashtags}`;
    const mediaId = item.format === "carousel"
      ? await igPublishCarousel(item.carousel_urls, caption)
      : await igPublishSingle(item.post_image_url, caption, false);
    const storyId = item.story_image_url ? await igPublishSingle(item.story_image_url, null, true) : null;

    await supabase.from("content_queue")
      .update({ status: "published", updated_at: new Date().toISOString() })
      .eq("id", queueId);
    await supabase.from("post_log").insert({ queue_id: queueId, ig_media_id: mediaId, ig_story_id: storyId });
    await announce(`✅ Published automatically (#${queueId})\nfeed: ${mediaId}${storyId ? `\nstory: ${storyId}` : ""}`);
    return Response.json({ ok: true, published: mediaId });
  } catch (e) {
    await supabase.from("content_queue")
      .update({ status: "failed", error: String(e), updated_at: new Date().toISOString() })
      .eq("id", queueId);
    await announce(`⚠️ Automatic publish FAILED (#${queueId})\n${String(e).slice(0, 300)}`);
    return Response.json({ ok: false, error: String(e) }, { status: 500 });
  }
}

/**
 * Tell every recipient who asked to hear about it.
 *
 * A post that went out with nobody told is a post nobody can catch, which is the one thing
 * auto mode must not become.
 */
async function announce(text: string): Promise<void> {
  const { data } = await config.from("social_telegram_recipients").select("chat_id").eq("notify", true);
  const chats = (data ?? []).map((r) => String(r.chat_id));
  const fallback = Deno.env.get("TELEGRAM_CHAT_ID");
  const targets = chats.length > 0 ? chats : (fallback ? [fallback] : []);
  for (const chat of targets) {
    await tg("sendMessage", { chat_id: chat, text });
  }
}

Deno.serve(async (req) => {
  if (req.headers.get("x-telegram-bot-api-secret-token") !== WEBHOOK_SECRET) {
    return new Response("forbidden", { status: 403 });
  }

  const update = await req.json();

  // ── Publish one already-approved post ──
  //
  // Not a Telegram update at all: `tick` asks for this when it finds a row the panel's Approve
  // button or an auto slot left at `approved`. It comes here rather than being copied into the
  // tick because this file holds the Instagram calls and the token refresh, and two publishers
  // would drift.
  //
  // The webhook secret above is the whole of the authorisation, which is the same guarantee
  // the button path has: nothing outside this project knows it.
  if (typeof update?.publish === "number") {
    return await publishApproved(Number(update.publish));
  }

  const cb = update.callback_query;
  if (!cb?.data) return Response.json({ ok: true }); // ignore non-button updates

  const [action, idRaw] = String(cb.data).split(":");
  const queueId = Number(idRaw);
  const chatId = cb.message.chat.id;
  const messageId = cb.message.message_id;

  // Checked before anything is read or written, and before the growth-plan branch below:
  // ticking somebody else's checklist is a smaller thing than publishing, and neither is
  // this chat's to do. Answered out loud rather than ignored, so a person who should have
  // access learns that they do not have it yet.
  if (!(await mayApprove(chatId))) {
    await tg("answerCallbackQuery", {
      callback_query_id: cb.id,
      text: "Aap is pipeline pe action nahi le sakte. Admin panel se access add karwao.",
      show_alert: true,
    });
    return Response.json({ ok: true, skipped: "not permitted" });
  }

  // ── Growth-plan task ticks (gp:<n>) ──
  // The 30-day-plan notifier (social-automation/growth-plan) sends task lists
  // whose tickable lines start with ☐/✅, plus one numbered button per line.
  // State lives in the message itself: toggle the n-th checkbox line, rebuild
  // the text and the keyboard from it. No queue item, no DB.
  if (action === "gp") {
    const idx = Number(idRaw);
    const lines: string[] = String(cb.message.text ?? "").split("\n");
    let seen = 0;
    let nowTicked: boolean | null = null;
    for (let i = 0; i < lines.length; i++) {
      if (!lines[i].startsWith("☐ ") && !lines[i].startsWith("✅ ")) continue;
      seen++;
      if (seen === idx) {
        nowTicked = lines[i].startsWith("☐ ");
        lines[i] = (nowTicked ? "✅ " : "☐ ") + lines[i].slice(2);
        break;
      }
    }
    if (nowTicked === null) {
      await tg("answerCallbackQuery", { callback_query_id: cb.id, text: "Ye item nahi mila." });
      return Response.json({ ok: true });
    }
    const states = lines
      .filter((l) => l.startsWith("☐ ") || l.startsWith("✅ "))
      .map((l) => l.startsWith("✅ "));
    const buttons = states.map((done, i) => ({
      text: `${done ? "✅" : "☐"} ${i + 1}`,
      callback_data: `gp:${i + 1}`,
    }));
    const rows: typeof buttons[] = [];
    for (let i = 0; i < buttons.length; i += 5) rows.push(buttons.slice(i, i + 5));
    await tg("editMessageText", {
      chat_id: chatId,
      message_id: messageId,
      text: lines.join("\n"),
      reply_markup: { inline_keyboard: rows },
    });
    await tg("answerCallbackQuery", { callback_query_id: cb.id, text: nowTicked ? "Ticked ✔" : "Un-ticked" });
    return Response.json({ ok: true });
  }

  const { data: item } = await supabase.from("content_queue").select("*").eq("id", queueId).single();
  if (!item) {
    await tg("answerCallbackQuery", { callback_query_id: cb.id, text: "Ye item ab actionable nahi hai." });
    return Response.json({ ok: true });
  }

  // FB Page actions run on their own lifecycle (fb_status), independent of the
  // IG approval — the IG post may or may not be published yet.
  if (action === "fb_approve" || action === "fb_reject") {
    if (item.fb_status !== "pending") {
      await tg("answerCallbackQuery", { callback_query_id: cb.id, text: "Ye FB item ab actionable nahi hai." });
      return Response.json({ ok: true });
    }
    if (action === "fb_reject") {
      await supabase.from("content_queue").update({ fb_status: "rejected", updated_at: new Date().toISOString() }).eq("id", queueId);
      await tg("answerCallbackQuery", { callback_query_id: cb.id, text: "FB rejected." });
      await editResult(item, chatId, messageId, `❌ FB rejected (#${queueId})`);
      return Response.json({ ok: true });
    }
    await tg("answerCallbackQuery", { callback_query_id: cb.id, text: "FB Page pe publishing…" });
    try {
      const caption = `${item.copy.caption}\n\n${item.copy.hashtags}`;
      const urls: string[] = item.format === "carousel" ? item.carousel_urls : [item.post_image_url];
      const fbPostId = await fbPublish(urls, caption);
      await supabase.from("content_queue").update({ fb_status: "published", fb_post_id: fbPostId, updated_at: new Date().toISOString() }).eq("id", queueId);
      await editResult(item, chatId, messageId, `✅ FB Page pe published (#${queueId})\npost: ${fbPostId}`);
    } catch (e) {
      await supabase.from("content_queue").update({ error: String(e), updated_at: new Date().toISOString() }).eq("id", queueId);
      await editResult(item, chatId, messageId, `⚠️ FB publish FAILED (#${queueId})\n${String(e).slice(0, 300)}`, true, "fb_");
    }
    return Response.json({ ok: true });
  }

  if ((action === "approve" || action === "fb_approve") && await isPaused()) {
    await tg("answerCallbackQuery", {
      callback_query_id: cb.id,
      text: "Pipeline paused hai. Admin panel se resume karo.",
      show_alert: true,
    });
    return Response.json({ ok: true, skipped: "paused" });
  }

  if (item.status !== "rendered") {
    await tg("answerCallbackQuery", { callback_query_id: cb.id, text: "Ye item ab actionable nahi hai." });
    return Response.json({ ok: true });
  }

  if (action === "reject") {
    await supabase.from("content_queue").update({ status: "rejected", updated_at: new Date().toISOString() }).eq("id", queueId);
    await tg("answerCallbackQuery", { callback_query_id: cb.id, text: "Rejected." });
    await editResult(item, chatId, messageId, `❌ Rejected (#${queueId})`);
    return Response.json({ ok: true });
  }

  if (action === "approve") {
    await tg("answerCallbackQuery", { callback_query_id: cb.id, text: "Publishing…" });
    try {
      const caption = `${item.copy.caption}\n\n${item.copy.hashtags}`;
      const mediaId = item.format === "carousel"
        ? await igPublishCarousel(item.carousel_urls, caption)
        : await igPublishSingle(item.post_image_url, caption, false);
      let storyId: string | null = null;
      if (item.story_image_url) {
        storyId = await igPublishSingle(item.story_image_url, null, true);
      }
      await supabase.from("content_queue").update({ status: "published", updated_at: new Date().toISOString() }).eq("id", queueId);
      await supabase.from("post_log").insert({ queue_id: queueId, ig_media_id: mediaId, ig_story_id: storyId });
      await editResult(item, chatId, messageId,
        `✅ Published (#${queueId})\nfeed: ${mediaId}${storyId ? `\nstory: ${storyId}` : ""}`);

      // FB copies are delivered by the renderer at render time, straight to the
      // FB uploader's chat (owner's call: no approval gate on FB) — nothing to
      // do here.
    } catch (e) {
      await supabase.from("content_queue").update({ error: String(e), updated_at: new Date().toISOString() }).eq("id", queueId);
      await editResult(item, chatId, messageId, `⚠️ Publish FAILED (#${queueId})\n${String(e).slice(0, 300)}`, true);
    }
  }

  return Response.json({ ok: true });
});

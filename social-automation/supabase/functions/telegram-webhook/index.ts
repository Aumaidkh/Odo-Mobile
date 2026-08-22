// Telegram approval webhook. The renderer sends the owner a preview with
// [Approve] / [Reject] inline buttons; Telegram calls this function on tap.
// Approve → publish feed post + story via IG Graph API → log. Reject → mark.
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

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  { db: { schema: "social" } },
);

async function igToken(): Promise<string> {
  const { data } = await supabase.from("app_config").select("value").eq("key", "ig_access_token").single();
  return data?.value ?? Deno.env.get("IG_ACCESS_TOKEN") ?? "YOUR_LONG_LIVED_IG_TOKEN_HERE";
}

async function igPublish(imageUrl: string, caption: string | null, story: boolean): Promise<string> {
  const token = await igToken();
  const params = new URLSearchParams({ image_url: imageUrl, access_token: token });
  if (story) params.set("media_type", "STORIES");
  if (caption) params.set("caption", caption);

  const containerRes = await fetch(`${IG}/${IG_USER_ID}/media`, { method: "POST", body: params });
  const container = await containerRes.json();
  if (!containerRes.ok) throw new Error(`container: ${JSON.stringify(container)}`);

  // Publishing straight away races container processing (error 9007 / subcode
  // 2207027 "media not available"). Wait for FINISHED, then publish with retries.
  for (let i = 0; i < 15; i++) {
    const statusRes = await fetch(`${IG}/${container.id}?fields=status_code&access_token=${token}`);
    const status = await statusRes.json();
    if (status.status_code === "FINISHED") break;
    if (status.status_code === "ERROR" || status.status_code === "EXPIRED") {
      throw new Error(`container status: ${JSON.stringify(status)}`);
    }
    await new Promise((r) => setTimeout(r, 2000));
  }

  let published: Record<string, unknown> = {};
  for (let attempt = 0; attempt < 4; attempt++) {
    const publishRes = await fetch(`${IG}/${IG_USER_ID}/media_publish`, {
      method: "POST",
      body: new URLSearchParams({ creation_id: container.id, access_token: token }),
    });
    published = await publishRes.json();
    if (publishRes.ok) return published.id as string;
    const err = (published as { error?: { code?: number; error_subcode?: number } }).error;
    const notReady = err?.code === 9007 || err?.error_subcode === 2207027;
    if (!notReady) break;
    await new Promise((r) => setTimeout(r, 3000));
  }
  throw new Error(`publish: ${JSON.stringify(published)}`);
}

async function tg(method: string, body: Record<string, unknown>) {
  await fetch(`${TG}/${method}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

Deno.serve(async (req) => {
  if (req.headers.get("x-telegram-bot-api-secret-token") !== WEBHOOK_SECRET) {
    return new Response("forbidden", { status: 403 });
  }

  const update = await req.json();
  const cb = update.callback_query;
  if (!cb?.data) return Response.json({ ok: true }); // ignore non-button updates

  const [action, idRaw] = String(cb.data).split(":");
  const queueId = Number(idRaw);
  const chatId = cb.message.chat.id;
  const messageId = cb.message.message_id;

  const { data: item } = await supabase.from("content_queue").select("*").eq("id", queueId).single();
  if (!item || item.status !== "rendered") {
    await tg("answerCallbackQuery", { callback_query_id: cb.id, text: "Ye item ab actionable nahi hai." });
    return Response.json({ ok: true });
  }

  if (action === "reject") {
    await supabase.from("content_queue").update({ status: "rejected", updated_at: new Date().toISOString() }).eq("id", queueId);
    await tg("answerCallbackQuery", { callback_query_id: cb.id, text: "Rejected." });
    await tg("editMessageCaption", { chat_id: chatId, message_id: messageId, caption: `❌ Rejected (#${queueId})` });
    return Response.json({ ok: true });
  }

  if (action === "approve") {
    await tg("answerCallbackQuery", { callback_query_id: cb.id, text: "Publishing…" });
    try {
      const caption = `${item.copy.caption}\n\n${item.copy.hashtags}`;
      const mediaId = await igPublish(item.post_image_url, caption, false);
      let storyId: string | null = null;
      if (item.story_image_url) {
        storyId = await igPublish(item.story_image_url, null, true);
      }
      await supabase.from("content_queue").update({ status: "published", updated_at: new Date().toISOString() }).eq("id", queueId);
      await supabase.from("post_log").insert({ queue_id: queueId, ig_media_id: mediaId, ig_story_id: storyId });
      await tg("editMessageCaption", {
        chat_id: chatId, message_id: messageId,
        caption: `✅ Published (#${queueId})\nfeed: ${mediaId}${storyId ? `\nstory: ${storyId}` : ""}`,
      });
    } catch (e) {
      await supabase.from("content_queue").update({ error: String(e), updated_at: new Date().toISOString() }).eq("id", queueId);
      await tg("editMessageCaption", { chat_id: chatId, message_id: messageId, caption: `⚠️ Publish FAILED (#${queueId})\n${String(e).slice(0, 300)}` });
    }
  }

  return Response.json({ ok: true });
});

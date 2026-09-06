// Approve, and publish — now.
//
// The panel's Approve button used to set `approved` and stop, leaving the post for the tick to
// sweep up. That is fine when the tick is running and silent when it is not, which is what it
// was on every project: the scheduler is armed by a button somebody has to press, and until
// they do, an approved post sits there looking approved and never goes anywhere.
//
// A button called Approve should do the thing. The tick keeps its sweep for posts nobody
// pressed anything for — an `auto` slot's — which is the case it was written for.

import { createClient } from "jsr:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const admin = createClient(SUPABASE_URL, SERVICE_ROLE);
const pipeline = createClient(SUPABASE_URL, SERVICE_ROLE, { db: { schema: "social" } });


/**
 * The panel is a browser app on another origin, so every answer needs these and the preflight
 * needs its own reply. Without them the fetch throws before it is sent and the panel reports
 * "could not reach the server" — which is true, and says nothing about why.
 */
const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, content-type, apikey",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "Content-Type": "application/json" },
  });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });

  // The caller's own token: this asks the database who they are, rather than taking the
  // service role's word for it.
  const asCaller = createClient(SUPABASE_URL, Deno.env.get("SUPABASE_ANON_KEY") ?? "", {
    global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } },
  });
  const { data: permitted } = await asCaller.rpc("admin_has", { p_permission: "blog.write" });
  if (permitted !== true) {
    return json(403, { error: "not permitted" });
  }

  const body = await req.json().catch(() => ({}));
  const queueId = Number(body?.queue_id);
  if (!Number.isFinite(queueId)) {
    return json(400, { error: "queue_id is required" });
  }

  const { data: settings } = await admin.from("social_settings").select("paused").limit(1).single();
  if (settings?.paused) {
    return json(409, { error: "the pipeline is paused" });
  }

  const { data: item } = await pipeline
    .from("content_queue")
    .select("id, status, post_image_url")
    .eq("id", queueId)
    .single();
  if (!item) {
    return json(404, { error: "no such post" });
  }

  // Nothing to publish without the rendered image, and Instagram is not the place to find
  // that out. A post is only ready once the renderer has been over it, and saying so here is
  // the difference between "not rendered yet" and an opaque failure against the IG API.
  if (!item.post_image_url) {
    return json(409, { error: "this post has not been rendered yet — there is no image to publish" });
  }

  await pipeline
    .from("content_queue")
    .update({ status: "approved", updated_at: new Date().toISOString() })
    .eq("id", queueId);

  // The webhook owns the Instagram calls and the token refresh. Asked over HTTP rather than
  // copied, so there stays one publisher.
  const published = await fetch(`${SUPABASE_URL}/functions/v1/telegram-webhook`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "x-telegram-bot-api-secret-token": Deno.env.get("TELEGRAM_WEBHOOK_SECRET") ?? "",
    },
    body: JSON.stringify({ publish: queueId }),
  });

  const result = await published.json().catch(() => ({}));
  if (!published.ok) {
    return json(502, { error: result?.error ?? "publish failed" });
  }
  return json(200, { ok: true, ...result });
});

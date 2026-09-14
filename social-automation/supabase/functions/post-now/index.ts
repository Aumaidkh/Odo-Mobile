// "Make one now" — the button on the panel's Queue tab.
//
// One post, made and shipped without waiting for a schedule. It exists to prove the pipeline
// end to end: a person can press it, watch the queue, and see something reach Instagram.
//
// **It is not a fourth mode.** It ignores the schedule and it ignores `posting_mode`, because
// a person pressing a button has already answered the question those settle. It does not
// ignore the pause: that switch means stop, and a test that walked past it would be the one
// thing running while somebody was trying to make everything stop.
//
// Authorisation is the caller's own admin session, checked against the same permission the
// section is behind. This is the only function here a browser calls directly.

import { createClient } from "jsr:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

/** Where the renderer lives, so this can ask it to run now rather than on its cron. */
const GITHUB_TOKEN = Deno.env.get("BLOG_DISPATCH_TOKEN") ?? "";
const GITHUB_REPO = Deno.env.get("BLOG_DISPATCH_REPO") ?? "AumaidKh/Odo-Mobile";

const admin = createClient(SUPABASE_URL, SERVICE_ROLE);


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

  // The caller's own token, not the service role: this asks the database who they are, and
  // `admin_has` answers against their session. A function that took the service role's word
  // for it would be a function anybody with the anon key could press.
  const authorization = req.headers.get("Authorization") ?? "";
  const asCaller = createClient(SUPABASE_URL, Deno.env.get("SUPABASE_ANON_KEY") ?? "", {
    global: { headers: { Authorization: authorization } },
  });

  const { data: permitted } = await asCaller.rpc("admin_has", { p_permission: "blog.write" });
  if (permitted !== true) {
    return json(403, { error: "not permitted" });
  }

  const { data: settings } = await admin
    .from("social_settings")
    .select("paused")
    .limit(1)
    .single();
  if (settings?.paused) {
    return json(409, { error: "the pipeline is paused" });
  }

  // Auto, because the person pressing this is the approval. The renderer sees that on the row
  // and sends the preview without buttons, then leaves it `approved` for the publisher.
  const generated = await fetch(`${SUPABASE_URL}/functions/v1/generate`, {
    method: "POST",
    headers: { "Authorization": `Bearer ${SERVICE_ROLE}`, "Content-Type": "application/json" },
    body: JSON.stringify({ story: false, approval: "auto" }),
  });
  const body = await generated.json();
  if (!generated.ok) {
    return json(502, { error: body?.error ?? "generate failed" });
  }

  // Ask the renderer to run now. Its own cron is hours away, and a test that took hours to
  // answer is a test nobody runs twice.
  //
  // Reported rather than raised: the post is queued either way, and "made it, could not hurry
  // the renderer" is a different thing to be told than "made nothing".
  let rendering = "not requested";
  if (GITHUB_TOKEN) {
    const dispatch = await fetch(`https://api.github.com/repos/${GITHUB_REPO}/dispatches`, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${GITHUB_TOKEN}`,
        "Accept": "application/vnd.github+json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ event_type: "social-render" }),
    });
    rendering = dispatch.ok ? "requested" : `dispatch ${dispatch.status}`;
  }

  return json(200, { ok: true, queue_id: body?.queue_id, rendering });
});

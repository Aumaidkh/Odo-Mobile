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

Deno.serve(async (req) => {
  // The caller's own token, not the service role: this asks the database who they are, and
  // `admin_has` answers against their session. A function that took the service role's word
  // for it would be a function anybody with the anon key could press.
  const authorization = req.headers.get("Authorization") ?? "";
  const asCaller = createClient(SUPABASE_URL, Deno.env.get("SUPABASE_ANON_KEY") ?? "", {
    global: { headers: { Authorization: authorization } },
  });

  const { data: permitted } = await asCaller.rpc("admin_has", { p_permission: "blog.write" });
  if (permitted !== true) {
    return Response.json({ error: "not permitted" }, { status: 403 });
  }

  const { data: settings } = await admin
    .from("social_settings")
    .select("paused")
    .limit(1)
    .single();
  if (settings?.paused) {
    return Response.json({ error: "the pipeline is paused" }, { status: 409 });
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
    return Response.json({ error: body?.error ?? "generate failed" }, { status: 502 });
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

  return Response.json({ ok: true, queue_id: body?.queue_id, rendering });
});

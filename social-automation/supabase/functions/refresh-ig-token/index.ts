// IG long-lived tokens expire in ~60 days. pg_cron calls this every ~50 days;
// it exchanges the current token for a fresh one and stores it in
// social.app_config (env vars can't be rewritten from a function).

import { createClient } from "jsr:@supabase/supabase-js@2";

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  { db: { schema: "social" } },
);

Deno.serve(async (_req) => {
  const { data } = await supabase.from("app_config").select("value").eq("key", "ig_access_token").single();
  const current = data?.value ?? Deno.env.get("IG_ACCESS_TOKEN") ?? "YOUR_LONG_LIVED_IG_TOKEN_HERE";

  const res = await fetch(
    `https://graph.instagram.com/refresh_access_token?grant_type=ig_refresh_token&access_token=${current}`,
  );
  const body = await res.json();
  if (!res.ok) return Response.json({ error: body }, { status: 502 });

  await supabase.from("app_config").upsert({
    key: "ig_access_token",
    value: body.access_token,
    updated_at: new Date().toISOString(),
  });

  return Response.json({ ok: true, expires_in_days: Math.round(body.expires_in / 86400) });
});

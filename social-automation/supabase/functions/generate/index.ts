// Daily content generation. Picks the least-recently-used fact from
// social.content_bank, asks Gemini to write copy around it (numbers come from
// the fact, never from the model), inserts a draft into social.content_queue.
// Trigger: pg_cron daily (see schema.sql) or manual POST.

import { createClient } from "jsr:@supabase/supabase-js@2";

const GEMINI_MODEL = "gemini-2.5-flash";

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  { db: { schema: "social" } },
);

// The same project, addressed at `public`, where the admin panel's settings live. A second
// client rather than schema-qualified queries: supabase-js pins one schema per client.
const config = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

/**
 * A secret set from the panel, falling back to this function's env.
 *
 * The panel writes into `social_credentials`, which nothing but the service role can read.
 * The env fallback is what keeps a pipeline that predates the panel running: it stops
 * applying the moment somebody sets the key on the screen.
 */
async function credential(key: string, envName: string): Promise<string> {
  const { data } = await config
    .from("social_credentials")
    .select("value")
    .eq("key", key)
    .maybeSingle();
  return data?.value ?? Deno.env.get(envName) ?? "";
}

const PROMPT = (fact: string, stats: string, cta: string) => `
Tum Odo (car service record + reminders + auto odometer Android app, India) ka
social copywriter ho. Neeche ek VERIFIED fact aur uske stats hain. Inke around
ek Instagram post ke liye copy likho.

Rules — non-negotiable:
- Sirf diye gaye numbers use karo. Koi naya number, stat ya claim INVENT mat karo.
- Language: Hinglish, bol-chaal wali, seedhi baat. Koi corporate tone nahi.
- Headline max 8 words, hook pehle 3 words me.
- Caption 2-3 line + ek sawal jo comment invite kare. Hashtags 8-12, mix of
  #carsofindia #carcareindia type + topic-specific.

FACT: ${fact}
STATS (card pe yehi dikhenge): ${stats}
CTA: ${cta}

Return ONLY JSON with exactly these keys:
{
  "headline": "...",            // post card ki badi line
  "footer": "...",              // post card ki neeche wali ek line
  "story_kicker": "...",        // story card top label, UPPERCASE, max 4 words
  "story_number": "...",        // story ka hero figure, stats me se (ya chhota phrase agar stat nahi)
  "story_unit": "...",          // hero figure ke neeche ki line, max 4 words
  "story_caption": "...",       // story ki supporting line, max 15 words
  "caption": "...",             // IG caption, bina hashtags ke
  "hashtags": "#... #..."
}`;

Deno.serve(async (req) => {
  // {"story": false} = post-only slot; default true (morning slot carries the story).
  // The tick sends the rest: which slot asked, and whether its post needs approving.
  let includeStory = true;
  let approval = "manual";
  let slotId: string | null = null;
  let platforms: string[] = [];
  try {
    const body = await req.json();
    if (typeof body?.story === "boolean") includeStory = body.story;
    if (typeof body?.approval === "string") approval = body.approval;
    if (typeof body?.slot_id === "string") slotId = body.slot_id;
    if (Array.isArray(body?.platforms)) platforms = body.platforms;
  } catch (_) { /* empty body = default */ }

  // The pause answers for the whole pipeline, and it is checked here as well as in the tick:
  // this function is also called by hand, and a pause that only the scheduler honoured would
  // be a pause somebody could walk straight past.
  const { data: settings } = await config
    .from("social_settings")
    .select("posting_mode, paused")
    .limit(1)
    .single();
  if (settings?.paused) {
    return Response.json({ ok: true, skipped: "paused" });
  }
  // Under `auto` the mode has already decided; a slot cannot ask for approval it will not get.
  if (settings?.posting_mode === "auto") approval = "auto";

  const geminiKey = await credential("gemini_api_key", "GEMINI_API_KEY");
  if (!geminiKey) {
    return Response.json({ error: "no gemini key set" }, { status: 500 });
  }
  const GEMINI_URL =
    `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent?key=${geminiKey}`;

  // 1. Least-recently-used fact.
  const { data: fact, error: pickErr } = await supabase
    .from("content_bank")
    .select("*")
    .order("last_used_at", { ascending: true, nullsFirst: true })
    .limit(1)
    .single();
  if (pickErr || !fact) {
    return Response.json({ error: `content_bank empty: ${pickErr?.message}` }, { status: 500 });
  }

  // 2. Gemini writes the copy (JSON mode).
  const geminiRes = await fetch(GEMINI_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      contents: [{ parts: [{ text: PROMPT(fact.fact, JSON.stringify(fact.stats ?? []), fact.cta) }] }],
      generationConfig: { responseMimeType: "application/json", temperature: 0.9 },
    }),
  });
  if (!geminiRes.ok) {
    return Response.json({ error: `gemini ${geminiRes.status}: ${await geminiRes.text()}` }, { status: 502 });
  }
  const gemini = await geminiRes.json();
  const copy = JSON.parse(gemini.candidates[0].content.parts[0].text);
  copy.stats = fact.stats ?? []; // card stats always come from the bank, not the model
  copy.cta = fact.cta;

  // 3. Queue as draft; renderer (GitHub Actions) picks it up.
  //
  // `approval` rides along on the row so the step that publishes does not have to re-derive
  // it from a mode that may have been changed in between. A post decides once whether it
  // needs a person, at the moment it is made.
  const { data: draft, error: insErr } = await supabase
    .from("content_queue")
    .insert({
      bank_id: fact.id,
      variant: fact.screenshot ? "screenshot" : "stat",
      include_story: includeStory,
      copy,
      approval,
      slot_id: slotId,
      platforms,
    })
    .select("id")
    .single();
  if (insErr) return Response.json({ error: insErr.message }, { status: 500 });

  await supabase.from("content_bank").update({ last_used_at: new Date().toISOString() }).eq("id", fact.id);

  return Response.json({ ok: true, queue_id: draft.id });
});

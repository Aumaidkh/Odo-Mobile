// Names bill lines the app's rule table could not.
//
// The app matches a bill line against `BillLineMatcher` first — free, offline, instant and
// inspectable. This is the fallback for what those rules miss: "throttle body cleaning",
// "injector decarb", the wording one workshop in one city uses.
//
// **The model classifies. It never prices.** The response carries a category slug and nothing
// else — no rupees, no band, no confidence dressed up as one. The number comes from
// `job_prices` and `labour_rates` through `get_fairness_benchmark`, exactly as it does for a
// line the rules named (AI_ADVISORY_PLAN §2.7). A hallucinated price band, read aloud at a
// service counter, is the one output we could be held to.
//
// Three things sit between a request and a rupee of spend:
//
//   1. **The cache.** Bill wording repeats across owners far more than it varies, so most
//      requests are answered from `bill_line_classifications` and cost nothing. A miss is
//      cached too — "this is no job we price" is an answer worth keeping.
//   2. **The meter.** Per owner per day, and across the project per day. Over either, this
//      answers from the cache alone and says so. Refusing is not an error.
//   3. **The catalogue.** Every slug the model returns is checked against `service_categories`.
//      An invented slug is dropped, not stored.
//
// Deploy:
//   supabase secrets set --project-ref <ref> GEMINI_API_KEY=...
//   supabase functions deploy advisory-classify --project-ref <ref>

import { createClient } from 'jsr:@supabase/supabase-js@2'

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
const ANON_KEY = Deno.env.get('SUPABASE_ANON_KEY')!

/** Absent on a project nobody has configured. Reported plainly rather than as an opaque 500. */
const GEMINI_API_KEY = Deno.env.get('GEMINI_API_KEY')

const GEMINI_URL =
  `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${GEMINI_API_KEY}`

/** One owner's calls per day, and the project's. Both raisable without a deploy. */
const OWNER_DAILY_CAP = Number(Deno.env.get('ADVISORY_OWNER_DAILY_CAP') ?? '20')
const DAILY_CAP = Number(Deno.env.get('ADVISORY_DAILY_CALL_CAP') ?? '500')

/** A bill has a dozen lines. Twenty is a generous ceiling and a cheap one to enforce. */
const MAX_LINES = 20

const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY)

/**
 * The same normalisation `BillLineMatcher` does, and it has to stay the same.
 *
 * The key is shared between the app's rules and this cache. If the two drift, a phrase
 * promoted into the rules keeps being asked about here under a key nothing matches.
 */
function labelKey(label: string): string {
  return label
    .toLowerCase()
    .replaceAll('a/c', 'ac')
    .replaceAll('a.c.', 'ac')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim()
}

const PROMPT = (slugs: string[], labels: string[]) => `
You are reading line items printed on Indian car service bills. For each line, say which
service category it is, using ONLY the slugs in this list.

CATEGORIES: ${slugs.join(', ')}

Rules — non-negotiable:
- Answer with a slug from the list, or null. Never invent a slug.
- Answer null whenever you are not sure. A wrong category puts a false rupee figure in front
  of an owner arguing with a mechanic; a null costs nothing but one silent line.
- Answer null for anything that is not a repair or service job: labour, consumables, tax,
  discounts, rounding, shop supplies.
- Answer null when one line covers two different jobs ("engine oil + filter"). One price
  cannot be checked against either of them.
- Indian workshop wording: "brake oil" is brake fluid, not engine oil. "A/C gas top up" is an
  AC service. "Denting painting" is bodywork.
- Never return a price, an amount, a range or an estimate. You are not being asked one.

LINES:
${labels.map((l, i) => `${i}. ${l}`).join('\n')}

Return ONLY JSON: {"lines": [{"i": 0, "slug": "ac_service"}, {"i": 1, "slug": null}]}`

Deno.serve(async (req) => {
  if (req.method !== 'POST') return fail(405, 'method_not_allowed')

  // ---- 1. Who is asking ----
  //
  // The platform verified the token (verify_jwt is on). This reads the owner out of it,
  // because the meter is per owner and an unattributed call cannot be capped.
  const authHeader = req.headers.get('Authorization') ?? ''
  const caller = createClient(SUPABASE_URL, ANON_KEY, {
    global: { headers: { Authorization: authHeader } },
  })
  const { data: userData } = await caller.auth.getUser()
  const ownerId = userData?.user?.id
  if (!ownerId) return fail(401, 'no_session')

  // ---- 2. What they are asking about ----
  let labels: string[] = []
  try {
    const body = await req.json()
    if (Array.isArray(body?.lines)) {
      labels = body.lines.filter((l: unknown) => typeof l === 'string' && l.trim().length > 0)
    }
  } catch (_) { /* an unreadable body asks about nothing */ }

  if (labels.length === 0) return Response.json({ classified: {} })
  if (labels.length > MAX_LINES) labels = labels.slice(0, MAX_LINES)

  // Keyed by the normalised form: two spellings of one line are one question.
  const byKey = new Map<string, string[]>()
  for (const label of labels) {
    const key = labelKey(label)
    if (!key) continue
    byKey.set(key, [...(byKey.get(key) ?? []), label])
  }

  // ---- 3. The cache ----
  const keys = [...byKey.keys()]
  const { data: cached } = await admin
    .from('bill_line_classifications')
    .select('label_key, category_slug')
    .in('label_key', keys)

  const answers = new Map<string, string | null>()
  for (const row of cached ?? []) answers.set(row.label_key, row.category_slug)
  if (cached?.length) {
    // What the human reviewing this queue sorts by: the wording that costs the most calls is
    // the wording worth promoting into the app's rules. Best-effort — a failed count must not
    // cost the owner their answer.
    const { error } = await admin.rpc('advisory_cache_hit', {
      p_keys: cached.map((row: { label_key: string }) => row.label_key),
    })
    if (error) console.error(`hit count failed: ${error.message}`)
  }

  const missing = keys.filter((k) => !answers.has(k))

  // ---- 4. The model, if the budget allows ----
  let capped = false
  if (missing.length > 0) {
    if (!GEMINI_API_KEY) return fail(503, 'not_configured')

    const { data: allowed } = await admin.rpc('advisory_meter_take', {
      p_owner: ownerId,
      p_lines: missing.length,
      p_owner_cap: OWNER_DAILY_CAP,
      p_daily_cap: DAILY_CAP,
    })

    if (allowed !== true) {
      // Not an error. The cached answers still go back, and the rest of the lines stay
      // unchecked — which is exactly what the screen shows for a line the rules cannot name.
      capped = true
    } else {
      const slugs = await categorySlugs()
      const named = await classify(slugs, missing)
      for (const [key, slug] of named) answers.set(key, slug)

      // Cache every answer, including the nulls. An unpriceable line asked once should not be
      // asked again by the next owner who was charged for it.
      const rows = missing.map((key) => ({
        label_key: key,
        label_sample: byKey.get(key)![0].slice(0, 200),
        category_slug: named.get(key) ?? null,
        source: 'model',
      }))
      const { error: cacheErr } = await admin
        .from('bill_line_classifications')
        .upsert(rows, { onConflict: 'label_key' })
      if (cacheErr) console.error(`cache write failed: ${cacheErr.message}`)
    }
  }

  // ---- 5. Back in the caller's own words ----
  //
  // Keyed by the label as it was sent, so the app does not have to reproduce this function's
  // normalisation to read the answer.
  const classified: Record<string, string> = {}
  for (const [key, sent] of byKey) {
    const slug = answers.get(key)
    if (slug) for (const label of sent) classified[label] = slug
  }

  return Response.json({ classified, capped })
})

/** The slugs that exist. The model may name one of these or nothing. */
async function categorySlugs(): Promise<string[]> {
  const { data, error } = await admin.from('service_categories').select('slug')
  if (error || !data?.length) throw new Error(`service_categories unreadable: ${error?.message}`)
  return data.map((row: { slug: string }) => row.slug)
}

/**
 * One Gemini call for every line the cache could not answer.
 *
 * A slug the catalogue does not carry is dropped rather than stored — the model is asked for
 * one of a fixed list, and anything else is it having ignored the instruction.
 */
async function classify(slugs: string[], keys: string[]): Promise<Map<string, string | null>> {
  const named = new Map<string, string | null>()
  for (const key of keys) named.set(key, null)

  const res = await fetch(GEMINI_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      contents: [{ parts: [{ text: PROMPT(slugs, keys) }] }],
      // Zero temperature: this is a lookup, not writing. The same bill line should come back
      // the same way every time, and a cached answer is only as good as that.
      generationConfig: { responseMimeType: 'application/json', temperature: 0 },
    }),
  })
  if (!res.ok) {
    console.error(`gemini ${res.status}: ${await res.text()}`)
    return named
  }

  try {
    const body = await res.json()
    const parsed = JSON.parse(body.candidates[0].content.parts[0].text)
    const known = new Set(slugs)
    for (const line of parsed.lines ?? []) {
      const key = keys[line.i]
      if (key === undefined) continue
      named.set(key, typeof line.slug === 'string' && known.has(line.slug) ? line.slug : null)
    }
  } catch (e) {
    // A reply nobody could parse names nothing. Every line stays null, and the owner sees the
    // same screen they would have seen without this function at all.
    console.error(`gemini reply unreadable: ${e}`)
  }
  return named
}

function fail(status: number, code: string): Response {
  return Response.json({ error_code: code }, { status })
}

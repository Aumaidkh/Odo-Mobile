// Gets a new admin into a state where they can actually sign in.
//
// **The gap this closes.** Signing in to the panel needs two separate things: a Firebase Auth
// account, which is what holds the password, and a row in `admin_users`, which is what
// `admin-session` checks before it will mint a session. The panel's "Add someone" created only
// the second. The row appeared in the staff list, the person was told to sign in, and there
// was nothing to sign in with — no password, no link, no way to set one.
//
// So this creates the Firebase account and asks Firebase to email them a link to choose their
// own password. Neither half is useful alone, which is why one call does both.
//
//   supabase secrets set --project-ref <ref> FIREBASE_WEB_API_KEY=<the web api key>
//   supabase functions deploy admin-invite --project-ref <ref>
//
// **No service account here, deliberately.** The two Identity Toolkit endpoints below are the
// public ones, authenticated with the web API key — the same key the sign-in page already
// carries, and a public identifier rather than a credential. Using the admin API instead would
// mean granting the service account Firebase Authentication Admin, which is a second role in a
// second console for no gain: the check that matters is the one at the top of this function,
// and that is ours either way.

import { createClient } from 'jsr:@supabase/supabase-js@2'

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const ANON_KEY = Deno.env.get('SUPABASE_ANON_KEY')!
const SERVICE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

/** Absent on a project nobody has configured, which is reported plainly rather than as a 500. */
const API_KEY = Deno.env.get('FIREBASE_WEB_API_KEY')

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response(null, { status: 204, headers: CORS })
  if (req.method !== 'POST') return fail(405, 'method_not_allowed')

  // ---- 1. Who is asking, and may they? ----
  const authorization = req.headers.get('Authorization')
  if (!authorization) return fail(401, 'not_signed_in')

  const caller = createClient(SUPABASE_URL, ANON_KEY, {
    global: { headers: { Authorization: authorization } },
    auth: { autoRefreshToken: false, persistSession: false },
  })

  const { data: permitted, error: permissionError } = await caller.rpc('admin_has', {
    p_permission: 'admin.roles.write',
  })

  // An error is a refusal, not a fault: `admin_has` is revoked from `anon`, so an anonymous
  // caller makes this fail rather than return false. Answering 500 would tell a stranger the
  // difference between "not allowed" and "broken".
  if (permissionError) {
    console.error('admin-invite permission check refused:', permissionError.message)
    return fail(403, 'not_permitted')
  }
  if (permitted !== true) return fail(403, 'not_permitted')

  if (!API_KEY) return fail(503, 'firebase_not_configured')

  let body: { email?: string }
  try {
    body = await req.json()
  } catch {
    return fail(400, 'malformed_body')
  }
  const email = (body.email ?? '').trim().toLowerCase()
  if (!email.includes('@')) return fail(400, 'missing_email')

  // ---- 2. Only somebody who is already on the allowlist ----
  //
  // This is what keeps the endpoint from being a way to create Firebase accounts for arbitrary
  // addresses. The row has to exist and be active first — inviting is the second step of
  // adding somebody, never a way to add them.
  const admin = createClient(SUPABASE_URL, SERVICE_KEY, {
    auth: { autoRefreshToken: false, persistSession: false },
  })
  const { data: row, error: lookupError } = await admin
    .from('admin_users')
    .select('email, is_active')
    .eq('email', email)
    .maybeSingle()

  if (lookupError) {
    console.error('admin-invite lookup failed:', lookupError.message)
    return fail(502, 'lookup_failed')
  }
  if (!row) return fail(404, 'not_on_allowlist')
  if (!row.is_active) return fail(409, 'access_revoked')

  // ---- 3. Make sure a Firebase account exists ----
  //
  // With a long random password nobody is told, because the account is only a place for the
  // reset link to land. If one already exists, EMAIL_EXISTS is the expected answer and not a
  // failure — re-inviting somebody is a thing people do.
  let created = false
  const signUp = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${API_KEY}`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password: randomPassword(), returnSecureToken: false }),
    },
  )

  if (signUp.ok) {
    created = true
  } else {
    const text = await signUp.text()
    if (text.includes('EMAIL_EXISTS')) {
      created = false
    } else if (text.includes('OPERATION_NOT_ALLOWED') || text.includes('ADMIN_ONLY_OPERATION')) {
      // Email/password sign-up is switched off for the project. Named rather than
      // reported as a generic failure, because the fix is one toggle in a console and
      // nothing about a 502 would point at it.
      console.error('admin-invite signUp refused:', text.slice(0, 300))
      return fail(409, 'signup_disabled')
    } else {
      console.error('admin-invite signUp failed:', signUp.status, text.slice(0, 300))
      return fail(502, 'account_create_failed')
    }
  }

  // ---- 4. Let Firebase send the email ----
  //
  // Firebase's own password-reset mail, rather than an invite template of ours. It means no
  // SMTP to configure and no deliverability to own, and the link it carries is the only thing
  // that can actually set a password on a Firebase account.
  const reset = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=${API_KEY}`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ requestType: 'PASSWORD_RESET', email }),
    },
  )

  if (!reset.ok) {
    const text = await reset.text()
    console.error('admin-invite sendOobCode failed:', reset.status, text.slice(0, 300))
    // The account exists by now, so this is recoverable: they can use "forgot password" on
    // the Firebase sign-in, or somebody can press invite again. Said as its own outcome so
    // the panel can say which half worked.
    return fail(502, 'email_failed')
  }

  return json(200, { created, emailed: true })
})

/**
 * A password nobody will ever use.
 *
 * Long and random because it is never told to anybody and never needs to be typed — the
 * account is reachable only through the reset link. Short or predictable would make it a
 * password, which is the one thing it must not be.
 */
function randomPassword(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(32))
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('') + 'Aa1!'
}

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, content-type, apikey',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...CORS },
  })

const fail = (status: number, code: string) => json(status, { error: code })

// Trades a Firebase ID token for a Supabase session that may use the admin panel.
//
// The third sibling of `firebase-session` and `blog-session`, and deliberately not an extension
// of either. `firebase-session` proves a phone number for an app user; `blog-session` proves an
// author's email and unlocks the CMS; this one proves a staff address and unlocks `/admin`.
// Folding them together would mean one function where a bug in the phone path can hand out the
// ability to edit other people's entitlements.
//
// **This is where the staff check lives.** A browser cannot be trusted to enforce it — anyone
// with a developer console gets past a client check. The difference from `blog-session` is
// where the list is kept: that one reads `BLOG_AUTHOR_EMAILS` out of the environment, this one
// reads the `admin_users` table. A table is what makes adding an admin a thing a super-admin
// can do in the panel, with an audit row behind it, rather than a redeploy.
//
// What this function does NOT do is decide what an admin may do once inside. The session it
// mints carries an `odo_admin` claim that means "is staff at all" and nothing more; every
// specific permission is checked live in RLS by `admin_has()` at the moment of the write. See
// D3 in docs/ADMIN_PANEL_PLAN.md for why that is not a JWT claim.
//
// Deploy with JWT verification OFF — the caller is signing in and has no Supabase token yet.
//
//   supabase secrets set --project-ref <ref> FIREBASE_PROJECT_ID=odo-mobile-ba9aa
//   supabase functions deploy admin-session --no-verify-jwt --project-ref <ref>

import { createClient } from 'jsr:@supabase/supabase-js@2'
import { createRemoteJWKSet, jwtVerify } from 'npm:jose@5'

/**
 * Firebase projects whose tokens are accepted, comma-separated.
 *
 * A closed list, not a wildcard: anyone can mint a valid Firebase token for a project of their
 * own, so "issued by Firebase" proves nothing. "Issued by one of ours" is the check.
 *
 * The same secret `firebase-session` reads, and it must list every Firebase project staff sign
 * in against — dev and production are separate projects sharing this one name.
 */
const FIREBASE_PROJECT_IDS = (Deno.env.get('FIREBASE_PROJECT_ID') ?? '')
  .split(',')
  .map((id) => id.trim())
  .filter((id) => id.length > 0)

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
const ANON_KEY = Deno.env.get('SUPABASE_ANON_KEY')!

/** Google's public keys for Firebase ID tokens. jose caches them and refetches on a new kid. */
const FIREBASE_JWKS = createRemoteJWKSet(
  new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com'),
)

/** Tolerance for a device clock running ahead of Google's. */
const CLOCK_SKEW_SECONDS = 60

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response(null, { status: 204, headers: CORS })
  if (req.method !== 'POST') return fail(405, 'method_not_allowed')

  let idToken: string | undefined
  try {
    idToken = (await req.json())?.idToken
  } catch {
    return fail(400, 'malformed_body')
  }
  if (!idToken) return fail(400, 'missing_id_token')

  // ---- 1. Is this really a Firebase token, for one of our projects, right now? ----
  let email: string
  let firebaseUid: string
  try {
    const { payload } = await jwtVerify(idToken, FIREBASE_JWKS, {
      issuer: FIREBASE_PROJECT_IDS.map((id) => `https://securetoken.google.com/${id}`),
      audience: FIREBASE_PROJECT_IDS,
      algorithms: ['RS256'],
    })

    if (typeof payload.sub !== 'string' || payload.sub.length === 0) return fail(401, 'invalid_token')
    if (typeof payload.auth_time !== 'number' || payload.auth_time > nowSeconds() + CLOCK_SKEW_SECONDS) {
      return fail(401, 'invalid_token')
    }
    // No email claim means the token came from a different sign-in method. Valid, but not
    // proof of the one thing being traded here.
    if (typeof payload.email !== 'string' || payload.email.length === 0) {
      return fail(401, 'no_email_claim')
    }

    firebaseUid = payload.sub
    email = payload.email.toLowerCase()
  } catch {
    // Deliberately opaque. Why a token failed is not something an unauthenticated caller
    // needs, and the token itself must never reach a log line.
    return fail(401, 'invalid_token')
  }

  const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, {
    auth: { autoRefreshToken: false, persistSession: false },
  })

  // Which round trip is in flight, for the log line in the catch. Supabase's own messages do
  // not say which call produced them.
  let step = 'lookup-admin'

  try {
    // ---- 2. Is this address staff? ----
    //
    // Before any account is touched, so a rejected address leaves nothing behind — no
    // auth.users row, nothing to clean up later.
    //
    // `is_active` is checked here rather than in the query's filter so that a deactivated
    // admin and an unknown address are told apart in the logs. Both get the same 403: which
    // one it was is not something the sign-in page should reveal.
    const { data: adminRow, error: lookupError } = await admin
      .from('admin_users')
      .select('id, user_id, is_active')
      .eq('email', email)
      .maybeSingle()
    if (lookupError) throw lookupError

    if (!adminRow) {
      console.log('admin-session refused: not on the staff list')
      return fail(403, 'not_an_admin')
    }
    if (!adminRow.is_active) {
      console.log('admin-session refused: deactivated admin')
      return fail(403, 'not_an_admin')
    }

    // ---- 3. Find the account, or make one ----
    //
    // Through an RPC rather than `admin.listUsers`, which pages through every account in the
    // project and gets slower with each signup. Same reason the other two functions do it.
    step = 'find-user'
    const { data: existingId, error: findError } = await admin.rpc('auth_user_id_by_email', {
      p_email: email,
    })
    if (findError) throw findError

    let userId: string
    if (existingId) {
      userId = existingId
      // Re-stamped every time rather than only at creation. It is what a policy would read if
      // one ever needed the coarse check, and an account that predates this function should
      // pick up the current answer on the next sign-in.
      step = 'stamp-claim'
      const { error } = await admin.auth.admin.updateUserById(userId, {
        app_metadata: { odo_admin: true, firebase_uid: firebaseUid },
      })
      if (error) throw error
    } else {
      step = 'create-user'
      const { data: created, error } = await admin.auth.admin.createUser({
        email,
        email_confirm: true,
        app_metadata: { odo_admin: true, firebase_uid: firebaseUid },
      })
      if (error) throw error
      userId = created.user!.id
    }

    // ---- 4. Bind the account to the staff row ----
    //
    // `admin_users` is keyed by email because the row has to exist before the person has an
    // account (see the migration). `user_id` is what `admin_has()` actually joins on, so
    // until this runs once, a seeded admin can sign in and still have no permissions.
    //
    // Only when it would actually change. Writing it unconditionally would put a row in
    // admin_audit_log on every single sign-in — an audit log nobody can read through is the
    // same as not having one.
    if (adminRow.user_id !== userId) {
      step = 'bind-user'
      const { error } = await admin
        .from('admin_users')
        .update({ user_id: userId })
        .eq('id', adminRow.id)
      if (error) throw error
    }

    // ---- 5. Mint an ordinary session ----
    //
    // There is no admin call that hands back an access/refresh pair. `generateLink` produces a
    // single-use token hash without sending mail, and `verify` trades it for a session through
    // GoTrue's normal path — so what the client gets refreshes and revokes like any other.
    step = 'generate-link'
    const { data: link, error: linkError } = await admin.auth.admin.generateLink({
      type: 'magiclink',
      email,
    })
    if (linkError) throw linkError

    const anon = createClient(SUPABASE_URL, ANON_KEY, {
      auth: { autoRefreshToken: false, persistSession: false },
    })
    step = 'verify-token'
    const { data: verified, error: verifyError } = await anon.auth.verifyOtp({
      token_hash: link.properties.hashed_token,
      type: 'email',
    })
    if (verifyError) throw verifyError
    if (!verified.session) throw new Error('verifyOtp returned no session')

    // GoTrue's own shape, passed through. The permission list is deliberately not in here —
    // the client asks `my_admin_identity()` for it with the session it just received, so there
    // is one answer to "what may I do" and it comes from the database rather than from a
    // payload that could go stale in a tab left open.
    return json(200, {
      access_token: verified.session.access_token,
      refresh_token: verified.session.refresh_token,
      expires_in: verified.session.expires_in,
      user: { id: userId, email },
    })
  } catch (error) {
    // The message can quote the address, so it goes to the function's logs and never into the
    // response.
    console.error(`admin-session failed at ${step}:`, error instanceof Error ? error.message : error)
    return fail(500, 'session_mint_failed')
  }
})

const nowSeconds = () => Math.floor(Date.now() / 1000)

/**
 * The panel is served from a different origin than Supabase, so the browser preflights this.
 */
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

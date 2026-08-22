// Trades a Firebase ID token for a Supabase session that may publish.
//
// The sibling of `firebase-session`, and deliberately not an extension of it. That one proves
// a phone number for an app user; this one proves an author's email and stamps a claim that
// unlocks the CMS. Folding them together would mean one function where a bug in the phone path
// can hand out publishing rights, and where every change to app sign-in has to be re-reasoned
// against the blog.
//
// **This is where the author check lives.** The browser has an idea of who may publish, but a
// browser cannot be trusted to enforce it — anyone with a developer console gets past a client
// check. `BLOG_AUTHOR_EMAILS` here is the real gate: an address not on it never receives a
// session, so RLS never sees the claim and every table stays shut.
//
// Deploy with JWT verification OFF — the caller is signing in and has no Supabase token yet.
//
//   supabase secrets set FIREBASE_PROJECT_ID=odo-mobile-ba9aa BLOG_AUTHOR_EMAILS=you@example.com
//   supabase functions deploy blog-session --no-verify-jwt

import { createClient } from 'jsr:@supabase/supabase-js@2'
import { createRemoteJWKSet, jwtVerify } from 'npm:jose@5'

/**
 * Firebase projects whose tokens are accepted, comma-separated.
 *
 * A closed list, not a wildcard: anyone can mint a valid Firebase token for a project of their
 * own, so "issued by Firebase" proves nothing. "Issued by one of ours" is the check.
 */
const FIREBASE_PROJECT_IDS = (Deno.env.get('FIREBASE_PROJECT_ID') ?? '')
  .split(',')
  .map((id) => id.trim())
  .filter((id) => id.length > 0)

/**
 * Who may publish, comma-separated, lower-cased on read.
 *
 * Empty means nobody. That is the safe way to be wrong — the alternative, empty meaning
 * everybody, is a mistake that looks like it is working right up until a stranger publishes.
 */
const AUTHOR_EMAILS = new Set(
  (Deno.env.get('BLOG_AUTHOR_EMAILS') ?? '')
    .split(',')
    .map((email) => email.trim().toLowerCase())
    .filter((email) => email.length > 0),
)

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

  // ---- 2. Is this address allowed to publish? ----
  //
  // Before any account is touched. A rejected address should leave nothing behind — no
  // auth.users row, no author row, nothing to clean up later.
  if (!AUTHOR_EMAILS.has(email)) return fail(403, 'not_an_author')

  const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, {
    auth: { autoRefreshToken: false, persistSession: false },
  })

  // Which round trip is in flight, for the log line in the catch. Supabase's own messages do
  // not say which call produced them.
  let step = 'lookup'

  try {
    // ---- 3. Find the account, or make one ----
    //
    // Through an RPC rather than `admin.listUsers`, which pages through every account in the
    // project and gets slower with each signup. Same reason `firebase-session` does it.
    step = 'find-user'
    const { data: existingId, error: lookupError } = await admin.rpc('auth_user_id_by_email', {
      p_email: email,
    })
    if (lookupError) throw lookupError

    let userId: string
    if (existingId) {
      userId = existingId
      // The claim is re-stamped every time rather than only at creation. It is what RLS reads,
      // and an account that predates this function — or one whose access was revoked by taking
      // the address off the list — should pick up the current answer on the next sign-in.
      step = 'stamp-claim'
      const { error } = await admin.auth.admin.updateUserById(userId, {
        app_metadata: { blog_author: true, firebase_uid: firebaseUid },
      })
      if (error) throw error
    } else {
      step = 'create-user'
      const { data: created, error } = await admin.auth.admin.createUser({
        email,
        email_confirm: true,
        app_metadata: { blog_author: true, firebase_uid: firebaseUid },
      })
      if (error) throw error
      userId = created.user!.id
    }

    // ---- 4. Make sure there is an author row to attribute posts to ----
    //
    // Here rather than in the CMS, because a post's `author_id` is not nullable in practice —
    // a byline that links nowhere is the thing the author page exists to avoid. The name is a
    // placeholder the author can correct later; the email is the part that has to be right.
    step = 'ensure-author'
    const localPart = email.split('@')[0]
    const { error: authorError } = await admin
      .from('blog_authors')
      .upsert(
        {
          email,
          slug: slugify(localPart),
          name: localPart,
          initial: localPart.charAt(0).toUpperCase(),
        },
        { onConflict: 'email', ignoreDuplicates: true },
      )
    if (authorError) throw authorError

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

    // GoTrue's own shape, passed through.
    return json(200, {
      access_token: verified.session.access_token,
      refresh_token: verified.session.refresh_token,
      expires_in: verified.session.expires_in,
      user: { id: userId, email },
    })
  } catch (error) {
    // The message can quote the address, so it goes to the function's logs and never into the
    // response.
    console.error(`blog-session failed at ${step}:`, error instanceof Error ? error.message : error)
    return fail(500, 'session_mint_failed')
  }
})

const slugify = (value: string) =>
  value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'author'

const nowSeconds = () => Math.floor(Date.now() / 1000)

/**
 * The blog is served from a different origin than Supabase, so the browser preflights this.
 * Restricted to the two hosts the CMS is ever opened from.
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

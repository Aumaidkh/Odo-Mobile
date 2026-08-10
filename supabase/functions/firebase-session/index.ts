// Trades a Firebase ID token for a real Supabase session.
//
// Firebase proves the phone number; it cannot issue the session. Every `owner_id` column in
// this project is a `uuid` referencing `auth.users(id)`, and a Firebase UID is a 28-character
// string, so putting a Firebase token in front of PostgREST would mean rewriting every RLS
// policy and migrating every table. This function is the join between the two: it checks
// Firebase's signature, finds or creates the matching `auth.users` row, and mints an ordinary
// GoTrue session for it.
//
// The response is GoTrue's own token shape, unchanged, because the client parses it with the
// same code that reads a password or refresh-token response (`SupabaseTokenEndpoint`).
//
// Deploy with JWT verification OFF — the caller is signing in and has no Supabase token yet.
// `supabase/config.toml` sets that; `--no-verify-jwt` does it for a one-off deploy.

import { createClient } from 'jsr:@supabase/supabase-js@2'
import { createRemoteJWKSet, jwtVerify } from 'npm:jose@5'

const FIREBASE_PROJECT_ID = Deno.env.get('FIREBASE_PROJECT_ID')!
const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
const ANON_KEY = Deno.env.get('SUPABASE_ANON_KEY')!

// Google's public keys for `securetoken@system.gserviceaccount.com`, in JWKS form. jose caches
// them and refetches on an unknown `kid`, which is what makes key rotation a non-event.
const FIREBASE_JWKS = createRemoteJWKSet(
  new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com'),
)

/**
 * A mailbox that cannot receive mail, derived from the number.
 *
 * `generateLink` is how a session gets minted below, and it keys on an email address — but
 * these accounts sign in by phone and have no real one. `.invalid` is reserved by RFC 2606
 * precisely so it can never resolve, so this can never collide with a live address or leak a
 * message to a stranger. Derived rather than random, so the same number always lands on the
 * same account no matter which path created it.
 */
const derivedEmail = (digits: string) => `p${digits}@phone.odo.invalid`

Deno.serve(async (req) => {
  if (req.method !== 'POST') return fail(405, 'method_not_allowed')

  let idToken: string | undefined
  try {
    idToken = (await req.json())?.idToken
  } catch {
    return fail(400, 'malformed_body')
  }
  if (!idToken) return fail(400, 'missing_id_token')

  // ---- 1. Is this really a Firebase token, for this project, right now? ----
  let phone: string
  let firebaseUid: string
  try {
    const { payload } = await jwtVerify(idToken, FIREBASE_JWKS, {
      issuer: `https://securetoken.google.com/${FIREBASE_PROJECT_ID}`,
      audience: FIREBASE_PROJECT_ID,
      algorithms: ['RS256'],
    })

    // `sub` is the Firebase UID and `auth_time` is when the SMS was actually verified. jose
    // has already checked `exp` and the signature; these two are what it cannot know about.
    if (typeof payload.sub !== 'string' || payload.sub.length === 0) return fail(401, 'invalid_token')
    if (typeof payload.auth_time !== 'number' || payload.auth_time > nowSeconds() + CLOCK_SKEW_SECONDS) {
      return fail(401, 'invalid_token')
    }
    // No phone claim means this token came from some other Firebase sign-in method. It is a
    // valid token, but not proof of a number, which is the only thing being traded here.
    if (typeof payload.phone_number !== 'string' || payload.phone_number.length === 0) {
      return fail(401, 'no_phone_claim')
    }

    firebaseUid = payload.sub
    phone = payload.phone_number
  } catch {
    // Deliberately opaque. The reason a token failed is not something an unauthenticated
    // caller needs, and the token itself must never reach a log line.
    return fail(401, 'invalid_token')
  }

  // GoTrue stores phone numbers as bare digits; Firebase's claim carries the leading `+`.
  const digits = phone.replace(/^\+/, '')
  const email = derivedEmail(digits)

  const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, {
    auth: { autoRefreshToken: false, persistSession: false },
  })

  try {
    // ---- 2. Find the account, or make one ----
    //
    // Through an RPC rather than `admin.listUsers`, which pages through every user in the
    // project and gets slower with each signup.
    const { data: existingId, error: lookupError } = await admin.rpc('auth_user_id_by_phone', {
      p_phone: digits,
    })
    if (lookupError) throw lookupError

    let userId: string
    if (existingId) {
      userId = existingId
      // An account that predates this function — or one created by any other path — may have
      // no email, and `generateLink` below needs one. Backfilling is cheaper than a second
      // sign-in mechanism for those users.
      const { data: existing, error: readError } = await admin.auth.admin.getUserById(userId)
      if (readError) throw readError
      if (!existing.user?.email) {
        const { error: patchError } = await admin.auth.admin.updateUserById(userId, {
          email,
          email_confirm: true,
        })
        if (patchError) throw patchError
      }
    } else {
      const { data: created, error: createError } = await admin.auth.admin.createUser({
        phone,
        phone_confirm: true,
        email,
        email_confirm: true,
        // Kept so a support question about one account can be traced back to the Firebase
        // side without matching on phone number.
        user_metadata: { firebase_uid: firebaseUid },
      })
      if (createError) throw createError
      userId = created.user!.id
    }

    // ---- 3. Mint an ordinary session ----
    //
    // There is no admin call that hands back an access/refresh pair directly. `generateLink`
    // produces a single-use token hash without sending anything, and `verify` trades it for a
    // session through GoTrue's normal path — so what the client gets is an ordinary session
    // that refreshes and revokes like any other, not something special-cased.
    const { data: link, error: linkError } = await admin.auth.admin.generateLink({
      type: 'magiclink',
      email,
    })
    if (linkError) throw linkError

    const anon = createClient(SUPABASE_URL, ANON_KEY, {
      auth: { autoRefreshToken: false, persistSession: false },
    })
    const { data: verified, error: verifyError } = await anon.auth.verifyOtp({
      token_hash: link.properties.hashed_token,
      type: 'email',
    })
    if (verifyError) throw verifyError
    if (!verified.session) throw new Error('verifyOtp returned no session')

    // GoTrue's own shape, passed through. SupabaseTokenEndpoint.toSession already parses it.
    return json(200, {
      access_token: verified.session.access_token,
      refresh_token: verified.session.refresh_token,
      expires_in: verified.session.expires_in,
      user: { id: userId },
    })
  } catch (error) {
    // The message can quote the phone number or the email derived from it, so it goes to the
    // function's own logs and never into the response.
    console.error('firebase-session failed:', error instanceof Error ? error.message : error)
    return fail(500, 'session_mint_failed')
  }
})

/** Tolerance for a device clock running ahead of Google's. */
const CLOCK_SKEW_SECONDS = 60

const nowSeconds = () => Math.floor(Date.now() / 1000)

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

/** `error_code`, matching what GoTrue itself returns, so the client reads one shape. */
const fail = (status: number, code: string) => json(status, { error_code: code })

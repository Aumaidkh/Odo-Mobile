// Odo's public legal pages, and the account deletion the Play Store listing points at.
//
//   GET  /functions/v1/legal                  index
//   GET  /functions/v1/legal/terms            Terms of Use
//   GET  /functions/v1/legal/privacy          Privacy Policy
//   GET  /functions/v1/legal/delete-account   the deletion page
//   POST /functions/v1/legal/delete-account   { idToken } -> erases the account
//
// An Edge Function rather than a static host because of that last line: the page has to prove
// who is asking and then actually delete something, and the service-role key that can do the
// deleting has to stay server-side. Serving the two documents from the same place costs
// nothing and keeps one URL prefix on the store listing.
//
// Deploy with JWT verification OFF (`supabase/config.toml` sets it). These are public pages,
// and the POST authenticates its own caller: nothing happens without an unexpired Firebase ID
// token, issued by one of our projects, carrying a phone claim, from an SMS verified minutes
// ago.

import { createRemoteJWKSet, jwtVerify } from 'npm:jose@5'
import { adminClient, eraseOwner } from './erase.ts'
import { DELETE_PAGE_CSP, deleteAccountPage } from './pages/delete-account.ts'
import { indexPage, notFoundPage } from './pages/index-page.ts'
import { htmlResponse } from './pages/layout.ts'
import { privacyPage } from './pages/privacy.ts'
import { termsPage } from './pages/terms.ts'
import { IDENTITY } from './identity.ts'

/**
 * The slug this function is expected to be deployed under.
 *
 * Only a fallback for `supabase functions serve`, which routes `/<slug>/<path>` with no
 * platform prefix to strip. The deployed case does not depend on it — see [subPath].
 */
const FUNCTION_NAME = 'legal'

/**
 * Google's public keys for `securetoken@system.gserviceaccount.com`.
 *
 * The same verification `firebase-session` does, deliberately not shared with it. That
 * function is the sign-in path for every user in the app; a refactor to save thirty lines is
 * not worth the chance of breaking it, and the rule below about verification age is this
 * function's alone.
 */
const FIREBASE_JWKS = createRemoteJWKSet(
  new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com'),
)

/** Tolerance for a device clock running ahead of Google's. */
const CLOCK_SKEW_SECONDS = 60

/**
 * How long an SMS verification stays good enough to delete an account with.
 *
 * A Firebase ID token lives an hour, which is right for reading your own service history and
 * far too long for this. Ten minutes is enough to read the warning and tick the box, and short
 * enough that a token captured from a shared or borrowed phone is useless by the time anyone
 * finds it.
 */
const VERIFICATION_MAX_AGE_SECONDS = 10 * 60

const nowSeconds = () => Math.floor(Date.now() / 1000)

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' },
  })

/** `error_code`, matching the shape `firebase-session` returns, so both read the same. */
const fail = (status: number, code: string) => json(status, { error_code: code })

/**
 * Origins allowed to POST an erase from a browser.
 *
 * Derived from the Firebase projects rather than configured on their own. The deletion page is
 * a Firebase web client, so the only hosts it can legitimately run on are those projects'
 * Hosting domains — deriving them means one less secret to forget, and forgetting this one
 * would break the page silently. `LEGAL_ALLOWED_ORIGINS` adds to the list for a custom domain.
 *
 * Only needed because the page can be served from somewhere other than this function: HTML on
 * the default Supabase domain is rewritten to `text/plain`, so the real copy is on Firebase
 * Hosting and its POST is cross-origin.
 */
const allowedOrigins = (): string[] => [
  ...firebaseProjectIds().flatMap((id) => [`https://${id}.web.app`, `https://${id}.firebaseapp.com`]),
  ...(Deno.env.get('LEGAL_ALLOWED_ORIGINS') ?? '')
    .split(',')
    .map((origin) => origin.trim())
    .filter((origin) => origin.length > 0),
]

/** CORS headers for [origin], or none at all when it is not a host we serve the page from. */
const corsHeaders = (origin: string | null): Record<string, string> =>
  origin !== null && allowedOrigins().includes(origin)
    ? {
      'Access-Control-Allow-Origin': origin,
      'Access-Control-Allow-Headers': 'Content-Type',
      'Access-Control-Allow-Methods': 'POST, OPTIONS',
      // The allowed origin is chosen per request, so a cache must key on it.
      'Vary': 'Origin',
    }
    : {}

/** [response] with [extra] merged in. Copied rather than mutated: a Response's headers are frozen. */
const withHeaders = (response: Response, extra: Record<string, string>): Response => {
  if (Object.keys(extra).length === 0) return response
  const headers = new Headers(response.headers)
  for (const [key, value] of Object.entries(extra)) headers.set(key, value)
  return new Response(response.body, { status: response.status, headers })
}

/** Firebase projects whose tokens count. Both variants' projects — see `firebase-session`. */
const firebaseProjectIds = (): string[] =>
  (Deno.env.get('FIREBASE_PROJECT_ID') ?? '')
    .split(',')
    .map((id) => id.trim())
    .filter((id) => id.length > 0)

/**
 * The Firebase web app config the deletion page initialises with, or null if it is unusable.
 *
 * Public values — an API key restricted to a domain, a project id, an app id — so rendering
 * them into the page is fine. Validated rather than trusted because a config with a missing
 * field fails at `initializeApp` with a console error nobody sees, and the page would look
 * fine while its only button did nothing.
 */
const firebaseWebConfig = (): string | null => {
  const raw = Deno.env.get('FIREBASE_WEB_CONFIG')
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    for (const key of ['apiKey', 'authDomain', 'projectId', 'appId']) {
      if (typeof parsed[key] !== 'string' || parsed[key].length === 0) {
        console.error(`legal: FIREBASE_WEB_CONFIG is missing "${key}"`)
        return null
      }
    }
    return JSON.stringify(parsed)
  } catch {
    console.error('legal: FIREBASE_WEB_CONFIG is not valid JSON')
    return null
  }
}

type Route = 'index' | 'terms' | 'privacy' | 'delete-account' | 'not-found'

/**
 * Which page a path asks for.
 *
 * Generous with aliases on purpose. The deletion URL is typed into the Play Console once and
 * is awkward to change afterwards, and a privacy link gets copied into an app store, a website
 * footer and an email signature with a different spelling each time. Answering `/privacy-policy`
 * as well as `/privacy` costs a line; a 404 on a store listing costs a review cycle.
 */
const ALIASES: Record<string, Route> = {
  '': 'index',
  'index': 'index',
  'terms': 'terms',
  'terms-of-use': 'terms',
  'terms-and-conditions': 'terms',
  'tos': 'terms',
  'tnc': 'terms',
  'privacy': 'privacy',
  'privacy-policy': 'privacy',
  'delete-account': 'delete-account',
  'delete': 'delete-account',
  'delete-my-account': 'delete-account',
  'account-deletion': 'delete-account',
  'data-deletion': 'delete-account',
}

/**
 * Strip whatever prefix the host put in front of this function's own paths.
 *
 * Three hosts, three shapes, and the deployed one must not depend on the slug: Supabase serves
 * every function at `/functions/v1/<slug>/…` whatever it is called, so dropping three fixed
 * segments is both simpler and safer than looking for the name. Getting that wrong would 404
 * every route — including the deletion URL on the store listing — for nothing worse than
 * naming the function `Legal`.
 *
 *   deployed                    /functions/v1/<slug>/terms  ->  terms
 *   supabase functions serve    /legal/terms                ->  terms
 *   deno run index.ts           /terms                      ->  terms
 */
const subPath = (segments: string[]): string[] => {
  if (segments[0] === 'functions' && segments[1] === 'v1') return segments.slice(3)
  if (segments[0]?.toLowerCase() === FUNCTION_NAME) return segments.slice(1)
  return segments
}

const routeOf = (pathname: string): Route => {
  const segments = pathname.split('/').filter((segment) => segment.length > 0)
  const rest = subPath(segments)
    .join('/')
    .toLowerCase()
    .replace(/\.html?$/, '')
  return ALIASES[rest] ?? 'not-found'
}

/**
 * Handle a deletion request.
 *
 * Every failure that is not "we could not verify you" returns one opaque code. The caller is
 * unauthenticated by definition, and the difference between "no such account" and "the
 * database refused" is not theirs to learn from an error body.
 */
const handleDelete = async (req: Request): Promise<Response> => {
  const projectIds = firebaseProjectIds()
  const supabaseUrl = Deno.env.get('SUPABASE_URL')
  const serviceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')
  if (projectIds.length === 0 || !supabaseUrl || !serviceRoleKey) {
    console.error('legal: deletion is not configured — check FIREBASE_PROJECT_ID and the service role key')
    return fail(503, 'not_configured')
  }

  let idToken: unknown
  try {
    idToken = (await req.json())?.idToken
  } catch {
    return fail(400, 'malformed_body')
  }
  if (typeof idToken !== 'string' || idToken.length === 0) return fail(400, 'missing_id_token')

  let phone: string
  try {
    const { payload } = await jwtVerify(idToken, FIREBASE_JWKS, {
      issuer: projectIds.map((id) => `https://securetoken.google.com/${id}`),
      audience: projectIds,
      algorithms: ['RS256'],
    })

    // No phone claim means some other Firebase sign-in method. A valid token, but not proof of
    // a number — and the number is the only thing that identifies the account being erased.
    if (typeof payload.phone_number !== 'string' || payload.phone_number.length === 0) {
      return fail(401, 'no_phone_claim')
    }
    if (typeof payload.auth_time !== 'number') return fail(401, 'invalid_token')

    const age = nowSeconds() - payload.auth_time
    if (age < -CLOCK_SKEW_SECONDS) return fail(401, 'invalid_token')
    if (age > VERIFICATION_MAX_AGE_SECONDS) return fail(401, 'stale_verification')

    phone = payload.phone_number
  } catch {
    // Opaque on purpose, and the token never reaches a log line.
    return fail(401, 'invalid_token')
  }

  const admin = adminClient(supabaseUrl, serviceRoleKey)

  try {
    // GoTrue stores phone numbers as bare digits; the Firebase claim carries the leading `+`.
    // The RPC trims it either way, but passing what the database stores keeps the two sides
    // honest about which form is canonical.
    const digits = phone.replace(/^\+/, '')
    const { data: ownerId, error } = await admin.rpc('auth_user_id_by_phone', { p_phone: digits })
    if (error) throw error

    // Verified the number, found no account. Nothing to erase, and saying so is safe: they
    // just proved the number is theirs.
    if (!ownerId) return json(200, { status: 'no_account' })

    const { filesRemoved } = await eraseOwner(admin, ownerId)
    console.log(`legal: erased owner ${ownerId} and ${filesRemoved} stored files`)
    return json(200, { status: 'deleted' })
  } catch (error) {
    // The message can quote the phone number, so it goes to the function log and nowhere else.
    console.error('legal: erase failed:', error instanceof Error ? error.message : error)
    return fail(500, 'erase_failed')
  }
}

Deno.serve((req) => {
  const route = routeOf(new URL(req.url).pathname)

  const cors = corsHeaders(req.headers.get('Origin'))

  if (req.method === 'POST') {
    return route === 'delete-account'
      ? handleDelete(req).then((response) => withHeaders(response, cors))
      : Promise.resolve(withHeaders(fail(405, 'method_not_allowed'), cors))
  }

  if (req.method === 'OPTIONS') {
    return Promise.resolve(
      new Response(null, {
        status: 204,
        headers: {
          Allow: route === 'delete-account' ? 'GET, HEAD, POST, OPTIONS' : 'GET, HEAD, OPTIONS',
          ...cors,
        },
      }),
    )
  }

  if (req.method !== 'GET' && req.method !== 'HEAD') {
    return Promise.resolve(fail(405, 'method_not_allowed'))
  }

  switch (route) {
    case 'index':
      return Promise.resolve(htmlResponse(indexPage()))
    case 'terms':
      return Promise.resolve(htmlResponse(termsPage()))
    case 'privacy':
      return Promise.resolve(htmlResponse(privacyPage()))
    case 'delete-account':
      // Never cached: it carries the Firebase config and a form whose whole job is to be
      // current, and a stale copy in a CDN is a deletion button wired to nothing.
      return Promise.resolve(
        htmlResponse(deleteAccountPage(firebaseWebConfig()), {
          cacheSeconds: 0,
          csp: DELETE_PAGE_CSP,
        }),
      )
    case 'not-found':
      return Promise.resolve(htmlResponse(notFoundPage(), { status: 404, cacheSeconds: 0 }))
  }
})

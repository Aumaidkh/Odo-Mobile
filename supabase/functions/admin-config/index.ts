// Reads and writes Firebase Remote Config, for the panel's feature-flags screen.
//
// **Why this function exists at all.** Every flag in this app comes from Remote Config —
// `ConfigSource` in `:core:config`, the app-status gate, the rollout percentages. None of it
// is in Supabase, so the panel cannot reach it the way it reaches a table. And Remote Config's
// REST API authenticates with a Google service account, which is a private key that must never
// be in a browser. So the browser talks to this, and this talks to Google.
//
// Two checks, both server-side:
//   1. The platform verifies the caller's Supabase JWT (verify_jwt is on for this one, unlike
//      the sign-in functions — the caller here is already signed in).
//   2. `admin_has('flags.write')` is asked *as the caller*, so the answer is the same one RLS
//      would give. A valid session is not enough; the permission is.
//
//   supabase secrets set --project-ref <ref> FIREBASE_SERVICE_ACCOUNT="$(cat service-account.json)"
//   supabase functions deploy admin-config --project-ref <ref>
//
// The service account needs the `firebase.remoteconfig.update` permission — the "Firebase
// Remote Config Admin" role in the Google Cloud console.

import { createClient } from 'jsr:@supabase/supabase-js@2'
import { SignJWT, importPKCS8 } from 'npm:jose@5'

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const ANON_KEY = Deno.env.get('SUPABASE_ANON_KEY')!

/**
 * The service account JSON, as a single secret.
 *
 * Absent on a project nobody has configured, which is a state worth reporting plainly rather
 * than failing as an opaque 500 — the panel says "not configured" and names this secret.
 */
const SERVICE_ACCOUNT = Deno.env.get('FIREBASE_SERVICE_ACCOUNT')

const SCOPE = 'https://www.googleapis.com/auth/firebase.remoteconfig'

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response(null, { status: 204, headers: CORS })
  if (req.method !== 'GET' && req.method !== 'POST') return fail(405, 'method_not_allowed')

  // ---- 1. Who is asking, and may they? ----
  //
  // Asked as the caller rather than with the service role, so this is the same answer RLS
  // would give. A function that checked with elevated rights would be a second opinion, and
  // the second opinion is the one that drifts.
  const authorization = req.headers.get('Authorization')
  if (!authorization) return fail(401, 'not_signed_in')

  const caller = createClient(SUPABASE_URL, ANON_KEY, {
    global: { headers: { Authorization: authorization } },
    auth: { autoRefreshToken: false, persistSession: false },
  })

  const { data: permitted, error: permissionError } = await caller.rpc('admin_has', {
    p_permission: 'flags.write',
  })

  // An error here is refused, not reported as a server fault. `admin_has` is revoked from
  // `anon`, so an anonymous caller — whose anon key is a perfectly valid JWT as far as the
  // gateway is concerned — makes this fail rather than return false. Answering 500 to that
  // would tell a stranger the difference between "not allowed" and "broken", and would show
  // an admin a fault when what happened was a refusal. The detail goes to the log.
  if (permissionError) {
    console.error('admin-config permission check refused:', permissionError.message)
    return fail(403, 'not_permitted')
  }
  if (permitted !== true) return fail(403, 'not_permitted')

  if (!SERVICE_ACCOUNT) return fail(503, 'remote_config_not_configured')

  let account: { client_email: string; private_key: string; project_id: string }
  try {
    account = JSON.parse(SERVICE_ACCOUNT)
  } catch {
    console.error('admin-config: FIREBASE_SERVICE_ACCOUNT is not valid JSON')
    return fail(503, 'remote_config_not_configured')
  }

  // ---- 2. Become the service account ----
  let accessToken: string
  try {
    accessToken = await googleAccessToken(account)
  } catch (error) {
    console.error('admin-config token exchange failed:', error instanceof Error ? error.message : error)
    return fail(502, 'google_auth_failed')
  }

  const endpoint = `https://firebaseremoteconfig.googleapis.com/v1/projects/${account.project_id}/remoteConfig`

  // ---- 3. Read ----
  if (req.method === 'GET') {
    const response = await fetch(endpoint, {
      headers: { Authorization: `Bearer ${accessToken}` },
    })
    if (!response.ok) {
      console.error('admin-config read failed:', response.status, (await response.text()).slice(0, 300))
      // A 403 from Google is its own outcome. It means the service account is real and its key
      // works — the token exchange above succeeded — but nobody granted it the Remote Config
      // role, which is a separate step from creating the key and easy to miss. Reported as a
      // generic read failure it looks like an outage; named, it tells whoever set this up
      // exactly which box is unticked.
      if (response.status === 403) return fail(403, 'remote_config_not_permitted')
      return fail(502, 'remote_config_read_failed')
    }
    const template = await response.json()
    // The ETag comes back as a header and is required to write. Handing it to the client
    // rather than holding it here is what makes the write a genuine compare-and-set: two
    // people editing at once, and the second one is told rather than silently winning.
    return json(200, {
      etag: response.headers.get('etag'),
      parameters: template.parameters ?? {},
    })
  }

  // ---- 4. Write one parameter ----
  //
  // One at a time, and against the ETag the caller read. Remote Config has no partial update:
  // the whole template is replaced, so this reads the current one, changes the single value,
  // and puts it back. Without the ETag that read-modify-write would quietly discard whatever
  // somebody else changed in between.
  let body: { key?: string; value?: string; etag?: string }
  try {
    body = await req.json()
  } catch {
    return fail(400, 'malformed_body')
  }
  if (!body.key || body.value === undefined || !body.etag) return fail(400, 'missing_fields')

  const current = await fetch(endpoint, { headers: { Authorization: `Bearer ${accessToken}` } })
  if (!current.ok) {
    if (current.status === 403) return fail(403, 'remote_config_not_permitted')
    return fail(502, 'remote_config_read_failed')
  }
  const template = await current.json()

  template.parameters = template.parameters ?? {}
  const existing = template.parameters[body.key]
  if (!existing) return fail(404, 'unknown_parameter')
  // Only the default value is touched. Conditional values are per-audience rollouts set up in
  // the Firebase console, and a panel that silently flattened them would be a panel that
  // undoes somebody's staged rollout without mentioning it.
  existing.defaultValue = { value: body.value }

  const write = await fetch(endpoint, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json; UTF-8',
      'If-Match': body.etag,
    },
    body: JSON.stringify(template),
  })

  if (write.status === 409 || write.status === 412) return fail(409, 'changed_elsewhere')
  if (!write.ok) {
    console.error('admin-config write failed:', write.status, (await write.text()).slice(0, 300))
    return fail(502, 'remote_config_write_failed')
  }

  return json(200, { etag: write.headers.get('etag') })
})

/**
 * A Google access token, from the service account's key.
 *
 * The two-step OAuth flow for service accounts: sign a short-lived assertion with the private
 * key, then trade it. No library beyond the JWT signing, because that is all it is.
 */
async function googleAccessToken(account: { client_email: string; private_key: string }): Promise<string> {
  const key = await importPKCS8(account.private_key.replace(/\\n/g, '\n'), 'RS256')
  const assertion = await new SignJWT({ scope: SCOPE })
    .setProtectedHeader({ alg: 'RS256' })
    .setIssuer(account.client_email)
    .setAudience('https://oauth2.googleapis.com/token')
    .setIssuedAt()
    .setExpirationTime('5m')
    .sign(key)

  const response = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion,
    }),
  })
  if (!response.ok) throw new Error(`token exchange ${response.status}`)
  return (await response.json()).access_token
}

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, content-type, apikey',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
}

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...CORS },
  })

const fail = (status: number, code: string) => json(status, { error: code })

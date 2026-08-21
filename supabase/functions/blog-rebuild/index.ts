// Asks GitHub to rebuild the static blog.
//
// The reading pages at /blog/<slug> are HTML files written at deploy time, so a
// post that has just been published is not on the site until something
// regenerates them. That something used to be a person running two commands,
// which meant the site was only as fresh as somebody's memory.
//
// The CMS calls this after any change that alters what a stranger can read —
// publishing, unpublishing, editing a live post, discarding one. This function
// checks the caller really is an author and then fires a repository_dispatch;
// the workflow does the building and deploying. Nothing here touches Firebase,
// because a token that can deploy a website has no business living next to a
// database.
//
// The caller's own Supabase token is the credential. A GitHub token never
// reaches the browser — it stays a secret on this function, which is the whole
// reason the browser cannot simply call GitHub itself.

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const ANON_KEY = Deno.env.get('SUPABASE_ANON_KEY')!

// A fine-grained PAT with Contents: read and write on this repository only. The
// dispatch endpoint needs write because it can start a workflow.
const GITHUB_TOKEN = Deno.env.get('BLOG_DISPATCH_TOKEN') ?? ''
const GITHUB_REPO = Deno.env.get('BLOG_DISPATCH_REPO') ?? 'AumaidKh/Odo-Mobile'

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, content-type, apikey',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, 'content-type': 'application/json' },
  })

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response(null, { status: 204, headers: CORS })
  if (req.method !== 'POST') return json({ error: 'method not allowed' }, 405)

  const authorization = req.headers.get('authorization') ?? ''
  if (!authorization.toLowerCase().startsWith('bearer ')) {
    return json({ error: 'not signed in' }, 401)
  }

  // GoTrue is asked who this token belongs to rather than the JWT being decoded
  // here. It is one request, it respects expiry and revocation, and it means
  // this function never handles the signing secret.
  const who = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
    headers: { apikey: ANON_KEY, authorization },
  })
  if (!who.ok) return json({ error: 'not signed in' }, 401)

  const user = await who.json()
  // The same claim blog-session mints and the row-level policies trust. An
  // ordinary app account — somebody who signed in to the phone app — has a valid
  // token and no business rebuilding the website.
  if (user?.app_metadata?.blog_author !== true) {
    return json({ error: 'not an author' }, 403)
  }

  if (!GITHUB_TOKEN) {
    // Deliberately not a 500. The CMS treats this as "the site will catch up
    // later", which is true: the workflow can always be run by hand.
    return json({ dispatched: false, reason: 'no dispatch token configured' }, 200)
  }

  const dispatch = await fetch(`https://api.github.com/repos/${GITHUB_REPO}/dispatches`, {
    method: 'POST',
    headers: {
      authorization: `Bearer ${GITHUB_TOKEN}`,
      accept: 'application/vnd.github+json',
      'content-type': 'application/json',
      'user-agent': 'odo-blog-rebuild',
    },
    body: JSON.stringify({
      event_type: 'blog-changed',
      client_payload: { by: user.email ?? '' },
    }),
  })

  if (!dispatch.ok) {
    return json({ dispatched: false, status: dispatch.status, error: await dispatch.text() }, 502)
  }
  return json({ dispatched: true }, 202)
})

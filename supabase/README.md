# Supabase server-side pieces

The schema itself is not here — it lives in `docs/SUPABASE_BOOTSTRAP.md` and is pasted into
the dashboard's SQL Editor. This directory holds the two things that cannot be a paste: the
Edge Function, and the one SQL object that function depends on.

## `firebase-session`

Trades a Firebase ID token for a real Supabase session.

Firebase proves the phone number. It cannot issue the session, because every `owner_id` in
this project is a `uuid` referencing `auth.users(id)` and a Firebase UID is a 28-character
string. This function is the join: it checks Firebase's signature, finds or creates the
matching `auth.users` row, and mints an ordinary GoTrue session for it.

```
POST /functions/v1/firebase-session
  { "idToken": "<Firebase ID token>" }

200 { "access_token": "…", "refresh_token": "…", "expires_in": 3600, "user": { "id": "<uuid>" } }
400 { "error_code": "missing_id_token" | "malformed_body" }
401 { "error_code": "invalid_token" | "no_phone_claim" }
500 { "error_code": "session_mint_failed" }
```

The 200 body is GoTrue's own token shape, so `SupabaseTokenEndpoint.toSession` parses it with
the same code that reads a password or refresh-token response.

### Deploying

```bash
supabase link --project-ref <project-ref>

# The SQL object the function calls. Or paste it into the SQL Editor — it is idempotent.
supabase db push

# The one secret that is not injected automatically. SUPABASE_URL,
# SUPABASE_ANON_KEY and SUPABASE_SERVICE_ROLE_KEY already are.
supabase secrets set FIREBASE_PROJECT_ID=<firebase-project-id>

supabase functions deploy firebase-session
```

`config.toml` sets `verify_jwt = false` for this function, which is the point: the caller is
signing in and has no Supabase token yet. It is not unauthenticated — it authenticates the
Firebase ID token itself, against Google's published keys, and will not act on a token that is
expired, issued for another Firebase project, or carrying no phone claim.

### Checking it works

```bash
curl -sX POST "https://<project-ref>.supabase.co/functions/v1/firebase-session" \
  -H "Content-Type: application/json" \
  -d '{"idToken":"<paste from a debug build>"}'
```

Then confirm the access token it returns actually passes row-level security, which is the part
that would silently not work if the session were minted wrongly:

```bash
curl -s "https://<project-ref>.supabase.co/rest/v1/profiles?select=id" \
  -H "apikey: <anon key>" -H "Authorization: Bearer <access_token from above>"
```

### Costs

Firebase phone auth needs the Blaze plan — roughly $0.01 per verification in India, with the
first 10 a day free. Edge Function invocations are inside the free tier at any volume this app
will see.

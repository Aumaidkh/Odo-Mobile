# Supabase server-side pieces

The schema itself is not here — it lives in `docs/SUPABASE_BOOTSTRAP.md` and is pasted into
the dashboard's SQL Editor. This directory holds what cannot be a paste: the Edge Functions,
and the SQL objects they depend on.

**Migrations here are not tracked by `supabase db push`.** Both projects' schema was bootstrapped
by hand-pasting SQL into the dashboard's SQL Editor rather than by running the CLI's migration
flow, so the remote `supabase_migrations.schema_migrations` history table is empty on both — a
plain `supabase db push` would try to replay every migration in this folder from the beginning,
including ones already live. Apply a single new migration file directly instead:

```bash
supabase link --project-ref <project-ref>
supabase db query --linked -f supabase/migrations/<file>.sql
```

## Vehicle catalog: accept → auto-promote

`20260830130000_promote_vehicle_catalog_submission.sql` adds a trigger on
`vehicle_catalog_submissions`: the moment a reviewer sets a row's `status` to `'accepted'`
(hand-edited in the SQL Editor — the table has no client-facing update policy), it inserts the
make (if new), the model's trim-less base row (if new), and the named trim (if given and new)
into `vehicle_makes` / `vehicle_models`. Ids use the exact same slug algorithm as
`VehicleSeedData.kt`'s `slug()`, so a promoted row lands where a bundled seed row for the same
make/model/trim would have.

Manual review still decides *whether* a submission is real — nothing here auto-accepts
anything. This only automates the mechanical follow-up (writing the catalog rows by hand)
once that call has already been made.

### Status — dev

Applied to `odo-mobile_dev` (`gezicmstbgfpwwohiboq`) on 2026-08-30 via the command above, and
verified with a throwaway submission wrapped in `begin ... rollback` (insert as `pending`,
`update ... set status = 'accepted'`, confirm the two `vehicle_models` rows appear under the
existing `make-tata` row, `rollback`). **Prod (`odo-mobile-ba9aa` / `kxxgfhwnidgfvjowqaad`) does
not have this yet.**

### Applying to prod

1. **Link to the production project** (not the dev one — check `supabase status` or the ref if
   unsure; the two refs are `gezicmstbgfpwwohiboq` = dev, `kxxgfhwnidgfvjowqaad` = prod):
   ```bash
   supabase link --project-ref kxxgfhwnidgfvjowqaad
   ```
2. **Apply the migration** — the same one-file command as above, not `supabase db push` (see the
   note at the top of this file about why):
   ```bash
   supabase db query --linked -f supabase/migrations/20260830130000_promote_vehicle_catalog_submission.sql
   ```
3. **Verify without touching real data.** Run this in one shot; the transaction never commits,
   so nothing is left behind either way:
   ```bash
   supabase db query --linked "
   begin;
   insert into vehicle_catalog_submissions (id, owner_id, make, model, variant, status, created_at)
   values (
     '11111111-1111-1111-1111-111111111111',
     (select id from profiles limit 1),  -- any real profile id satisfies the FK for this test
     'Tata', 'Sierra', 'Pure +', 'pending', now()
   );
   update vehicle_catalog_submissions set status = 'accepted'
     where id = '11111111-1111-1111-1111-111111111111';
   select id, make_id, name, variant, display_order from vehicle_models
     where make_id = 'make-tata' and name = 'Sierra' order by display_order;
   rollback;
   "
   ```
   Expect two rows back: `model-tata-sierra` (`variant` null) and `model-tata-sierra-pure-plus`
   (`variant` = `Pure +`). If `make-tata` doesn't already exist in prod's seed, adjust the
   `make_id`/`name` in the query to a make you know is there, or drop the `where` clause and
   just eyeball the newest two rows by `display_order`.
4. **Re-link back to dev when done**, so a later `supabase db query --linked` from this repo
   doesn't accidentally target prod:
   ```bash
   supabase link --project-ref gezicmstbgfpwwohiboq
   ```

Real acceptances afterward are just: `update vehicle_catalog_submissions set status = 'accepted'
where id = '<uuid>';` in the SQL Editor — the trigger does the rest.

| Function | What it is |
| --- | --- |
| [`firebase-session`](#firebase-session) | Trades a Firebase ID token for a Supabase session. The sign-in path. |
| [`legal`](#legal) | The public Terms and Privacy pages, and the account deletion the Play listing points at. |

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
#
# Comma-separated, and it must list EVERY Firebase project the app signs in
# against — the debug build uses odo-mobile-dev and release uses the production
# project, while both talk to this one Supabase project. Miss one and that
# variant gets a 401 from here after Firebase has already verified the number.
supabase secrets set FIREBASE_PROJECT_ID=odo-mobile-dev,odo-mobile-ba9aa

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


## `legal`

Three public pages, and one of them does something.

```
GET  /functions/v1/legal                  index
GET  /functions/v1/legal/terms            Terms of Use
GET  /functions/v1/legal/privacy          Privacy Policy
GET  /functions/v1/legal/delete-account   the deletion page
POST /functions/v1/legal/delete-account   { "idToken": "<Firebase ID token>" }

200 { "status": "deleted" | "no_account" }
400 { "error_code": "malformed_body" | "missing_id_token" }
401 { "error_code": "invalid_token" | "no_phone_claim" | "stale_verification" }
500 { "error_code": "erase_failed" }
503 { "error_code": "not_configured" }
```

Common misspellings resolve rather than 404 — `/privacy-policy`, `/terms-and-conditions`,
`/data-deletion`, `/delete`, a trailing `.html`. A store listing URL is typed once and is
awkward to correct.

An Edge Function rather than a static host because Google requires that an owner can delete
their account **from the web, without installing the app**, and that the page actually deletes
something. Doing that needs the service-role key, which has to stay server-side.

### Two environments

Everything below exists twice, and the pairs must not cross. A debug build signs in against the
dev Firebase project and writes to the dev Supabase project, so the deletion page it links to
has to be the one that can delete what that build created.

| | Firebase project | Supabase ref | Pages |
| --- | --- | --- | --- |
| debug, stage | `odo-mobile-dev` | `gezicmstbgfpwwohiboq` | https://odo-mobile-dev.web.app |
| release | `odo-mobile-ba9aa` | `kxxgfhwnidgfvjowqaad` | https://odo-mobile-ba9aa.web.app |

The app's half is automatic: `google-services.json` is per-variant, and
`infrastructure/supabase/build.gradle.kts` picks the Supabase project from the build type. The
pages' half is `sh web/deploy.sh <dev|prod>`, which builds and deploys in one step so the two
cannot drift. The pairs themselves are the `ENVIRONMENTS` table in `web/build.ts`, and the
Firebase aliases are in `.firebaserc`.

`sh supabase/check-legal.sh <dev|prod>` verifies one environment end to end: the function
serves, the pages are deployed, and the pages point at that environment's Firebase project and
that environment's function.

**Each Supabase project needs its own secrets and its own deployed functions.** They share
nothing. In particular `FIREBASE_PROJECT_ID` on *each* project must list the Firebase projects
that sign in against it, because that is what both token verification and the CORS allow-list
are derived from.

### Where the pages are actually read: Firebase Hosting

**The URLs above serve the right HTML and no browser will render it.** Supabase rewrites any
`text/html` response on its default domain to `text/plain` and forces
`default-src 'none'; sandbox` over it, so the documents show as source text and the deletion
page's script cannot run at all. Serving HTML needs a custom domain, which is a paid add-on.

So the pages are built to static HTML and served from Firebase Hosting instead, and the
function keeps the half that has to stay server-side:

```
https://odo-mobile-ba9aa.web.app/privacy          <- what the store listing points at
https://odo-mobile-ba9aa.web.app/delete-account
POST .../functions/v1/legal/delete-account        <- still the function, cross-origin now
```

`web/build.ts` renders the pages by importing the same modules the function does, so there is
one copy of the markup:

```bash
sh web/deploy.sh prod    # or dev — builds for that environment, then deploys to its project
```

Two consequences worth knowing:

- **The deletion page's POST is cross-origin**, so the function answers it with CORS headers.
  The allowed origins are derived from `FIREBASE_PROJECT_ID` (`https://<id>.web.app` and
  `https://<id>.firebaseapp.com`), so no new secret — but a project missing from that list is
  an origin the browser refuses. `LEGAL_ALLOWED_ORIGINS` adds a custom domain later.
- **The CSP lives in `firebase.json`**, not in the function, for the copy people read. One
  entry per path, deliberately not globbed — see the note in that file.

### How deletion works

The account *is* the phone number, so proving the number is the only way to prove the account.

1. The visitor types their number; Firebase sends an SMS code, gated by an invisible reCAPTCHA.
2. Confirming the code signs them into Firebase and yields an ID token.
3. The browser POSTs that token back to the same URL. The function verifies it against Google's
   published keys — right project, unexpired, carries a phone claim, and **verified within the
   last 10 minutes**, which an ordinary hour-long ID token is not enough for.
4. It erases everything: files first, then the two tables that block a delete, then the account.
5. Once the server confirms, the browser deletes the Firebase user too, so the number itself is
   no longer held anywhere. Client-side, because `deleteUser` only needs a recent sign-in —
   which just happened — and that keeps a Google service-account key out of the secrets.

What the erase covers, and why it is only three steps (`erase.ts`):

- **Storage** — every object under `{owner_id}/` in `bill-photos`, `documents`, `passports`,
  `avatars` and `app-logs`. These are not foreign-keyed to anything, so nothing removes them
  automatically. **Adding a bucket to §19 of the bootstrap means adding it to `OWNER_BUCKETS`.**
- **`payments` and `resale_passports`** — the only two tables referencing `profiles` with
  `ON DELETE RESTRICT`. Both belong to features that are off, so today they delete nothing; they
  are cleared first so the day one holds a row is not the day deletion starts failing.
- **`auth.users`** — a hard delete, not GoTrue's soft delete, which would keep the phone number.
  Everything else follows from the cascade: `profiles` → cars, service logs, bills, line items,
  documents, reminders, health scores, per-km snapshots, device tokens, overcharge reports,
  subscriptions.

The anonymous price points in `fairness_data_points` survive, and both legal pages say so. That
table has no `owner_id`, no `car_id` and no `bill_id` by design — there is nothing in it to
trace back to a person.

### Before it works: two things in the Firebase console

The page is a small Firebase web client, and it fails **silently** without both of these — the
form renders, the button does nothing useful.

1. **Register a Web App.** Project settings → General → Your apps → Add app → Web. Use the
   production project (`odo-mobile-ba9aa`); accounts are keyed on the phone number, not on which
   Firebase project created them, so a number that signed up through a debug build is still
   deleted correctly. Copy the config object it shows you.
2. **Authorise the host.** Authentication → Settings → Authorized domains. Firebase Hosting's
   own `<project-id>.web.app` and `.firebaseapp.com` are on that list by default, so the page
   as deployed needs nothing done here. Any other host — a custom domain, or
   `<project-ref>.supabase.co` if the function ever serves the page again — has to be added,
   or sending a code fails with `auth/unauthorized-domain`.

### Deploying

```bash
supabase link --project-ref <project-ref>

# The Firebase web config from step 1, as one line of JSON. Public values — an API key
# restricted by domain, a project id, an app id — but they live in a secret rather than in the
# repo so the two Firebase projects are not baked into a deployed artifact.
#
# Must contain apiKey, authDomain, projectId and appId. A config missing any of them is
# rejected at render time and the page shows an "email us instead" fallback rather than a
# broken form.
supabase secrets set FIREBASE_WEB_CONFIG='{"apiKey":"…","authDomain":"odo-mobile-ba9aa.firebaseapp.com","projectId":"odo-mobile-ba9aa","appId":"1:…:web:…"}'

# Already set if firebase-session is deployed — the same list, used the same way.
supabase secrets set FIREBASE_PROJECT_ID=odo-mobile-dev,odo-mobile-ba9aa

supabase functions deploy legal
```

`SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` are injected automatically. `config.toml` sets
`verify_jwt = false`, which is the point: a Play reviewer and somebody who has already
uninstalled the app both have to be able to read these pages.

### Deploying without the CLI

The dashboard's function editor is one file, and this function is seven. Bundle it first:

```bash
deno bundle --external 'npm:*' --external 'jsr:*' \
  --output legal.bundle.js supabase/functions/legal/index.ts
```

`--external` keeps `npm:jose` and `jsr:@supabase/supabase-js` as plain imports for the edge
runtime to resolve, which is the difference between a 47 KB file and a two-megabyte one. The
output is byte-for-byte equivalent — same pages, same responses.

Then, in the dashboard:

1. **Project Settings → Edge Functions → Secrets.** Add `FIREBASE_WEB_CONFIG` and
   `FIREBASE_PROJECT_ID` as above. Secrets apply immediately, with no redeploy.
2. **Edge Functions → Deploy a new function → Via Editor.** Call it `legal` — the slug is the
   URL, so it is what the store listing and the app will point at. Routing does not depend on
   it (`subPath` strips `/functions/v1/<slug>` whatever the slug is), so a rename costs you the
   URLs, not a 404 on every page. Replace the template with the bundle and deploy.
3. **Turn off "Verify JWT with legacy secret"** in the function's settings. Without this every
   page returns 401 before it runs, including the deletion URL on the store listing.

> **This toggle is known to switch itself back on when a function is updated**
> ([supabase/supabase#43608](https://github.com/supabase/supabase/issues/43608)). Re-check it
> after *every* redeploy, and confirm with a request that carries no `Authorization` header:
>
> ```bash
> curl -so /dev/null -w '%{http_code}\n' "https://<project-ref>.supabase.co/functions/v1/legal/privacy"
> # 200 = public. 401 = the toggle came back and the Play listing links are dead.
> ```

The bundle is a build output, not a source of truth. Edit the seven files and re-bundle; do not
edit the pasted copy in the dashboard, or the next bundle silently reverts it.

### Checking it works

```bash
BASE="https://<project-ref>.supabase.co/functions/v1/legal"

# All three pages render, and an unknown path 404s rather than erroring.
for path in "" /terms /privacy /delete-account /privacy-policy /nope; do
  printf '%-18s %s\n' "$path" "$(curl -so /dev/null -w '%{http_code}' "$BASE$path")"
done

# The POST refuses anything that is not a fresh, valid token.
curl -sX POST "$BASE/delete-account" -H 'Content-Type: application/json' -d '{}'
# -> {"error_code":"missing_id_token"}
curl -sX POST "$BASE/delete-account" -H 'Content-Type: application/json' -d '{"idToken":"not.a.token"}'
# -> {"error_code":"invalid_token"}
```

Then do the real thing once, end to end, with a number you are willing to lose: open
`$BASE/delete-account` in a browser, delete a throwaway account, and confirm afterwards that
`profiles` has no row for it and the buckets have no folder named with its id. A deletion page
that 200s without deleting is the failure mode this needs a human to rule out.

Locally, without deploying:

```bash
supabase functions serve legal --no-verify-jwt --env-file supabase/.env.local
```

### Keeping it honest

Both documents describe what the app actually does, so they are part of a change, not a thing
to update later:

- `identity.ts` — `legalEntity` and `registeredAddress` are `null` until the registered name and
  office are confirmed. The pages omit those lines rather than print a placeholder, but a
  published privacy notice under the DPDP Act is expected to name the entity and a grievance
  contact. **Fill both before the app is public.** `lastUpdated` is hand-maintained so the
  footer date does not move on unrelated redeploys.
- `privacy.ts` — two claims are load-bearing. Bill and document text is read on the device
  (true while `:infrastructure:ai` uses ML Kit locally), and trip coordinates never leave the
  phone (true while `TripDto` has no coordinate fields). The permission list mirrors
  `androidApp/src/main/AndroidManifest.xml`.
- `terms.ts` — the disclaimers about reminders, bill checks and the health score are the honest
  description of those features, not padding. A reminder that does not arrive has real
  consequences for an owner.

### Costs

Free. Static HTML on the Supabase free tier, and a deletion is a handful of queries. The SMS
that verifies the number is billed by Firebase at the same rate as a sign-in.

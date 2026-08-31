# `:adminApp`

> The Compose/Wasm admin panel, to be published at **odoapp.in/admin**. Epic #363,
> planned in `docs/ADMIN_PANEL_PLAN.md`.

- **Package:** `com.hopcape.odo.web.admin`
- **Gradle:** `:adminApp` · accessor `projects.adminApp`
- **Targets:** `wasmJs` only, like `:webApp`.
- **Shares:** `:webCore` — PostgREST, the Supabase session, the Firebase exchange,
  the token store, and the presentation primitives.

---

## 1. Why it is its own app and not a route in `:webApp`

Kotlin/Wasm has no code splitting, so everything linked into a bundle is
downloaded by everybody who opens it. `:webApp` is a search-traffic surface
already shipping ~13 MB to anonymous readers; putting a user-management panel in
it would mean every reader downloading the code that edits entitlements. Two apps
means the panel's code never reaches a reader, and when the CMS moves out of the
blog in #370 that bundle gets *smaller*.

The two are still one deployment target — the same Firebase Hosting site — which is
why the bundle is named `odo-admin.js` rather than sharing `odo-blog.js`.

## 2. What is here

```
adminApp/src/
├─ commonMain/kotlin/com/hopcape/odo/web/admin/
│  ├─ AdminApp.kt              the gate + the route guard
│  ├─ domain/Permission.kt     the vocabulary, mirroring admin_role_permissions
│  ├─ domain/AdminSession.kt   who is signed in + the auth port
│  ├─ infrastructure/          Firebase → admin-session → my_admin_identity()
│  ├─ presentation/            SessionViewModel, SignInViewModel, error → copy
│  ├─ routing/                 AdminRoute, Routes, Router, Landing (who sees what)
│  └─ ui/                      AdminTheme, AdminShell, SignInScreen, section screens
├─ commonMain/composeResources/values/strings.xml   `ad_` prefixed
├─ commonTest/…/                RoutesTest (URL round trip), LandingTest (who sees what)
└─ wasmJsMain/                 Main.kt, BrowserRouter, index.html
```

### Routes

Every section is a real address, so a reload lands where you were.

| Path | Route | Permission |
| --- | --- | --- |
| `/admin` | `SignIn` | — |
| `/admin/vehicles` | `Vehicles` | `catalog.vehicles.write` |
| `/admin/cities` | `Cities` | `catalog.cities.write` |
| `/admin/fairness` | `Fairness` | `fairness.write` |
| `/admin/users` | `Users` | `users.read` |
| `/admin/blog` | `Blog` | `blog.write` |
| `/admin/audit` | `Audit` | `audit.read` |
| `/admin/staff` | `Staff` | `admin.roles.write` |
| anything else | `NotFound` | — |

Nothing is nested, and `/users/42` is a 404 rather than resolving to `/users` —
otherwise a link that was never real would look like it worked.

**The permission column is what the nav draws, not what protects the data.** Every
table is behind an RLS policy calling `admin_has()`. A browser that lied about its
permissions would draw itself a menu whose every button fails.

## 3. The shell, and the three checks

A rail on the left listing only the sections this session may open, content on the
right. Where somebody lands after signing in is the first section their role
actually covers — not a fixed home page, because a support admin holds
`users.read` and nothing else, and a fixed landing greeted them with "no access".

The same permission is checked three times, and only the last one matters:

1. **The rail** hides sections this role cannot open. A courtesy.
2. **The route guard** refuses one reached by typing its URL, and says the server
   would refuse it too. Also a courtesy, and it is what stops a blank page being
   mistaken for a broken one.
3. **RLS** refuses the data. This is the control. `admin_has()` runs inside every
   policy, at the moment of the write, where a browser cannot reach it.

An account that is staff and holds no roles is a real state — `seed_admin.sql`
inserts the row and grants the role in two statements — so it gets its own screen
rather than a blank page.

## 4. Sections

**Cities** (#367) is built: the "my city isn't listed" queue above the catalog on
one page, because a reviewer approving "Srinagar" needs to see whether the catalog
already has it under another spelling.

Two things about it are worth knowing before touching either catalog:

- **Approving is an edit, not a click.** `cities.state` is NOT NULL and the app
  only ever asks an owner for a name, so a reviewer has to supply the state. The
  state, the tier and the status go in one PATCH: the promote trigger fires on
  that update and reads the row as it then stands, so writing the status first
  would fire it against a row whose state is still null — which its own guard
  turns into a silent no-op.
- **Retiring is `is_active`, not a delete.** There is no delete policy on
  `cities`, and PostgREST reports `204` for a DELETE that RLS matched no rows
  for — success, with the row still there. Anything that needs a row gone needs a
  policy, not a retry.

The rest are placeholders: a route, a permission and a nav item, no screen. #366,
#368, #369 and #370 replace them one at a time. They exist now because the nav's
shape is what the permission model is tested against.

The hosting config is in place (§6); what is left before a first deploy is the
prod server-side rollout, not code.

## 5. Running it

```sh
./gradlew :adminApp:wasmJsBrowserDevelopmentRun   # http://localhost:8080/admin/
./gradlew :adminApp:wasmJsBrowserDistribution     # production bundle
```

The admin schema currently exists **only on the dev Supabase project**, while a
default build points at production. To run against dev without editing
`local.properties`:

```sh
SUPABASE_ENV=dev FIREBASE_WEB_API_KEY_DEV=<dev project's web api key> \
  ./gradlew :adminApp:wasmJsBrowserDevelopmentRun
```

Both halves matter. `SUPABASE_ENV=dev` alone pairs the dev database with the
production Firebase project, and the mismatch surfaces as "wrong password" rather
than as a configuration problem — which is why the dev Firebase key has no
fallback while the production one does.

## 6. Publishing

Not deployed yet, and deliberately: the admin schema exists only on the **dev**
Supabase project, so a deploy today would put a working sign-in page at
`odoapp.in/admin` that correctly refuses everybody. Before the first deploy, prod
needs `20260831120000_admin_rbac.sql` and `20260831130000_admin_session_support.sql`
applied, `admin-session` deployed, and `seed_admin.sql` run — see
`supabase/README.md`, including the note about prod's unrepaired migration history.

When it is time, it is the blog's runbook with the paths changed. It goes to the
same `odo-landing` Firebase site, and the blog's own deploy (`rm -rf
landing/public/blog`) does not touch `public/admin`, so the two are independent.

```sh
./gradlew :adminApp:wasmJsBrowserDistribution
rm -rf landing/public/admin && mkdir -p landing/public/admin
cp -R adminApp/build/dist/wasmJs/productionExecutable/. landing/public/admin/
rm -f landing/public/admin/*.map landing/public/admin/*.LICENSE.txt
cd landing && firebase deploy --only hosting --project odo-mobile-ba9aa
```

Source maps are dropped on the way: another 1.5 MB, and they hand out the whole
source tree — which matters more here than on the blog.

Four things in `landing/firebase.json` make it work, and each has already bitten
once on `/blog`:

- **Two rewrites**, `/admin` and `/admin/**`. Firebase matches literally and the
  second does not cover the first. Static files still win, so
  `/admin/odo-admin.js` serves the bundle and only unmatched paths fall through.
- **`'wasm-unsafe-eval'`** in `script-src`, or the bundle never starts and the page
  sits on its boot placeholder.
- **`base-uri 'self'`**, not `'none'`. The page carries `<base href="/admin/">`,
  which is what makes a two-segment path find its own resources; `'none'` drops the
  tag and the panel renders with none of its labels.
- **Header sources must not overlap.** The `**/*.png` and `**/*.svg` rules win over
  a specific source whichever order they appear in, so nothing under `/admin` may
  be a png or an svg served from this site.

`X-Robots-Tag: noindex, nofollow` is set on both sources, and `index.html` carries
the same instruction as a `<meta name="robots">` — the header is the half a
misordered rule can lose.

The bare `/admin` document is `no-store` so a deploy is picked up immediately at
the address people bookmark. Everything under it is `max-age=300`, because
`/admin/**` also matches 12.5 MB of content-hashed `.wasm` that would otherwise be
re-downloaded on every load.

## 7. Configuration, and the trap it avoids

Credentials come from `:webCore`'s generated `BuildWebConfig`: `SUPABASE_URL`,
`SUPABASE_ANON_KEY` and `FIREBASE_WEB_API_KEY`, read from `local.properties` or the
environment. The Firebase web API key is a public client identifier — it names a
project and authorises nothing; who may sign in is decided by the `admin_users`
table, server-side.

`BuildWebConfig.isConfigured` is checked in `adminModule`, and a build missing any
of the three gets a repository that refuses sign-in with "email sign-in is switched
off" rather than one that fails at the first request. This codebase has shipped
unconfigured builds three times, each time surfacing as a runtime error about the
request instead of about the missing configuration.

## 8. Signing in

Firebase email/password proves identity; `admin-session` decides whether that
address is staff and mints a Supabase session; `my_admin_identity()` says what they
may do. See `supabase/README.md` for the server half and the three things that must
be true before anyone can get in.

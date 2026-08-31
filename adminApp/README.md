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

## 2. What is here (S4)

```
adminApp/src/
├─ commonMain/kotlin/com/hopcape/odo/web/admin/
│  ├─ AdminApp.kt              the shell: signed-out → sign-in, signed-in → placeholder
│  ├─ domain/Permission.kt     the vocabulary, mirroring admin_role_permissions
│  ├─ domain/AdminSession.kt   who is signed in + the auth port
│  ├─ infrastructure/          Firebase → admin-session → my_admin_identity()
│  ├─ presentation/            SessionViewModel, SignInViewModel, error → copy
│  ├─ routing/                 AdminRoute, Routes, Router
│  └─ ui/                      AdminTheme, SignInScreen
├─ commonMain/composeResources/values/strings.xml   `ad_` prefixed
├─ commonTest/…/RoutesTest.kt  the URL round trip + the permission invariants
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

## 3. Not built yet

S4 is the module, the routes and sign-in. The signed-in screen is a placeholder
that prints the identity — it exists to prove the whole chain in a browser. **S5**
adds the role-gated nav, the route guard and a screen per section; **S6** adds the
Firebase Hosting rewrites and the `noindex` headers, without which `/admin/users`
is a 404 on reload in production.

## 4. Running it

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

## 5. Configuration, and the trap it avoids

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

## 6. Signing in

Firebase email/password proves identity; `admin-session` decides whether that
address is staff and mints a Supabase session; `my_admin_identity()` says what they
may do. See `supabase/README.md` for the server half and the three things that must
be true before anyone can get in.

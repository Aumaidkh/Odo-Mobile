# `:webApp`

> The Compose/Wasm browser app behind **odoapp.in/blog**. A third app module next to
> `:androidApp` and `:iosApp` — its own entry point, its own target, its own
> deployment — rather than a library the phone apps share.

- **Package:** `com.hopcape.odo.web.blog`
- **Gradle:** `:webApp` · accessor `projects.webApp`
- **Targets:** `wasmJs` (browser) only. No Android, no iOS.
- **Stack:** Compose Multiplatform `1.11.1` · Kotlin/Wasm `2.4.0` · kotlinx-browser `0.3`

---

## 1. What is here

The Wasm target, the host page, and the route layer. Every page in the design has a
route and a branch in the shell; each branch draws a placeholder, because the UI is
a separate pass.

```
webApp/
├─ build.gradle.kts
└─ src/
   ├─ commonMain/kotlin/com/hopcape/odo/web/blog/
   │  ├─ BlogApp.kt              the shell — one exhaustive branch per page
   │  └─ routing/
   │     ├─ BlogRoute.kt         every page, as a type
   │     ├─ Routes.kt            the only place a URL becomes a route and back
   │     └─ Router.kt            what a screen may know + an off-browser one
   ├─ commonTest/…/RoutesTest.kt the URL round trip
   └─ wasmJsMain/
      ├─ kotlin/…/Main.kt                entry point
      ├─ kotlin/…/routing/BrowserRouter.kt   history, popstate, the address bar
      └─ resources/index.html            host page, loads odo-blog.js
```

### Routes

Public — dark, and the only part a crawler sees:

| Path | Route | Design frame |
| --- | --- | --- |
| `/blog` | `Public.Index` | INDEX |
| `/blog/<slug>` | `Public.Article` | ARTICLE |
| `/blog/category/<slug>` | `Public.Category` | CATEGORY |
| `/blog/author/<slug>` | `Public.Author` | AUTHOR |
| `/blog/search?q=` | `Public.Search` | SEARCH |
| anything else | `Public.NotFound` | 404 |

Admin — light, signed in, linked from nowhere public:

| Path | Route | Design frame |
| --- | --- | --- |
| `/blog/admin` | `Admin.SignIn` | LOGIN |
| `/blog/admin/posts` | `Admin.Posts` | POSTS |
| `/blog/admin/posts/new`, `/blog/admin/posts/<id>` | `Admin.Editor` | EDITOR |
| `/blog/admin/media` | `Admin.Media` | MEDIA |
| `/blog/admin/analytics` | `Admin.Analytics` | ANALYTICS |
| `/blog/admin/settings` | `Admin.Settings` | — |

Publish/SEO, slug conflict, insert image, unsaved changes, published and unpublish
are **overlays on the editor**, not routes: none of them is a place to land, and
giving them URLs would put a reader on a confirm dialog with no post behind it.

Post URLs are flat, so `category`, `author`, `search` and `admin` cannot be post
slugs — `RESERVED_SLUGS` names them, and the content pipeline has to reject them.

### What is not here any more

The PostgREST client, the Supabase session and its Firebase exchange, the token
store, and the presentation primitives (`FormField`, `Loadable`, `Submission`,
`UiText`, `CollectEffects`, `RouteScope`) now live in **`:webCore`**, shared with
the admin panel — see `webCore/README.md`. `BuildBlogConfig` went with them and is
`BuildWebConfig`; the Gradle task that generates it is `:webCore:generateWebConfig`.

What stayed is everything that is about a blog: the routes, the domain, the
repositories, the screens, the theme, and the `WebError` → copy mapping in
`presentation/Loading.kt`.

## 2. Why it is not `odo.kmp.library`

That convention exists to hand a shared module the Android and iOS targets and a
static framework. This module has none of them, so it applies the Kotlin
Multiplatform plugin directly and adds the one target it wants. It does keep
`odo.compose.multiplatform`, which is what puts the Compose UI surface into
`commonMain`; the Wasm source set inherits from there.

## 3. Why Wasm and not static HTML

The blog UI is written in Compose because that is what the rest of Odo is written
in. One cost is worth writing down rather than rediscovering: **Compose renders to a
canvas, so the page a crawler downloads contains no readable text.** A blog is a
search-traffic surface, so whatever ships here has to carry its own answer to that —
the intended one is a `<noscript>` index generated into `index.html` at build time,
holding the same titles and summaries the app draws.

## 4. Running it

```sh
./gradlew :webApp:wasmJsBrowserDevelopmentRun   # dev server, hot reload
./gradlew :webApp:wasmJsBrowserDistribution     # production bundle
```

The production bundle lands in `webApp/build/dist/wasmJs/productionExecutable/`.
Kotlin/Wasm needs a browser that supports the garbage-collection proposal —
Chrome 119+, Firefox 120+, Safari 18.2+.

## 5. The database

Postgres, through PostgREST, in the same Supabase project the app uses. Tables are
prefixed `blog_` and none of them references a car, a profile or an owner — the
only join between the two worlds is an author whose email matches an
`auth.users` account.

```
supabase/migrations/20260820120000_blog.sql         schema, RLS, the two RPCs
supabase/migrations/20260820120100_blog_media_bucket.sql   storage for screenshots
supabase/functions/blog-session/                    Firebase token → Supabase session
supabase/seed_blog.sql                              the design's posts, once
supabase/check-blog.sh                              asks the database what a stranger can see
```

### Applying it

Paste `supabase/blog_schema.sql` into the SQL editor and run it. It is the two
migration files joined in order — the storage half calls `is_blog_author()`,
which the first half defines — and every statement is idempotent, so running it
twice is not a problem.

Then `supabase/seed_blog.sql`, once, if you want the design's posts rather than an
empty blog.

The edge function is the one part with no SQL editor. It needs the CLI:

```sh
supabase secrets set --project-ref <ref> \
  FIREBASE_PROJECT_ID=odo-mobile-ba9aa BLOG_AUTHOR_EMAILS=you@example.com
supabase functions deploy blog-session --no-verify-jwt --project-ref <ref>
```

Then, always: `sh supabase/check-blog.sh`. It asks the project what a stranger can
see and do, and fails if a table that should be shut answers back. Until it
passes, nothing here has actually been verified — the Kotlin tests are mocked
HTTP, which is the same blind spot that once hid a sign-in bug for a day.

If you would rather the CLI ran the migrations: `supabase db push` takes
`--linked` or `--db-url`, **not** `--project-ref`, and this project already
carries migrations from before the blog existed — run `--dry-run` first and read
what it says it will replay.

**Reads are a policy, not a filter.** Every public call runs as `anon`, and the
only rows that role can see are published ones. No query in the Kotlin says
`status=eq.published`; that rule lives in one place, and a second copy is the one
that drifts.

**Writes need a claim.** `is_blog_author()` reads `blog_author` out of the JWT's
`app_metadata`, which only `blog-session` stamps. An ordinary app account — every
one of which signs in by phone — can never carry it.

Three things are worth knowing before changing a query:

- The body is `jsonb`, holding the same block list the app models. Nothing wants
  to query inside a paragraph; what gets queried is the title and the dek, which
  have a generated `tsvector` and a GIN index behind them. Search is
  `websearch_to_tsquery`, not `ilike`.
- Author and category come back **embedded** (`select=*,author:blog_authors(*)`),
  so a grid of twelve bylines is one round trip rather than thirteen.
- Payloads are written with `explicitNulls`. PostgREST reads an absent key as
  "leave this column alone", so a field cleared in the CMS would otherwise keep
  its old value.

Credentials come from `local.properties` — the same `supabase.url` and
`supabase.anonKey` the app reads. Without them `blogModule` keeps the sample
repositories, so a clone with no credentials still builds and runs.

## 6. Signing in

The CMS authenticates against Firebase Auth over its **REST API**, not the
Firebase JS SDK. Kotlin/Wasm has no `@JsModule`, so the SDK would need an npm
dependency, a hand-written JavaScript shim and Kotlin externals to reach it;
password sign-in is three endpoints and no bundling. What the SDK would have done
for free — refreshing the token, remembering the session — is two small pieces of
`infrastructure/FirebaseAuthRepository.kt`.

Three steps, each somebody else's job: Firebase says the password is right,
`blog-session` trades that token for a Supabase session carrying a `blog_author`
claim, and Postgres says who that author is. After the first sign-in only the
last two matter — what survives a reload is the Supabase refresh token, so coming
back never touches Firebase.

**The author list is server-side.** `BLOG_AUTHOR_EMAILS` in the function's
environment decides who may publish; an address not on it never receives a
session, so RLS never sees the claim and every table stays shut. The client has no
say, which is the point — a browser check is a courtesy, not a control.

Email/Password has to be enabled on the Firebase project, and the screen says so
plainly when it is not rather than pretending the password was wrong.

Two things that do not apply here but usually do: Firebase Auth's
**authorized-domains** list governs the SDK's browser flows (Google, phone), not
this one, so `odoapp.in` needs no entry for password sign-in. An **API-key
referrer restriction** does apply, and has to allow both
`identitytoolkit.googleapis.com` and `securetoken.googleapis.com` — refreshing
goes to a different host from signing in.

## 7. Publishing

Live at **https://odoapp.in/blog**, on the `odo-landing` Firebase Hosting site —
the same site as the landing page and the legal documents.

```sh
./gradlew :webApp:wasmJsBrowserDistribution
rm -rf landing/public/blog && mkdir -p landing/public/blog
cp -R webApp/build/dist/wasmJs/productionExecutable/. landing/public/blog/
rm -f landing/public/blog/*.map landing/public/blog/*.LICENSE.txt
cd landing && firebase deploy --only hosting --project odo-mobile-ba9aa
```

Source maps are dropped on the way: another 1.4 MB, and they hand out the whole
source tree.

Three things in `landing/firebase.json` make it work, and each of them has bitten
once:

- **Two rewrites**, `/blog` and `/blog/**`. Firebase matches literally and the
  second does not cover the first. Static files still win, so `/blog/odo-blog.js`
  serves the bundle and only unmatched paths fall through to the app.
- **`'wasm-unsafe-eval'`** in `script-src`. Without it the bundle never starts and
  the page sits on its boot placeholder.
- **`base-uri 'self'`**, not `'none'`. The legal pages use `'none'` and are right
  to; this page carries `<base href="/blog/">`, which is what makes a
  three-segment path find its own resources. `'none'` drops the tag and the page
  renders its data with none of its labels.

Header sources must not overlap — a `**` rule wins over a specific one whichever
order they appear in. The `**/*.png` and `**/*.svg` entries already there are why
nothing under `/blog` may be a png or an svg served from this site; the post
images come from Supabase storage instead.

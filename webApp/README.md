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

## 5. Signing in

The CMS authenticates against Firebase Auth over its **REST API**, not the
Firebase JS SDK. Kotlin/Wasm has no `@JsModule`, so the SDK would need an npm
dependency, a hand-written JavaScript shim and Kotlin externals to reach it;
password sign-in is three endpoints and no bundling. What the SDK would have done
for free — refreshing the token, remembering the session — is two small pieces of
`infrastructure/FirebaseAuthRepository.kt`.

Two things have to be true before anybody can sign in, and neither is code:

1. **Email/Password has to be enabled** on the Firebase project
   (`odo-mobile-ba9aa`). It is off as this is written — the app signs users in by
   phone — and the screen says so rather than pretending the password was wrong.
2. **`FirebaseConfig.AUTHOR_EMAILS` has to name the authors.** It is empty, which
   lets nobody in. That is deliberate: every account in this Firebase project is
   an app user, so an empty list meaning "everybody" would open the CMS to anyone
   who ever registered.

That list is a gate on the screen, **not security** — anybody with a developer
console gets past it. The real check is a custom claim minted server-side and
rules on whatever stores the posts, and it belongs with the backend that does not
exist yet.

Two things that do not apply here but usually do: Firebase Auth's
**authorized-domains** list governs the SDK's browser flows (Google, phone), not
this one, so `odoapp.in` needs no entry for password sign-in. An **API-key
referrer restriction** does apply, and has to allow both
`identitytoolkit.googleapis.com` and `securetoken.googleapis.com` — refreshing
goes to a different host from signing in.

## 6. Publishing — not wired yet

`odoapp.in` is the Firebase Hosting site `odo-landing`, configured in
`landing/firebase.json`; `/blog` is free there today. Publishing this module means
copying the distribution into `landing/public/blog/`, adding a rewrite so
`/blog/**` serves the app's own `index.html`, and giving those paths a
`'wasm-unsafe-eval'` script policy — the site's existing per-path CSP rules would
otherwise block the bundle from starting. `connect-src` has to allow
`https://identitytoolkit.googleapis.com` and `https://securetoken.googleapis.com`
as well, or the CMS cannot sign anybody in.

Both hosting configs follow one rule that is easy to break: **header sources must
not overlap.** A `**` rule wins over a specific one whichever order they appear in,
which is how the legal pages once lost the script policy the deletion page needs.

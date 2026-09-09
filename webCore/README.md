# `:webCore`

> What both browser apps are built out of. Not an app — it has no entry point and
> no `binaries.executable()`. `:webApp` (the blog, at `odoapp.in/blog`) and
> `:adminApp` (the admin panel, at `odoapp.in/admin`) are the things with those.

- **Package:** `com.hopcape.odo.web.core`
- **Gradle:** `:webCore` · accessor `projects.webCore`
- **Targets:** `wasmJs` only. No Android, no iOS — same reason `:webApp` has none.

---

## 1. Why it exists

The admin panel needs the same four things the blog already had: a PostgREST
client, a Supabase session obtained by trading a Firebase token, somewhere to keep
a refresh token, and the small set of state types every ViewModel here is written
against. Copying them would mean two versions of the sign-in exchange, which is
the one piece of this codebase where a divergence is a security bug rather than an
inconsistency.

It is also what keeps the reader-facing bundle from growing. `:webApp` is a
search-traffic surface that already ships ~13 MB of Wasm to anonymous readers, and
Kotlin/Wasm has no code splitting — anything linked into it is downloaded by
everybody. Putting the admin panel in its own module means its code never reaches
a reader, and when the CMS moves out of `:webApp` in #370 that bundle gets
*smaller*.

## 2. What is here

```
webCore/src/
├─ commonMain/kotlin/com/hopcape/odo/web/core/
│  ├─ domain/WebError.kt              every failure either app may draw
│  ├─ infrastructure/
│  │  ├─ supabase/Postgrest.kt        the REST client
│  │  ├─ supabase/Query.kt            encoded() / jsonEscaped() for query strings
│  │  └─ firebase/FirebaseSignIn.kt   Firebase Auth over its REST API
│  ├─ platform/                       TokenStore, Browser, UploadRequest (expect)
│  └─ presentation/
│     ├─ Effects.kt                   CollectEffects, RouteScope, route ViewModels
│     └─ state/                       FormField, Loadable, Submission, UiText
└─ wasmJsMain/…/platform/             the actuals: localStorage, window, file input
```

`BuildWebConfig` is generated here too, by `generateWebConfig`, from the same
`supabase.url` / `supabase.anonKey` in `local.properties` that the phone app reads.
Both web apps talk to the same project, so the task that reads those values belongs
to the module they share rather than being copied into each of them. A checkout
with no credentials generates blanks, and each app falls back to its samples.

## 3. What is deliberately **not** here

- **The theme.** `BlogTheme` / `BlogThemeTokens` are still in `:webApp`. Moving
  them means renaming a token object referenced by every screen in the blog, which
  buys nothing until `:adminApp` actually draws the CMS — and in #370 those screens
  move anyway, so the rename happens once, in the slice that already touches them.
- **`loadInto` / `asUiText` / `isRetryable`.** They map a `WebError` onto copy, and
  copy is per-app: the blog says "you are not an author", the panel says "you are
  not staff". Sharing the mapping would mean sharing the strings.
- **An HTTP engine.** Each app declares its own `ktor-client-js`, so nothing here
  decides what an app talks over.

## 4. Two things that bite

**`WebError` members are named for what happened, not for who it happened to.**
`NotPermitted` is the 403 a session function returns — "not an author" to the blog,
"not staff" to the panel. Do not add a member that names one app's concept.

**Kotlin does not smart-cast a public property across a module boundary.** Code
that used to do `if (e is SignInRejected) … e.triesLeft <= 0` stopped compiling
when `WebError` moved here; the fix is to read the property into a local first.
Expect that anywhere a sealed member's field is used after an `is` check.

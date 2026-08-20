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

The module skeleton: the Wasm target, the host page, and a `main()` that mounts
Compose over `<body>`. What it draws today is a wordmark and nothing else — the blog
UI is a separate commit.

```
webApp/
├─ build.gradle.kts
└─ src/wasmJsMain/
   ├─ kotlin/com/hopcape/odo/web/blog/Main.kt   entry point + placeholder
   └─ resources/index.html                      host page, loads odo-blog.js
```

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

## 5. Publishing — not wired yet

`odoapp.in` is the Firebase Hosting site `odo-landing`, configured in
`landing/firebase.json`; `/blog` is free there today. Publishing this module means
copying the distribution into `landing/public/blog/`, adding a rewrite so
`/blog/**` serves the app's own `index.html`, and giving those paths a
`'wasm-unsafe-eval'` script policy — the site's existing per-path CSP rules would
otherwise block the bundle from starting.

Both hosting configs follow one rule that is easy to break: **header sources must
not overlap.** A `**` rule wins over a specific one whichever order they appear in,
which is how the legal pages once lost the script policy the deletion page needs.

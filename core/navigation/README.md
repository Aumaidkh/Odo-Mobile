# `:core:navigation`

> The single navigation backbone for Odo. Features describe **where to go**; this
> module decides **how to get there**. Built on **Navigation 3** (user-owned back
> stack) with a **command-bus** on top so navigation works from a ViewModel without
> ever touching the back stack or another feature.

- **Package:** `com.hopcape.odo.core.navigation`
- **Gradle:** `:core:navigation` · accessor `projects.core.navigation`
- **Targets:** Android + iOS (KMP, `commonMain`). MVP ships Android.
- **Stack:** Navigation 3 `1.1.1` (JetBrains CMP port) · Koin DI · Kotlin coroutines `SharedFlow`

---

## 1. TL;DR — the 30-second mental model

```
 Feature ViewModel                          :app (host)                         Nav 3
 ─────────────────                          ───────────                         ─────
 navigationManager.navigateTo(             ┌─ OdoNavHost ──────────────┐
   OdoDestination.CarDetail("MH-12") )     │  collect commands         │
        │                                   │        │                  │
        ▼                                   │        ▼                  │
 NavigationManager ──SharedFlow<Command>──►│  Navigator.execute(cmd)   │   (internal)
                                            │        │ add / removeAt   │
                                            │        ▼                  │
                                            │  NavBackStack  ───────────┼─► NavDisplay
                                            │  (observable List<NavKey>)│      │
                                            └───────────────────────────┘      ▼
                                                                         entry<CarDetail> {
                                                                           CarDetailScreen(it.carId)
                                                                         }
```

A feature only ever calls **`NavigationManager`** and references **`OdoDestination`**.
Everything to the right of the bus (`Navigator`, the back stack, `NavDisplay`) is
**host-only** and a feature never imports it.

---

## 2. Why this design

We adapted a well-known enterprise Android pattern (a `NavigationManager` command bus +
per-feature graph contribution via DI multibinding) to **Odo's actual stack**:

| Classic enterprise pattern (Hilt + NavController) | Odo (Koin + Nav 3 + KMP) |
| --- | --- |
| `NavigationDestination(route: String)` (stringly-typed) | `OdoDestination : NavKey` — **typed**, args are compile-checked, no `"car/{carId}"` templates |
| `NavigationManager` + `SharedFlow<Command>` | same — multiplatform `commonMain` |
| `NavigationManagerImpl` + Hilt `@Binds @Singleton` | `DefaultNavigationManager` + **Koin** `single` |
| `NavGraphProvider.register(navController)` | `FeatureEntryProvider` → Nav 3 `entry<>` on `EntryProviderScope` |
| Hilt `@IntoSet` multibinding | Koin `getAll<FeatureEntryProvider>()` |
| `:app` collector drives a `NavController` | `:app` collector drives `Navigator` (Nav 3 back stack) |

**Why a command bus at all?** In KMP + Compose + Koin, a ViewModel is a plain object —
it can't hold a composition-scoped navigator. The `SharedFlow` bus lets any ViewModel
emit an intent; the host (the only place that owns the back stack) executes it. This is
what keeps features decoupled and unit-testable.

---

## 3. File map (the whole module, one line each)

Every file earns its place — there is no dead code. Public API is split into
**feature-facing** (safe to use anywhere) and **host-facing** (only `:app` wires these).

| File | Layer | Responsibility |
| --- | --- | --- |
| `OdoDestination.kt` | feature-facing | All destinations as typed `NavKey`s; `TopLevel` marks bottom-nav roots; `topLevel` lists them. **Add screens here.** |
| `NavigationCommand.kt` | feature-facing | The intents: `NavigateTo` (with `popUpTo`/`inclusive`/`singleTop`), `Back`, `BackTo`, `ToRoot`. |
| `NavigationManager.kt` | feature-facing | The bus: `commands` flow + `navigate()`; shorthands `navigateTo()` / `back()`; `NavigationManager()` factory; `LocalNavigationManager`. |
| `FeatureEntryProvider.kt` | feature-facing | A feature's `entry<>` registrations (the `NavGraphProvider` analog). |
| `NavigationModule.kt` | wiring | Koin `coreNavigationModule` exposing `NavigationManager` as a `single`. |
| `Navigator.kt` | host-facing | Low-level back-stack ops: `navigate` / `goBack` / `popUpTo` / `backStack` / `canGoBack`. |
| `RememberNavigator.kt` | host-facing | `rememberNavigator(start)` — creates the `Navigator` for the host. |
| `OdoNavHost.kt` | host-facing | The host composable: collects the bus and renders `NavDisplay`. |
| `OdoNavigator.kt` | internal | `Navigator` impl over a Nav 3 `NavBackStack`. |
| `NavigatorCommandExecutor.kt` | internal | `Navigator.execute(command)` — the **only** place a command touches the back stack. |
| `NavigatorCommandExecutorTest.kt` | test | Locks the command → back-stack semantics (no Compose needed). |

---

## 4. Public API surface

### A feature may use **only** these
- `OdoDestination` (and `OdoDestination.TopLevel`, `OdoDestination.topLevel`)
- `NavigationManager` + `navigate()` / `navigateTo()` / `back()`  (inject via Koin)
- `NavigationCommand`
- `FeatureEntryProvider`
- `LocalNavigationManager` (only for a composable with no ViewModel; prefer the ViewModel)

### Only `:app` (the host) uses these
- `rememberNavigator(startDestination)`
- `OdoNavHost(...)`
- `Navigator` (passed to the host; e.g. read `backStack` for bottom-bar selection)
- `coreNavigationModule` (registered at `startKoin`)

> 🔒 **Boundary rule:** if a `:feature:*` module imports `Navigator`, `OdoNavHost`,
> `rememberNavigator`, or `androidx.navigation3.*` directly, that's a code smell — route
> it through `NavigationManager` / `OdoDestination` instead.

---

## 5. Recipes

### 5.1 Add a new destination
Add a subtype in `OdoDestination`. Carry arguments as constructor params — no string
templates, no `navArgument` parsing.

```kotlin
// OdoDestination.kt
data class Passport(val carId: String) : OdoDestination          // nested
data object Documents : TopLevel { override val label = "Docs" }  // bottom-nav root
// remember to add new TopLevel entries to `topLevel`
```

### 5.2 Register a screen (inside a feature module)
Each feature exposes one `FeatureEntryProvider` and registers it in its Koin module.

```kotlin
// :feature:passport
class PassportEntryProvider : FeatureEntryProvider {
    override fun EntryProviderScope<NavKey>.registerEntries() {
        entry<OdoDestination.Passport> { key -> PassportScreen(carId = key.carId) }
    }
}

// feature's Koin module — the @IntoSet analog
val passportModule = module {
    single { PassportEntryProvider() } bind FeatureEntryProvider::class
}
```

That's the whole checklist to add a feature to navigation: **one provider + one Koin
binding.** No edits to `:app`, no other feature touched.

### 5.3 Navigate from a ViewModel
```kotlin
class GarageViewModel(
    private val navigationManager: NavigationManager,   // Koin injects the single
) : ViewModel() {
    fun onCarClicked(carId: String) =
        navigationManager.navigateTo(OdoDestination.CarDetail(carId))

    fun onBack() = navigationManager.back()
}
```

### 5.4 Bottom-nav tab switch (reselect-safe)
```kotlin
navigationManager.navigateTo(
    destination = OdoDestination.Garage.Home,
    popUpTo = OdoDestination.Home,   // reset to the root first…
    singleTop = true,                // …and don't stack duplicates
)
```

### 5.5 Login → Home, clearing the auth stack
No special `replaceAll` needed — `popUpTo` + `inclusive` does it:

```kotlin
navigationManager.navigateTo(
    destination = OdoDestination.Home,
    popUpTo = OdoDestination.AuthLogin,
    inclusive = true,                // pop the auth screens too
)
```

### 5.6 Back variants
```kotlin
navigationManager.navigate(NavigationCommand.Back)                       // pop one
navigationManager.navigate(NavigationCommand.BackTo(OdoDestination.Garage.Home))
navigationManager.navigate(NavigationCommand.ToRoot)                     // pop to start
```

---

## 6. Host wiring (`:app`)

Done **once**. The host owns the `Navigator`, collects every feature's entries from Koin,
and renders the graph.

```kotlin
@Composable
fun OdoApp() {
    val navigationManager: NavigationManager = koinInject()
    val entryProviders: List<FeatureEntryProvider> = remember { getKoin().getAll() }
    val navigator = rememberNavigator(OdoDestination.Home)

    CompositionLocalProvider(LocalNavigationManager provides navigationManager) {
        Scaffold(bottomBar = { OdoBottomBar(navigator) }) { padding ->
            OdoNavHost(
                navigator = navigator,
                navigationManager = navigationManager,
                entryProviders = entryProviders,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

// At startup:
startKoin { modules(coreNavigationModule, /* feature modules… */) }
```

> A runnable reference wiring lives in `:shared` → `App.kt` + `NavigationDemo.kt`. It
> uses `remember { NavigationManager() }` instead of Koin only so the demo runs before
> DI is bootstrapped — production uses `coreNavigationModule` as above.

---

## 7. How a command becomes a screen change

`NavigatorCommandExecutor.kt` is the entire translation layer. Semantics:

| Command | Effect on the back stack |
| --- | --- |
| `NavigateTo(dest, popUpTo, inclusive, singleTop)` | optional `popUpTo` first; then push `dest` unless `singleTop` and it's already on top |
| `Back` | pop the top (never the root) |
| `BackTo(dest, inclusive)` | pop down to `dest` (also pop `dest` if `inclusive`) |
| `ToRoot` | pop everything back to the start destination |

The host wires Nav 3's system/predictive **back gesture** to `Navigator.goBack()`, so OS
back and `NavigationCommand.Back` behave identically.

---

## 8. Tech notes & gotchas

- **Nav 3 artifact:** depend on `org.jetbrains.androidx.navigation3:navigation3-ui` only.
  The JetBrains CMP port does **not** publish a separate `navigation3-runtime` under that
  group (that's the AndroidX coordinate); the runtime/common types arrive transitively.
- **`NavBackStack`** is an observable `MutableList<NavKey>` — that's what `NavDisplay`
  renders and what `Navigator` mutates.
- **`entry<T>`** is a *member* of `EntryProviderScope`, not a top-level function — call it
  inside `registerEntries()`; do **not** add an `import …runtime.entry`.
- **State restoration:** the back stack is held in `remember`, so it survives
  recomposition but **not** process death / config-change recreation. To add full
  persistence, make every `OdoDestination` `@Serializable`, build a `SavedStateConfiguration`
  with the polymorphic registrations, and swap `rememberNavigator` to use Nav 3's
  `rememberNavBackStack(configuration, …)`. (commonMain only exposes the
  `SavedStateConfiguration` overload — this is deliberate, not a bug.)
- **Money/precision, RLS, etc.** are unrelated here — this module is UI-routing only and
  holds no domain data.

---

## 9. Testing

Command semantics are covered without Compose in
`NavigatorCommandExecutorTest` (push, `singleTop` guard, `popUpTo` tab-reselect,
back-never-pops-root, `BackTo` inclusive, `ToRoot`).

```bash
./gradlew :core:navigation:testAndroidHostTest   # Android-host unit tests
./gradlew :core:navigation:allTests              # all KMP targets
./gradlew :core:navigation:assemble              # compile + link (incl. iOS framework)
```

When you add a destination/command with non-trivial back-stack behaviour, add a case
here — the back stack is the source of truth and these tests are its contract.

---

## 10. Decisions log (FAQ)

- **Why not pass a `NavController`/`Navigator` down to features?** ViewModels can't hold a
  composition-scoped object cleanly, and it would couple features to the host. The command
  bus removes that dependency and makes navigation unit-testable.
- **Why centralize destinations instead of one per feature?** Cross-feature jumps need a
  shared vocabulary; centralizing typed keys gives that without features importing each
  other. Adding a screen is still a one-line change here.
- **Why typed `NavKey` over string routes?** Compile-time-checked arguments, no template
  parsing, refactor-safe.
- **Where did `replaceAll` go?** Removed — `NavigateTo(popUpTo = root, inclusive = true)`
  expresses the same "clear and go" intent through the bus, so a second mechanism was dead
  weight.

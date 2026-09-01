# `:core:config`

> **Remote config and feature flags.** One declaration per key, in the module that owns
> it; the implementation, the flows, the registry contribution and the Koin wiring are
> generated from it by KSP. Replaces the compile-time `FeatureFlags` consts and the
> hand-maintained key/default maps that used to live in the Firebase adapter.

- **Package:** `com.hopcape.odo.core.config`
- **Gradle:** `:core:config` · accessor `projects.core.config`
- **Processor:** `:core:config:processor` — plain JVM, KSP only, never on a runtime classpath
- **Convention plugin:** `odo.config` · alias `libs.plugins.odo.config`
- **Targets:** Android + iOS (KMP, `commonMain` only)
- **Dependencies:** coroutines, `:core:common` (`BuildInfo`), `:observability:logging`

---

## 1. Adding a key, end to end

Worked example: a kill switch and a tuning number for a **challan** (traffic-fine) feature.

### Step 1 — decide where the group goes

**The rule: the lowest module every consumer already depends on.**

| Who reads it | Where it goes |
|---|---|
| One feature | that feature module — `:feature:challan` |
| Several features | `:core:config` |
| Only the adapter that talks to the backend | that adapter module |

Getting this wrong means a feature module depending on a sibling feature module, which
nothing in this repo does. `auto_odometer_enabled` is the standing example: `:shared`,
`:feature:garage` and `:feature:dashboard` all read it, so it lives in `:core:config`.

**`:core:domain` cannot host a group.** Its own build file says *"NO framework types ever
(no Compose, DI, …)"*, and every generated group emits a Koin module. That is why the
`appstatus` / `legal` / `support` keys are declared in
`:infrastructure:firebase:remoteconfig` — the one module all three of their consumers live
in — while their ports stay in `:core:domain`.

For challan: one feature reads it, so `:feature:challan` owns it.

### Step 2 — apply the convention plugin

```kotlin
// feature/challan/build.gradle.kts
plugins {
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.kmpTest)
    alias(libs.plugins.odo.config)   // <- this line, and nothing else
}
```

That one line applies KSP, puts the processor on `kspCommonMainMetadata`, adds the
generated directory to `commonMain`, adds `:core:config`, applies `odo.koin` (each group
generates a Koin module), and wires the task ordering. Do **not** hand-roll it: the
ordering rule has to cover KSP's own per-compilation tasks as well as the compile tasks,
and getting that wrong fails the build in a task that looks unrelated to config.

### Step 3 — declare the group

```kotlin
// feature/challan/…/ChallanConfig.kt
package com.hopcape.odo.feature.challan

import com.hopcape.odo.core.config.ConfigGroup
import com.hopcape.odo.core.config.Flag
import com.hopcape.odo.core.config.Value

@ConfigGroup("challan")
internal interface ChallanConfig {

    @Flag(
        key = "challan_enabled",
        default = false,
        owner = "growth",
        why = "Kill switch for the whole challan surface",
    )
    val enabled: Boolean

    @Value(
        key = "challan_lookup_cache_hours",
        default = "24",
        owner = "platform",
        why = "How long a fine lookup is reused before asking the source again",
        range = "1..168",
    )
    val cacheHours: Int
}
```

Three things about the shape:

- **`@Flag` is for `Boolean` and nothing else**; its `default` is typed. Everything else is
  `@Value`, whose `default` is written as a **string** and parsed at build time against the
  property's declared type. A Kotlin annotation parameter has one type and `@Value` has to
  carry `Int`, `Long`, `Double` and `String` — and it means one parsing path serves compiled
  defaults and QA overrides alike, since an override is typed in as text.
- **`owner` and `why` are required** and are not decoration. They are what the QA screen
  shows, and what stops a key becoming unattributable a year from now.
- **`range` is numbers only**, `"min..max"`, inclusive. It is checked at build time against
  the default *and* at read time against whatever the console holds.

Supported types: `Boolean`, `Int`, `Long`, `Double`, `String`, and enums whose constant
names are the wire names (matched ignoring case, so a console holding `off` maps to `OFF`).
A JSON-decoded object is not supported — a later addition, not a v1 gap.

### Step 4 — build, and read what was generated

`./gradlew :feature:challan:assemble`

KSP writes one file next to your interface:

```
feature/challan/build/generated/ksp/metadata/commonMain/kotlin/…/ChallanConfigGenerated.kt
```

containing four things:

| Generated | What it is |
|---|---|
| `ChallanConfigImpl` | implements your interface, reading through `ConfigResolver` |
| `ChallanConfigFlows` | one `Flow` per key, for screens that re-render mid-session |
| `ChallanConfigContribution` | the key descriptors, plus a `const` per key name |
| `challanConfigModule` | the Koin module binding all three |

Read it once. If the build fails instead, the processor rejected the declaration and the
message names the offending property — see §4.

### Step 5 — install the Koin module

The generated module has to be installed once. Two options, and the second is preferred:

```kotlin
// Preferred: fold it into the feature's own module, so initKoin's list never grows.
val challanModule = module {
    includes(challanConfigModule)
    // … the rest of the feature
}
```

```kotlin
// Or list it directly in shared/…/di/KoinInit.kt, alongside the others.
```

**Ordering.** Resolution is lazy, so a group registered after `coreConfigModule` still
reaches the registry — `ConfigGraphTest` asserts exactly that across two modules. What
ordering *does* decide is overrides: `firebaseRemoteConfigModule` must stay after
`coreConfigModule`, because it replaces the no-backend `ConfigSource`.

### Step 6 — read it

Inject the interface you wrote. It is the type consumers depend on; the generated impl is
an implementation detail.

```kotlin
internal class ChallanViewModel(
    private val config: ChallanConfig,
) : ViewModel() {

    fun onOpen() {
        if (!config.enabled) return
        …
    }
}
```

```kotlin
// Koin
viewModel { ChallanViewModel(config = get()) }
```

For a screen that should follow a fetch that lands while it is open, inject
`ChallanConfigFlows` instead and collect. Do not call `ConfigRefresher.refresh()` from a
read — the one refresh runs from the process-lifecycle observer in `OdoApplication`, which
fires on cold start and on every return to the foreground.

Outside a class that takes constructor injection — an app-shell composable, a navigation
route — `koinInject<ChallanConfig>()` is the house pattern.

### Step 7 — test it

A test writes its own fake. No backend, no Koin, no build flag to branch on:

```kotlin
// Parameter names deliberately differ from the property names: an object expression
// whose initializer has the same name as the property it initialises is a trap.
private fun config(on: Boolean = true, hours: Int = 24) = object : ChallanConfig {
    override val enabled = on
    override val cacheHours = hours
}

@Test
fun `the surface is closed while the flag is off`() {
    val vm = ChallanViewModel(config = config(enabled = false))
    …
}
```

**This is the point of the whole system.** The old `const` could not be faked, so tests
guarded themselves with `assumeTrue(FeatureFlags…)` and only whichever half the build was
compiled for ever ran. Both paths run now.

### Step 8 — create the key in the Firebase console

Until the key exists in the console, every install answers with the compiled default —
which is correct, and is what a fresh install with no network answers regardless. The
console entry is what makes the switch usable without a release. Key name and type must
match the declaration exactly.

### Step 9 — check it on a device

Debug builds have **Profile → "Config & flags (debug)"**, which lists every registered key with its
value, its `owner`, its `why`, and **which step of the resolution order answered** —
override, remote, or the compiled default. Editable there, with a per-key reset and a
reset-all.

Showing the source is the point. "The flag is off" and "the flag is off *because the
console never set it and this is the compiled default*" are different bugs, and from a
device there is no other way to tell them apart.

---

## 2. How a value is decided

Every read goes through one `ConfigResolver`, in this order:

1. **Local override** — the QA screen. Debug builds only; release binds no store, so this
   step simply finds nothing.
2. **Remote** — whatever Firebase Remote Config last activated.
3. **Compiled default** — the `default =` argument.

**Step 3 is not a fallback of last resort.** It is the normal answer for the first seconds
of every install's life, and the permanent answer on a device that never reaches the
backend. A default that differs from current behaviour is a behaviour change on first run.

A value is **skipped**, and resolution continues to the next step, when it does not parse,
falls outside the key's `range`, or is an enum name the declaration does not list. The
build can only vouch for the compiled default; a typo in the console is the case worth
surviving.

Blank means "no override", not "the empty string" — which is why the legal-link keys can
default to `""` and still fall through to the address the build derives for itself.

---

## 3. The rules

1. **A group goes in the lowest module every consumer already depends on.** §1 step 1.
2. **Naming is `^[a-z][a-z0-9_]*$`, prefixed by the group.** The processor enforces it.
3. **Remote config turns things off, never on.** A flag can only reach code the installed
   APK already contains and the manifest already declares. Any flag whose "on" state needs
   a manifest entry, a permission or a native dependency that is not shipped is a lie.
   `refuel_detect_enabled` is exactly this — the notification listener's `<service>` is
   deliberately absent from the manifest, so `RefuelDetectionWorker` checks
   `NotificationAccess.isListenerDeclared()` and logs `detect_listener_not_declared` rather
   than failing silently. If a new flag has that shape, give it the same treatment.
4. **Defaults are not a last resort.** §2.
5. **A key belongs to exactly one group.** KSP sees one module at a time, so a cross-module
   clash is caught when the registry is assembled: **fail fast in debug, log in release**.
   The first declaration wins, which means the loser's consumers read a value nobody wrote
   for them — loud on a developer's machine, survivable in a shipped build.
6. **Most constants are not config.** Telemetry event and parameter names, test tags, table
   and column names, PDF colours, animation durations, arithmetic facts
   (`PAISE_PER_RUPEE`, `MONTHS_IN_YEAR`), protocol constants (OTP length), and Compose
   idioms with one correct value. A sweep for `const val` here returns hundreds of hits and
   almost all of them are noise.

   Two deliberate exclusions worth knowing. **Health-score band cutoffs** stay compiled:
   moving them silently restates every owner's score with no explanation they can see, so
   that should be a release with release notes. **`PlanLimits.FREE_PLAN`** may have its
   *numbers* overridden but never its *set* — `PlanLimitsTest`'s exhaustive `when` over
   `ProFeature` is what stops a missing row reaching a build, and a remote map throws that
   away.

---

## 4. What the build rejects

The processor fails the build with a message naming the offending property when:

- the key does not match `^[a-z][a-z0-9_]*$`;
- two keys in the same module share a name;
- the property type is unsupported, or nullable (a config key always has a value);
- the `default` does not parse as the declared type;
- the `range` does not parse, does not contain the default, or sits on a non-number;
- an enum `default` is not one of the constants;
- `@Flag` is on a non-`Boolean`, or `@Value` on a `Boolean`;
- a property in the group has neither annotation;
- `owner` or `why` is blank;
- `@ConfigGroup` is on something that is not an interface.

All of these are covered by tests in `:core:config:processor`, each a real compilation.

---

## 5. Map of the module

| File | What it holds |
|---|---|
| `Annotations.kt` | `@ConfigGroup`, `@Flag`, `@Value` |
| `ConfigKey.kt` | `ConfigKey`, `ConfigType`, `ConfigContribution`, `ResolvedConfigValue` |
| `ConfigSource.kt` | the remote port and `LocalConfigOverrides` |
| `ConfigResolver.kt` | the resolution order, the flows, and `describe()` for the QA screen |
| `ConfigRegistry.kt` | assembles contributions; records cross-module duplicates |
| `DuplicateKeys.kt` | the fail-in-debug / log-in-release policy |
| `ConfigRefresher.kt` | the refresh port, and the no-backend answers |
| `CoreConfigModule.kt` | registry + resolver + the defaults used when nothing is wired |
| `FeatureConfig.kt` | the two keys that used to be `FeatureFlags` consts |

Implementations that need a platform live elsewhere: `RemoteConfigSource` in
`:infrastructure:firebase:remoteconfig`, and the override store in `:core:platform`
(`SharedPreferences` / `NSUserDefaults`, debug builds only).

---

## 6. Keys in use today

| Key | Declared in | Type | Default |
|---|---|---|---|
| `min_supported_version_code` | `:infrastructure:firebase:remoteconfig` | Long | `0` |
| `maintenance_mode` | same | enum | `OFF` |
| `maintenance_message` | same | String | `""` |
| `legal_privacy_policy_url` | same | String | `""` |
| `legal_terms_url` | same | String | `""` |
| `legal_delete_account_url` | same | String | `""` |
| `support_email` | same | String | `""` |
| `auto_odometer_enabled` | `:core:config` | Boolean | `true` |
| `refuel_detect_enabled` | `:core:config` | Boolean | `true` |

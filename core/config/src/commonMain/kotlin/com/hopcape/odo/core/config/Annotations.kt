package com.hopcape.odo.core.config

/*
 * ── The rules for adding a config key ────────────────────────────────────────────────
 *
 * Read these before declaring one.
 *
 * They live in KDoc rather than a markdown file on purpose: every `.md` in this repo except
 * the README and the VCS conventions is gitignored, so a document would not reach a clone.
 * This is the file someone opens when they add a key.
 *
 * **1. Where a group goes.** In the lowest module every consumer already depends on. One
 * feature reads it, that feature module owns it. Several features read it, it goes in
 * `:core:config`. It is a domain port's payload, it goes with whatever module already reads
 * the key — not necessarily beside the port, because a generated group brings a Koin module
 * with it and `:core:domain` takes no framework types.
 *
 * **2. Naming.** `^[a-z][a-z0-9_]*$`, prefixed by the group. The processor rejects anything
 * else at build time.
 *
 * **3. Remote config turns things off, never on.** A flag can only reach code the installed
 * APK already contains and the manifest already declares. Any flag whose "on" state needs a
 * manifest entry, a permission or a native dependency that is not shipped is a lie —
 * `refuel_detect_enabled` is exactly this, and it will happen again. If a flag has that
 * shape, give it a runtime precondition that says so in the logs.
 *
 * **4. Defaults are not a last resort.** The compiled default is what every install answers
 * for the first seconds of its life, and forever on a device that never reaches the
 * backend. A default that differs from current behaviour is a behaviour change on first
 * run.
 *
 * **5. A key belongs to exactly one group.** KSP only sees one module, so two modules can
 * declare the same key and only the registry notices — fail fast in debug, log in release.
 *
 * **6. Most constants are not config.** Telemetry event and parameter names, test tags,
 * table and column names, PDF colours, animation durations, arithmetic facts
 * (`PAISE_PER_RUPEE`, `MONTHS_IN_YEAR`), protocol constants (OTP length), and Compose
 * idioms with one correct value. A sweep for `const val` in this repo returns hundreds of
 * hits and almost all of them are noise. Health-score band cutoffs are deliberately
 * excluded too: moving them silently restates every owner's score with no explanation they
 * can see, so that should be a release with release notes.
 */

/**
 * Marks an interface as a group of config keys.
 *
 * The rules above are the ones to follow when adding one.
 *
 * The interface is written by hand and is the type consumers inject. KSP generates
 * an implementation of it, a sibling holding one [kotlinx.coroutines.flow.Flow] per
 * key, the registry contribution, and the Koin module that binds them.
 *
 * Generating a sibling rather than the interface itself is deliberate: the file you
 * wrote stays resolvable on a fresh clone, before any build has run.
 *
 * [name] prefixes every key in the group and is what the QA screen groups by.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class ConfigGroup(val name: String)

/**
 * A boolean config key. Always a `Boolean` property, which is why [default] is typed
 * here and a string in [Value].
 *
 * [owner] is the team or person who decides this key's value. [why] is what the key
 * is for. Neither is decoration: both are shown on the QA screen, and together they
 * are what stops a key becoming unattributable a year from now.
 *
 * Remember that [default] is not a fallback of last resort. It is the answer every
 * install gives for the first seconds of its life, before any fetch lands, so a
 * default that differs from current behaviour is a behaviour change on first run.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Flag(
    val key: String,
    val default: Boolean,
    val owner: String,
    val why: String,
)

/**
 * A non-boolean config key: `Int`, `Long`, `Double`, `String`, or a string-backed enum.
 *
 * [default] is written as a string and parsed at build time against the property's
 * declared type. The build fails if it does not parse, or if [range] does not contain
 * it. This matches [range], which has always been a parsed string, and it means one
 * string-parsing path serves both compiled defaults and QA overrides.
 *
 * [range] applies to numbers only and is written as `"min..max"`, inclusive at both
 * ends. Leave it empty when the key has no bounds.
 *
 * A JSON-decoded object is not supported and is a later addition, not a gap in v1.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Value(
    val key: String,
    val default: String,
    val owner: String,
    val why: String,
    val range: String = "",
)

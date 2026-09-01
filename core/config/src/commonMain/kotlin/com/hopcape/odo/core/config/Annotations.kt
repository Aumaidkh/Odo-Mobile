package com.hopcape.odo.core.config

/*
 * The rules for adding a config key — where a group goes, naming, "remote turns off never
 * on", why a default is not a last resort, one key per group, and what is not config — are
 * in this module's README, together with a worked end-to-end example.
 *
 * A module README is committed (the repo gitignores every other `.md`), so it does reach a
 * clone. They are not repeated here: two copies of a rule is one copy that goes stale.
 */

/**
 * Marks an interface as a group of config keys.
 *
 * See this module's README before adding one.
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

package com.hopcape.odo.core.config

/**
 * What to do about a key two modules both declared.
 *
 * KSP validates one module at a time, so it cannot see a clash across modules. The registry
 * notices when it is assembled, and this decides the consequence: **fail fast in debug, log
 * in release**.
 *
 * Both halves matter. A duplicate key means one module's default silently loses, and every
 * consumer of the losing declaration reads a value nobody wrote for it — the kind of fault
 * that is invisible in a shipped build and obvious in a crash on a developer's machine. But
 * it must never take down a shipped app, because the wrong default is survivable and a
 * crash on launch is not.
 *
 * A `configVerify` Gradle task could catch it at build time instead. It is deliberately not
 * built: the runtime check is where the contributions actually meet, and there is no
 * evidence yet that it is too late.
 */
internal fun enforceUniqueKeys(
    registry: ConfigRegistry,
    isDebug: Boolean,
    onWarn: (String) -> Unit,
) {
    val duplicates = registry.duplicateKeys
    if (duplicates.isEmpty()) return

    val message = "Config keys declared by more than one module: ${duplicates.joinToString()}. " +
        "The first declaration wins and the rest are ignored. Each key belongs to exactly " +
        "one @ConfigGroup."
    if (isDebug) error(message) else onWarn(message)
}

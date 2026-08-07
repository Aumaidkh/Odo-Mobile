package com.hopcape.odo.infrastructure.database.car

/**
 * The current calendar year in the device's local timezone.
 *
 * Deliberately platform-native (not kotlinx-datetime): under Kotlin 2.4 the
 * `kotlinx.datetime.Clock`/`Instant` typealiases (to `kotlin.time.*`) break the
 * Kotlin/Native framework link ("IrTypeAliasSymbolImpl is already bound"), so the
 * data layer must not reference them. Used to cap selectable model/purchase years.
 */
internal expect fun currentYear(): Int

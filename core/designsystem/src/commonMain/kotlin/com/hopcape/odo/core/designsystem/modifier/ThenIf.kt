package com.hopcape.odo.core.designsystem.modifier

import androidx.compose.ui.Modifier

/**
 * Applies [other] only when [condition] holds.
 *
 * The alternative at every call site is an `if` around the whole chain, which duplicates
 * every modifier that is not conditional, or a `.then(if (x) Modifier.foo() else Modifier)`
 * that reads backwards. This keeps the chain in one piece:
 *
 * ```
 * Modifier
 *     .fillMaxSize()
 *     .thenIf(isSelected) { border(1.dp, OdoTheme.colors.accent) }
 *     .padding(OdoTheme.spacing.md)
 * ```
 *
 * Order still matters, as it does anywhere in a modifier chain: what [other] adds lands at
 * the point `thenIf` appears, not at the end.
 */
inline fun Modifier.thenIf(condition: Boolean, other: Modifier.() -> Modifier): Modifier =
    if (condition) other() else this

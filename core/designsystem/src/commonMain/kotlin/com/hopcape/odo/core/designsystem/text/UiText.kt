package com.hopcape.odo.core.designsystem.text

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A piece of user-facing text produced by a presentation layer (ViewModel/state)
 * as a [StringResource] reference plus any format args — never a hardcoded literal.
 * The UI turns it into a String with [asString] at render time, so all copy stays
 * in a `strings.xml` and localizable, while the ViewModel remains Compose-free.
 *
 * Lives in the design system (not any one feature) so every feature's state can
 * carry resolvable text the same way. Each feature supplies its own [StringResource]s.
 */
data class UiText(
    val id: StringResource,
    val args: List<Any> = emptyList(),
)

/** Resolve this [UiText] against the current composition's resources. */
@Composable
fun UiText.asString(): String =
    if (args.isEmpty()) stringResource(id) else stringResource(id, *args.toTypedArray())

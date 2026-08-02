package com.hopcape.odo.core.designsystem.text

import androidx.compose.runtime.Composable
import com.hopcape.odo.core.designsystem.units.LocalOdoDistanceFormat
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

/**
 * A distance inside a message, in stored kilometres.
 *
 * Wrapping it — rather than putting a formatted number in the args — is what lets a
 * ViewModel write "the last reading was this far" without knowing which unit the owner
 * reads in. [asString] converts it at render time, where the setting is available. Use it
 * for every distance a message quotes; a raw number would always print kilometres.
 */
data class DistanceArg(val km: Int)

/**
 * Resolve this [UiText] against the current composition's resources, converting any
 * [DistanceArg] to the owner's distance unit first.
 */
@Composable
fun UiText.asString(): String {
    if (args.isEmpty()) return stringResource(id)
    val format = LocalOdoDistanceFormat.current
    val resolved = args.map { arg -> if (arg is DistanceArg) format.format(arg.km) else arg }
    return stringResource(id, *resolved.toTypedArray())
}

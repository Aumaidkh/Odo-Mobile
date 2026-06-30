package com.hopcape.odo.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * The root Odo theme. Wrap the app (and Compose previews) in this exactly once,
 * above the navigation host. It establishes Material 3 (so stock components are
 * on-brand) **and** provides Odo's named brand tokens — colours, type, shapes,
 * spacing, elevation, icon sizes, motion — via [OdoTheme].
 *
 * Odo is dark-first by identity, but this honours the system setting by default;
 * pass [darkTheme] explicitly to pin a theme (e.g. in previews).
 *
 * ```
 * OdoTheme {
 *     Text("Apni gaadi", style = OdoTheme.typography.title, color = OdoTheme.colors.text)
 * }
 * ```
 *
 * @param darkTheme whether to use the dark palette (defaults to the system setting).
 */
@Composable
fun OdoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val odoColors = if (darkTheme) DarkOdoColors else LightOdoColors
    val materialScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(
        LocalOdoColors provides odoColors,
        LocalOdoTypography provides OdoDefaultTypography,
        LocalOdoShapes provides OdoDefaultShapes,
        LocalOdoSpacing provides OdoSpacing(),
        LocalOdoElevation provides OdoElevation(),
        LocalOdoIconSizes provides OdoIconSizes(),
        LocalOdoMotion provides OdoMotion(),
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = odoMaterialTypography(OdoDefaultTypography),
            shapes = odoMaterialShapes(OdoDefaultShapes),
            content = content,
        )
    }
}

/**
 * Accessor for Odo's design tokens inside a composition under [OdoTheme] — the
 * brand-faithful counterpart to [MaterialTheme]. Reach for `OdoTheme.colors` /
 * `OdoTheme.typography` first; [materialColors] is the underlying Material
 * scheme for the rare case a stock component needs a role directly.
 */
object OdoTheme {
    val colors: OdoColors
        @Composable @ReadOnlyComposable get() = LocalOdoColors.current

    val typography: OdoTypography
        @Composable @ReadOnlyComposable get() = LocalOdoTypography.current

    val shapes: OdoShapes
        @Composable @ReadOnlyComposable get() = LocalOdoShapes.current

    val spacing: OdoSpacing
        @Composable @ReadOnlyComposable get() = LocalOdoSpacing.current

    val elevation: OdoElevation
        @Composable @ReadOnlyComposable get() = LocalOdoElevation.current

    val iconSizes: OdoIconSizes
        @Composable @ReadOnlyComposable get() = LocalOdoIconSizes.current

    val motion: OdoMotion
        @Composable @ReadOnlyComposable get() = LocalOdoMotion.current

    /** The underlying Material 3 scheme, for stock Material components. */
    val materialColors: ColorScheme
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme
}

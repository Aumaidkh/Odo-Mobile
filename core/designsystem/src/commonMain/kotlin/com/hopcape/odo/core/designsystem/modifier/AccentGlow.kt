package com.hopcape.odo.core.designsystem.modifier

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * The accent bloom under a primary call to action, so "this is the way forward" reads the
 * same everywhere it appears.
 *
 * Dropped when [enabled] is false, where a glow would promise something the button will not
 * do.
 */
@Composable
fun Modifier.accentGlow(enabled: Boolean = true, elevation: Dp = 18.dp): Modifier =
    if (!enabled) {
        this
    } else {
        shadow(
            elevation = elevation,
            shape = OdoTheme.shapes.pill,
            clip = false,
            ambientColor = OdoTheme.colors.accent,
            spotColor = OdoTheme.colors.accent,
        )
    }

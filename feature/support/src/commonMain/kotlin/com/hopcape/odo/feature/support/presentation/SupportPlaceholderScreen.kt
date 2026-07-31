package com.hopcape.odo.feature.support.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.component.OdoEmptyState
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.icons.IcClock
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.support.resources.Res
import com.hopcape.odo.feature.support.resources.sp_soon
import com.hopcape.odo.feature.support.resources.sp_soon_message
import org.jetbrains.compose.resources.stringResource

/**
 * The stand-in every support destination renders until its own screen is built.
 *
 * One composable rather than a dozen near-identical stubs: each destination differs only
 * by its [title], so the entry provider passes that and nothing else. When a real screen
 * lands it replaces its entry's body here — no other file changes.
 */
@Composable
internal fun SupportPlaceholderScreen(title: String, onBack: () -> Unit) {
    OdoScreen(title = title, onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            OdoEmptyState(
                title = stringResource(Res.string.sp_soon),
                message = stringResource(Res.string.sp_soon_message),
                icon = {
                    OdoIcon(
                        IcClock,
                        contentDescription = null,
                        tint = OdoTheme.colors.textMuted,
                        size = OdoTheme.iconSizes.large,
                    )
                },
            )
        }
    }
}

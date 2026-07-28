package com.hopcape.odo.feature.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcLock
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.auth.resources.Res
import com.hopcape.odo.feature.auth.resources.au_verifying_body
import com.hopcape.odo.feature.auth.resources.au_verifying_title
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * "Verifying your number" ([com.hopcape.odo.core.navigation.OdoDestination.Auth.Verifying]).
 * Sample: auto-completes after a short delay and hands off via [onDone] (the real
 * verification lands with the ViewModel + auth backend).
 */
@Composable
internal fun VerifyingScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1600)
        onDone()
    }
    OdoScreen {
        Column(
            modifier = Modifier.fillMaxSize().padding(it),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(88.dp),
                    color = OdoTheme.colors.accent,
                    trackColor = OdoTheme.colors.border,
                    strokeWidth = 3.dp,
                )
                OdoIcon(IcLock, contentDescription = null, tint = OdoTheme.colors.accent, size = OdoTheme.iconSizes.large)
            }
            Spacer(Modifier.height(OdoTheme.spacing.lg))
            OdoText(stringResource(Res.string.au_verifying_title), style = OdoTheme.typography.title, textAlign = TextAlign.Center)
            Spacer(Modifier.height(OdoTheme.spacing.xs))
            OdoText(stringResource(Res.string.au_verifying_body), style = OdoTheme.typography.body, color = OdoTheme.colors.textDim, textAlign = TextAlign.Center)
        }
    }
}

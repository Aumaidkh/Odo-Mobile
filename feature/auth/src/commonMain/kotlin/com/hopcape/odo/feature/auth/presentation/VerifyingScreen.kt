package com.hopcape.odo.feature.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.auth.resources.Res
import com.hopcape.odo.feature.auth.resources.au_verifying_body
import com.hopcape.odo.feature.auth.resources.au_verifying_title
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * The beat after signing in ([com.hopcape.odo.core.navigation.OdoDestination.Auth.Verifying]).
 *
 * **Confirmation, not work.** Verification happens on the code screen, which shows its own
 * in-flight state while the request is out; this is only reached once a session exists. It
 * used to say "Verifying your number" over a spinner, which was a lie held on screen for a
 * second and a half — so it is a tick and a done message now.
 *
 * It is kept rather than dropped because signing in is the one moment in the flow worth
 * acknowledging: the owner just handed over a number to protect records they already care
 * about, and going straight back to a list would leave them unsure it worked.
 */
@Composable
internal fun VerifyingScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(CONFIRMATION_MILLIS)
        onDone()
    }
    OdoScreen {
        Column(
            modifier = Modifier.fillMaxSize().padding(it),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SignedInBadge()
            Spacer(Modifier.height(OdoTheme.spacing.lg))
            OdoText(stringResource(Res.string.au_verifying_title), style = OdoTheme.typography.title, textAlign = TextAlign.Center)
            Spacer(Modifier.height(OdoTheme.spacing.xs))
            OdoText(stringResource(Res.string.au_verifying_body), style = OdoTheme.typography.body, color = OdoTheme.colors.textDim, textAlign = TextAlign.Center)
        }
    }
}

/**
 * The same glow badge the document-vault success screen uses, so the two confirmations in the
 * app look like one app. A tick rather than a spinner: nothing is in progress by the time this
 * is drawn.
 */
@Composable
private fun SignedInBadge() {
    val tone = OdoTheme.colors.success
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(120.dp).clip(CircleShape).background(tone.copy(alpha = 0.10f)))
        Box(Modifier.size(92.dp).clip(CircleShape).background(tone.copy(alpha = 0.20f)))
        Box(
            Modifier.size(68.dp).clip(CircleShape).background(tone),
            contentAlignment = Alignment.Center,
        ) {
            OdoIcon(IcCheck, contentDescription = null, tint = OdoTheme.colors.bg, size = OdoTheme.iconSizes.large)
        }
    }
}

/** Long enough to read, short enough not to be in the way. */
private const val CONFIRMATION_MILLIS = 1_200L

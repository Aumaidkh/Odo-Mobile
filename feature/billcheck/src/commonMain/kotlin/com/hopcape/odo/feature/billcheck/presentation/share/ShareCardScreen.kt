package com.hopcape.odo.feature.billcheck.presentation.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.core.platform.share.toPngBytes
import com.hopcape.odo.feature.billcheck.resources.Res
import com.hopcape.odo.feature.billcheck.resources.bc_share_card_body
import com.hopcape.odo.feature.billcheck.resources.bc_share_card_brand
import com.hopcape.odo.feature.billcheck.resources.bc_share_card_label
import com.hopcape.odo.feature.billcheck.resources.bc_share_privacy
import com.hopcape.odo.feature.billcheck.resources.bc_share_save
import com.hopcape.odo.feature.billcheck.resources.bc_share_title
import com.hopcape.odo.feature.billcheck.resources.bc_share_whatsapp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The card, and the two things an owner does with it.
 *
 * The card is drawn on screen and captured from that same drawing, so what gets sent is what
 * they were shown. Rendering it twice — once to look at, once to send — is how the two end up
 * different.
 */
@Composable
internal fun ShareCardScreen(
    state: ShareCardUiState,
    onEvent: (ShareCardEvent) -> Unit,
    modifier: Modifier = Modifier,
    /** Where "saved" and "could not make the card" are said. */
    snackbarHostState: SnackbarHostState? = null,
) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()

    /** Capture what is on screen, then hand the bytes to [event]. */
    fun capture(event: (ByteArray?) -> ShareCardEvent) {
        scope.launch { onEvent(event(layer.toImageBitmap().toPngBytes())) }
    }

    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.bc_share_title),
        onBack = { onEvent(ShareCardEvent.BackClicked) },
        snackbarHostState = snackbarHostState,
        bottomBar = {
            Actions(
                enabled = !state.working,
                onWhatsApp = { capture(ShareCardEvent::SendOnWhatsAppClicked) },
                onSave = { capture(ShareCardEvent::SaveClicked) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Card(state = state, layer = layer)
            Spacer(Modifier.height(OdoTheme.spacing.lg))
            OdoText(
                text = stringResource(Res.string.bc_share_privacy),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textMuted,
            )
        }
    }
}

/**
 * The card itself — white, whatever the app is wearing.
 *
 * Its colours are literals rather than theme tokens, and that is the point: a picture that
 * leaves the app has to look the same coming out of every phone. Reading the theme here would
 * send a dark-mode owner's friends a black card and a light-mode owner's friends a white one,
 * from one product.
 *
 * [layer] records the drawing as it happens, so the capture is this exact composition rather
 * than a second one built to be sent.
 */
@Composable
private fun Card(state: ShareCardUiState, layer: GraphicsLayer) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Rounded *outside* the recording, and opaque inside it. The corners are a
            // rounded clip on the way to the screen; what the layer records is the square
            // white fill beneath them. WhatsApp re-encodes a shared picture as JPEG, JPEG
            // has no transparency, and a rounded card sent through it arrives with four
            // black corners.
            .clip(RoundedCornerShape(CARD_RADIUS))
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            }
            .background(CARD_BACKGROUND)
            .padding(CARD_PADDING),
    ) {
        OdoText(
            text = stringResource(Res.string.bc_share_card_label),
            style = OdoTheme.typography.label,
            color = CARD_LABEL,
        )
        Spacer(Modifier.height(OdoTheme.spacing.sm))
        OdoText(
            text = state.amount.formatRupees(),
            style = OdoTheme.typography.display.copy(
                // Bigger than anything the app itself shows. On a phone this is read across a
                // room, in a chat thread, next to a thumbnail.
                fontSize = HERO_SIZE,
                lineHeight = HERO_LINE,
                fontWeight = FontWeight.Bold,
            ),
            color = CARD_INK,
        )
        Spacer(Modifier.height(OdoTheme.spacing.md))
        Box(
            Modifier
                .fillMaxWidth()
                .height(RULE_HEIGHT)
                .background(CARD_RULE),
        )
        Spacer(Modifier.height(OdoTheme.spacing.md))
        OdoText(
            text = pluralStringResource(
                Res.plurals.bc_share_card_body,
                state.flagged,
                state.flagged,
                state.lines,
            ),
            style = OdoTheme.typography.body,
            color = CARD_BODY,
        )
        Spacer(Modifier.height(OdoTheme.spacing.lg))
        OdoText(
            text = stringResource(Res.string.bc_share_card_brand),
            style = OdoTheme.typography.label,
            color = CARD_INK,
        )
    }
}

/**
 * WhatsApp first, and by name.
 *
 * The card exists to be retold in a family group — that is the whole of Scene 2 — so the
 * primary action names where it is going rather than offering a sheet to choose from.
 */
@Composable
private fun Actions(enabled: Boolean, onWhatsApp: () -> Unit, onSave: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(OdoTheme.spacing.screenEdge),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        OdoButton(
            text = stringResource(Res.string.bc_share_whatsapp),
            onClick = onWhatsApp,
            enabled = enabled,
            modifier = Modifier.weight(WHATSAPP_WEIGHT),
        )
        OdoButton(
            text = stringResource(Res.string.bc_share_save),
            onClick = onSave,
            enabled = enabled,
            variant = OdoButtonVariant.Secondary,
        )
    }
}

/* ------------------------------ The card's own palette ------------------------------ */

private val CARD_BACKGROUND = Color.White
private val CARD_INK = Color(0xFF0A0A0A)
private val CARD_LABEL = Color(0xFF5B6472)
private val CARD_BODY = Color(0xFF1F2937)
private val CARD_RULE = Color(0xFFE3E6EA)

private val CARD_RADIUS = 28.dp
private val CARD_PADDING = 28.dp
private val RULE_HEIGHT = 1.dp
private val HERO_SIZE = 56.sp
private val HERO_LINE = 64.sp

/** "Send on WhatsApp" carries the errand; "Save" is the afterthought beside it. */
private const val WHATSAPP_WEIGHT = 1f

@OdoThemePreviews
@Composable
private fun ShareCardPreview() = OdoPreview(padded = false) {
    ShareCardScreen(
        state = ShareCardUiState(
            amount = Amount.of(730_000).getOrNull() ?: Amount.ZERO,
            flagged = 3,
            lines = 6,
        ),
        onEvent = {},
    )
}

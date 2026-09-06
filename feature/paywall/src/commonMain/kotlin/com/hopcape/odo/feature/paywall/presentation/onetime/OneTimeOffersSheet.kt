package com.hopcape.odo.feature.paywall.presentation.onetime

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.paywall.presentation.state.Loadable
import com.hopcape.odo.feature.paywall.resources.Res
import com.hopcape.odo.feature.paywall.resources.pw_ot_close
import com.hopcape.odo.feature.paywall.resources.pw_ot_empty
import com.hopcape.odo.feature.paywall.resources.pw_retry
import org.jetbrains.compose.resources.stringResource

/**
 * "Buy it once" — the things Odo sells one at a time, for someone who does not want a plan.
 *
 * Every price on it is the store's own string. Nothing here is formatted by Odo and nothing
 * is written into `strings.xml`: a price change in Play Console reaches this sheet with no
 * release, and a product the store does not know about is simply not drawn.
 *
 * Stateless: renders [state] and forwards [OneTimeOffersEvent]s.
 */
@Composable
internal fun OneTimeOffersSheetContent(
    state: OneTimeOffersUiState,
    onEvent: (OneTimeOffersEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(bottom = OdoTheme.spacing.xl)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            OdoText(stringResource(state.context.title), style = OdoTheme.typography.title)
            OdoText(
                text = stringResource(state.context.subtitle),
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.textDim,
            )
        }

        when (val offers = state.offers) {
            Loadable.Loading -> Centred { OdoLoadingIndicator() }

            is Loadable.Failed -> Message(offers.message.asString()) {
                OdoButton(
                    text = stringResource(Res.string.pw_retry),
                    onClick = { onEvent(OneTimeOffersEvent.RetryTapped) },
                    variant = OdoButtonVariant.Secondary,
                )
            }

            is Loadable.Ready -> if (offers.value.isEmpty()) {
                // Not a failure. Nothing has been created in the store yet, and saying so is
                // more honest than a retry button that will find the same nothing.
                Message(stringResource(Res.string.pw_ot_empty))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
                    offers.value.forEach { card ->
                        OfferRow(card) { onEvent(OneTimeOffersEvent.OfferTapped(card.offer.productId)) }
                    }
                    state.context.footer?.let { footer ->
                        OdoText(
                            text = stringResource(footer),
                            style = OdoTheme.typography.bodySmall,
                            color = OdoTheme.colors.textMuted,
                        )
                    }
                }
            }
        }

        // The sheet's own way out, like every other sheet in the app. The scrim and the back
        // gesture both close it too; a button is what makes that discoverable.
        OdoButton(
            text = stringResource(Res.string.pw_ot_close),
            onClick = { onEvent(OneTimeOffersEvent.CloseTapped) },
            modifier = Modifier.fillMaxWidth(),
            variant = OdoButtonVariant.Secondary,
        )
    }
}

/**
 * One product: what it is, what it does, and what the store charges for it.
 *
 * A recommended row inverts, the way `OdoOptionCardStyle.Filled` does — but this is not that
 * card. Tapping here buys, so the row announces itself as a button; the shared card announces
 * a radio or a checkbox, which is the wrong thing to say about a purchase.
 */
@Composable
private fun OfferRow(card: OneTimeOfferCard, onClick: () -> Unit) {
    val colors = OdoTheme.colors
    val onCard = if (card.recommended) colors.onAccent else colors.text
    OdoCard(
        onClick = onClick,
        color = if (card.recommended) colors.accent else colors.surface,
        border = if (card.recommended) null else BorderStroke(1.dp, colors.border),
        modifier = Modifier.semantics { role = Role.Button },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
            ) {
                OdoText(
                    text = stringResource(card.offer.title),
                    style = OdoTheme.typography.heading,
                    color = onCard,
                )
                OdoText(
                    text = stringResource(card.offer.subtitle),
                    style = OdoTheme.typography.bodySmall,
                    color = if (card.recommended) colors.onAccent.copy(alpha = DIM) else colors.textDim,
                )
            }
            OdoText(text = card.price, style = OdoTheme.typography.heading, color = onCard)
        }
    }
}

/** The second line on an inverted card — readable, and clearly the lesser of the two. */
private const val DIM = 0.7f

@Composable
private fun Message(text: String, action: (@Composable () -> Unit)? = null) = Centred {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        OdoText(
            text = text,
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

/** Keeps the wait, the failure and the empty state the same height as a short list. */
@Composable
private fun Centred(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = MESSAGE_MIN_HEIGHT),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}

private val MESSAGE_MIN_HEIGHT = 140.dp

@OdoThemePreviews
@Composable
private fun OneTimeOffersPreview() = OdoPreview {
    OneTimeOffersSheetContent(
        state = OneTimeOffersUiState(
            context = OneTimeContext.BILL_CHECK,
            offers = Loadable.Ready(
                listOf(
                    OneTimeOfferCard(OneTimeOffer.BILL_CHECK_PACK, "₹99", recommended = true),
                    OneTimeOfferCard(OneTimeOffer.BILL_CHECK_SINGLE, "₹49"),
                ),
            ),
        ),
        onEvent = {},
    )
}

/** What it looks like today: the products are not in the store yet. */
@OdoThemePreviews
@Composable
private fun OneTimeOffersEmptyPreview() = OdoPreview {
    OneTimeOffersSheetContent(
        state = OneTimeOffersUiState(offers = Loadable.Ready(emptyList())),
        onEvent = {},
    )
}

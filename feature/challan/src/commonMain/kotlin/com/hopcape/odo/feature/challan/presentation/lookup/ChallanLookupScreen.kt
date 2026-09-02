package com.hopcape.odo.feature.challan.presentation.lookup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoInputField
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcClose
import com.hopcape.odo.core.designsystem.icons.IcWindow
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.challan.presentation.ChallanTestTags
import com.hopcape.odo.feature.challan.resources.Res
import com.hopcape.odo.feature.challan.resources.ch_cd_back
import com.hopcape.odo.feature.challan.resources.ch_lookup_cta
import com.hopcape.odo.feature.challan.resources.ch_lookup_field_hint
import com.hopcape.odo.feature.challan.resources.ch_lookup_field_label
import com.hopcape.odo.feature.challan.resources.ch_lookup_footnote
import com.hopcape.odo.feature.challan.resources.ch_lookup_no_owner
import com.hopcape.odo.feature.challan.resources.ch_lookup_nothing_saved
import com.hopcape.odo.feature.challan.resources.ch_lookup_public_only
import com.hopcape.odo.feature.challan.resources.ch_lookup_reason
import com.hopcape.odo.feature.challan.resources.ch_lookup_title
import com.hopcape.odo.feature.challan.resources.ch_notfound_body
import com.hopcape.odo.feature.challan.resources.ch_notfound_check_for
import com.hopcape.odo.feature.challan.resources.ch_notfound_edit
import com.hopcape.odo.feature.challan.resources.ch_notfound_hint_digit
import com.hopcape.odo.feature.challan.resources.ch_notfound_hint_letters
import com.hopcape.odo.feature.challan.resources.ch_notfound_hint_new
import com.hopcape.odo.feature.challan.resources.ch_notfound_title
import org.jetbrains.compose.resources.stringResource

/**
 * "Check any vehicle" — the buyer's check (mockup 7), and its "No vehicle found" answer
 * (mockup 9) on the same route, so the typed plate survives the round trip.
 */
@Composable
internal fun ChallanLookupScreen(
    state: ChallanLookupUiState,
    onEvent: (ChallanLookupEvent) -> Unit,
) {
    OdoScreen(
        title = stringResource(Res.string.ch_lookup_title),
        onBack = { onEvent(ChallanLookupEvent.BackTapped) },
        backContentDescription = stringResource(Res.string.ch_cd_back),
        bottomBar = { BottomBar(state, onEvent) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            if (state.notFound != null) {
                NotFoundBody(state.notFound)
            } else {
                InputBody(state, onEvent)
            }
        }
    }
}

@Composable
private fun InputBody(state: ChallanLookupUiState, onEvent: (ChallanLookupEvent) -> Unit) {
    OdoText(
        stringResource(Res.string.ch_lookup_reason),
        style = OdoTheme.typography.body,
        color = OdoTheme.colors.textDim,
    )
    OdoInputField(
        value = state.plate,
        onValueChange = { onEvent(ChallanLookupEvent.PlateChanged(it)) },
        label = stringResource(Res.string.ch_lookup_field_label),
        placeholder = stringResource(Res.string.ch_lookup_field_hint),
        errorText = state.error?.asString(),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = KeyboardType.Ascii,
        ),
        modifier = Modifier.fillMaxWidth().testTag(ChallanTestTags.LOOKUP_FIELD),
    )
    OdoCard(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
        PrivacyRow(included = true, text = stringResource(Res.string.ch_lookup_public_only))
        OdoDivider()
        PrivacyRow(included = false, text = stringResource(Res.string.ch_lookup_no_owner))
        OdoDivider()
        PrivacyRow(included = false, text = stringResource(Res.string.ch_lookup_nothing_saved))
    }
}

/** ✓ what the lookup reads, ✗ what it never touches — the screen's whole privacy story. */
@Composable
private fun PrivacyRow(included: Boolean, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoIcon(
            if (included) IcCheck else IcClose,
            contentDescription = null,
            tint = if (included) OdoTheme.colors.success else OdoTheme.colors.danger,
            size = OdoTheme.iconSizes.small,
        )
        OdoText(text, style = OdoTheme.typography.body, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun NotFoundBody(notFound: NotFoundState) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(ChallanTestTags.NOT_FOUND),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        Spacer(Modifier.size(OdoTheme.spacing.xxl))
        Box(
            Modifier
                .size(72.dp)
                .clip(OdoTheme.shapes.small)
                .background(OdoTheme.colors.surfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            OdoIcon(IcWindow, contentDescription = null, tint = OdoTheme.colors.textDim, size = OdoTheme.iconSizes.large)
        }
        OdoText(
            stringResource(Res.string.ch_notfound_title),
            style = OdoTheme.typography.title,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        OdoText(
            stringResource(Res.string.ch_notfound_body, notFound.plateDisplay),
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        OdoCard(modifier = Modifier.fillMaxWidth()) {
            OdoText(
                stringResource(Res.string.ch_notfound_check_for).uppercase(),
                style = OdoTheme.typography.caption,
                color = OdoTheme.colors.textMuted,
            )
            HintRow(stringResource(Res.string.ch_notfound_hint_digit))
            HintRow(stringResource(Res.string.ch_notfound_hint_letters))
            HintRow(stringResource(Res.string.ch_notfound_hint_new))
        }
    }
}

@Composable
private fun HintRow(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(OdoTheme.colors.textMuted),
        )
        OdoText(
            text,
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BottomBar(state: ChallanLookupUiState, onEvent: (ChallanLookupEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = OdoTheme.spacing.screenEdge,
                end = OdoTheme.spacing.screenEdge,
                top = OdoTheme.spacing.sm,
                bottom = OdoTheme.spacing.sm,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
    ) {
        if (state.notFound != null) {
            OdoButton(
                text = stringResource(Res.string.ch_notfound_edit),
                onClick = { onEvent(ChallanLookupEvent.EditNumberTapped) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OdoButton(
                text = stringResource(Res.string.ch_lookup_cta),
                onClick = { onEvent(ChallanLookupEvent.CheckTapped) },
                enabled = state.canCheck,
                loading = state.checking,
                modifier = Modifier.fillMaxWidth().testTag(ChallanTestTags.LOOKUP_CTA),
            )
            OdoText(
                stringResource(Res.string.ch_lookup_footnote),
                style = OdoTheme.typography.caption,
                color = OdoTheme.colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

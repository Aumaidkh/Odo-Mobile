package com.hopcape.odo.feature.billscanner.presentation.result

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcLightbulbFilled
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.billscanner.resources.Res
import com.hopcape.odo.feature.billscanner.resources.bs_save_body
import com.hopcape.odo.feature.billscanner.resources.bs_save_body_plain
import com.hopcape.odo.feature.billscanner.resources.bs_save_card_body
import com.hopcape.odo.feature.billscanner.resources.bs_save_card_title
import com.hopcape.odo.feature.billscanner.resources.bs_save_done
import com.hopcape.odo.feature.billscanner.resources.bs_save_title
import com.hopcape.odo.feature.billscanner.resources.bs_save_view
import org.jetbrains.compose.resources.stringResource

/**
 * Terminal success after a reviewed bill is saved — a verified entry now in the
 * owner's service history, with the Health Score bump it earned. Offers a jump into
 * the service log or a plain dismiss.
 */
@Composable
internal fun SaveSuccessScreen(
    /** Blank when the caller has neither — the body then names nothing rather than nothing twice. */
    workshop: String,
    dateLabel: String,
    onViewLog: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ResultScreen(
        modifier = modifier,
        badgeIcon = IcCheck,
        badgeTone = OdoTheme.colors.success,
        title = stringResource(Res.string.bs_save_title),
        body = if (workshop.isBlank() && dateLabel.isBlank()) {
            stringResource(Res.string.bs_save_body_plain)
        } else {
            stringResource(Res.string.bs_save_body, workshop, dateLabel)
        },
        infoCard = {
            ResultInfoCard(
                icon = IcLightbulbFilled,
                iconTone = OdoTheme.colors.accent,
                title = stringResource(Res.string.bs_save_card_title),
                subtitle = stringResource(Res.string.bs_save_card_body),
            )
        },
    ) {
        OdoButton(stringResource(Res.string.bs_save_view), onClick = onViewLog, modifier = Modifier.fillMaxWidth())
        OdoText(
            stringResource(Res.string.bs_save_done),
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
            modifier = Modifier
                .clip(OdoTheme.shapes.pill)
                .clickable(onClick = onDone)
                .padding(horizontal = OdoTheme.spacing.md, vertical = OdoTheme.spacing.sm),
        )
    }
}

@OdoThemePreviews
@Composable
private fun SaveSuccessScreenPreview() = OdoPreview(padded = false) {
    SaveSuccessScreen(workshop = "Sharma Motors", dateLabel = "12 Jun 2026", onViewLog = {}, onDone = {})
}

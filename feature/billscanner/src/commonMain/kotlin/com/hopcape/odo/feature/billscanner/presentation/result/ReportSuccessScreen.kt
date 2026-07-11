package com.hopcape.odo.feature.billscanner.presentation.result

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.icons.IcPerson
import com.hopcape.odo.core.designsystem.icons.IcSend
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.billscanner.resources.Res
import com.hopcape.odo.feature.billscanner.resources.bs_report_back
import com.hopcape.odo.feature.billscanner.resources.bs_report_body
import com.hopcape.odo.feature.billscanner.resources.bs_report_card_body
import com.hopcape.odo.feature.billscanner.resources.bs_report_card_title
import com.hopcape.odo.feature.billscanner.resources.bs_report_title
import org.jetbrains.compose.resources.stringResource

/**
 * Terminal success after an anonymous overcharge report — thanks the owner and shows
 * their contribution to the city fair-price index (the pool the fairness check reads
 * from). The report is de-identified, so nothing here ties back to the owner.
 */
@Composable
internal fun ReportSuccessScreen(
    city: String,
    reportCount: Int,
    onBackToLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ResultScreen(
        modifier = modifier,
        badgeIcon = IcSend,
        badgeTone = OdoTheme.colors.accent,
        title = stringResource(Res.string.bs_report_title),
        body = stringResource(Res.string.bs_report_body, city),
        infoCard = {
            ResultInfoCard(
                icon = IcPerson,
                iconTone = OdoTheme.colors.success,
                title = stringResource(Res.string.bs_report_card_title, reportCount),
                subtitle = stringResource(Res.string.bs_report_card_body),
            )
        },
    ) {
        OdoButton(stringResource(Res.string.bs_report_back), onClick = onBackToLog, modifier = Modifier.fillMaxWidth())
    }
}

@OdoThemePreviews
@Composable
private fun ReportSuccessScreenPreview() = OdoPreview(padded = false) {
    ReportSuccessScreen(city = "Pune", reportCount = 37, onBackToLog = {})
}

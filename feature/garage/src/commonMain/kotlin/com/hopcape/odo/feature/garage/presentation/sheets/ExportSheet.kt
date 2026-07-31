package com.hopcape.odo.feature.garage.presentation.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcPdf
import com.hopcape.odo.core.designsystem.icons.IcShare
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.garage.presentation.GarageSheet
import com.hopcape.odo.feature.garage.presentation.state.valueOrNull
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_ex_docs
import com.hopcape.odo.feature.garage.resources.gr_ex_docs_count
import com.hopcape.odo.feature.garage.resources.gr_ex_pdf
import com.hopcape.odo.feature.garage.resources.gr_ex_pro_note
import com.hopcape.odo.feature.garage.resources.gr_ex_service
import com.hopcape.odo.feature.garage.resources.gr_ex_service_count
import com.hopcape.odo.feature.garage.resources.gr_ex_share
import com.hopcape.odo.feature.garage.resources.gr_ex_subtitle
import com.hopcape.odo.feature.garage.resources.gr_ex_title
import org.jetbrains.compose.resources.stringResource

/**
 * Export-car-record sheet ([com.hopcape.odo.core.navigation.OdoDestination.Garage.Export]).
 *
 * It lists what is on file and says plainly that exporting it is part of Pro. Both actions
 * lead to the paywall: the real export is the Resale Passport, and shipping a partial one
 * now would give away the thing a buyer is meant to trust and an owner is meant to pay for.
 *
 * The per-section toggles are gone with it. A checkbox that changes nothing about a document
 * that is not produced is a control pretending to have an effect.
 */
@Composable
internal fun ExportSheetContent(state: ExportUiState, onEvent: (ExportEvent) -> Unit) {
    val car = state.car.valueOrNull
    GarageSheet {
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
            OdoText(stringResource(Res.string.gr_ex_title), style = OdoTheme.typography.heading)
            OdoText(
                stringResource(Res.string.gr_ex_subtitle),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }
        if (car != null) {
            ContentsRow(
                label = stringResource(Res.string.gr_ex_service),
                count = stringResource(Res.string.gr_ex_service_count, car.serviceCount),
            )
            ContentsRow(
                label = stringResource(Res.string.gr_ex_docs),
                count = stringResource(Res.string.gr_ex_docs_count, car.documentCount),
            )
        }
        OdoText(
            stringResource(Res.string.gr_ex_pro_note),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
            OdoButton(
                stringResource(Res.string.gr_ex_pdf),
                onClick = { onEvent(ExportEvent.PdfTapped) },
                modifier = Modifier.weight(1f),
                variant = OdoButtonVariant.Secondary,
                leadingIcon = { OdoIcon(IcPdf, contentDescription = null, size = OdoTheme.iconSizes.small) },
            )
            OdoButton(
                stringResource(Res.string.gr_ex_share),
                onClick = { onEvent(ExportEvent.ShareTapped) },
                modifier = Modifier.weight(1f),
                leadingIcon = { OdoIcon(IcShare, contentDescription = null, size = OdoTheme.iconSizes.small) },
            )
        }
    }
}

/** One line of "what's in the record" — a fact about the car, not a control. */
@Composable
private fun ContentsRow(label: String, count: String) {
    OdoCard(color = OdoTheme.colors.surfaceRaised) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoText(label, style = OdoTheme.typography.heading, modifier = Modifier.weight(1f))
            OdoText(count, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
        }
    }
}

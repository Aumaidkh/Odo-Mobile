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
import com.hopcape.odo.feature.garage.resources.gr_ex_failed
import com.hopcape.odo.feature.garage.resources.gr_ex_pdf
import com.hopcape.odo.feature.garage.resources.gr_ex_preparing
import com.hopcape.odo.feature.garage.resources.gr_ex_service
import com.hopcape.odo.feature.garage.resources.gr_ex_service_count
import com.hopcape.odo.feature.garage.resources.gr_ex_share
import com.hopcape.odo.feature.garage.resources.gr_ex_subtitle
import com.hopcape.odo.feature.garage.resources.gr_ex_title
import org.jetbrains.compose.resources.stringResource

/**
 * Export-car-record sheet ([com.hopcape.odo.core.navigation.OdoDestination.Garage.Export]).
 *
 * It lists what is on file and offers the vehicle-details PDF two ways: downloaded, or
 * handed straight to the system share sheet. Both produce the same document; the tapped
 * button shows "Preparing…" while it is laid out, and both go quiet for the duration —
 * a second tap would only render the same document again.
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
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
            ExportButton(
                label = stringResource(Res.string.gr_ex_pdf),
                via = ExportVia.PDF,
                icon = { OdoIcon(IcPdf, contentDescription = null, size = OdoTheme.iconSizes.small) },
                variant = OdoButtonVariant.Secondary,
                state = state,
                onEvent = { onEvent(ExportEvent.PdfTapped) },
                modifier = Modifier.weight(1f),
            )
            ExportButton(
                label = stringResource(Res.string.gr_ex_share),
                via = ExportVia.SHARE,
                icon = { OdoIcon(IcShare, contentDescription = null, size = OdoTheme.iconSizes.small) },
                variant = OdoButtonVariant.Primary,
                state = state,
                onEvent = { onEvent(ExportEvent.ShareTapped) },
                modifier = Modifier.weight(1f),
            )
        }
        if (state.export is ExportProgress.Failed) {
            OdoText(
                text = stringResource(Res.string.gr_ex_failed),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.danger,
            )
        }
    }
}

/**
 * One of the two ways out. The tapped button says "Preparing…" while the document is laid
 * out; both are disabled until the car has loaded and while a render is in flight.
 */
@Composable
private fun ExportButton(
    label: String,
    via: ExportVia,
    icon: @Composable () -> Unit,
    variant: OdoButtonVariant,
    state: ExportUiState,
    onEvent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRendering = (state.export as? ExportProgress.Rendering)?.via == via
    OdoButton(
        text = if (isRendering) stringResource(Res.string.gr_ex_preparing) else label,
        onClick = onEvent,
        modifier = modifier,
        variant = variant,
        enabled = state.car.valueOrNull != null && !state.isBusy,
        leadingIcon = icon,
    )
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

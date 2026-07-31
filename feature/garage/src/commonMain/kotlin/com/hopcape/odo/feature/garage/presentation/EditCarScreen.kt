package com.hopcape.odo.feature.garage.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoInputField
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoRegistrationNumberField
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcInfo
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.garage.presentation.state.Loadable
import com.hopcape.odo.feature.garage.presentation.state.text
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_cd_close
import com.hopcape.odo.feature.garage.resources.gr_ec_nickname
import com.hopcape.odo.feature.garage.resources.gr_ec_nickname_hint
import com.hopcape.odo.feature.garage.resources.gr_ec_odo_note
import com.hopcape.odo.feature.garage.resources.gr_ec_reg
import com.hopcape.odo.feature.garage.resources.gr_ec_save
import com.hopcape.odo.feature.garage.resources.gr_ec_title
import org.jetbrains.compose.resources.stringResource

/**
 * Edit-car full screen ([com.hopcape.odo.core.navigation.OdoDestination.Garage.EditCar]).
 *
 * Make, model, year and fuel go through the design system's pickers via [CarDetailFields],
 * and the plate through [OdoRegistrationNumberField] — the same controls onboarding uses,
 * so a car described at setup and a car edited later are described the same way.
 *
 * Odometer is deliberately **not** editable here. It is Odo's audit-trail number (₹/km,
 * health score, km-anomaly checks), so it changes through exactly one door — the garage
 * card's update sheet — and this screen only says so.
 */
@Composable
internal fun EditCarScreen(state: EditCarUiState, onEvent: (EditCarEvent) -> Unit) {
    OdoScreen(
        topBar = {
            CloseTopBar(
                title = stringResource(Res.string.gr_ec_title),
                closeLabel = stringResource(Res.string.gr_cd_close),
                onClose = { onEvent(EditCarEvent.CloseTapped) },
            )
        },
        bottomBar = {
            GarageBottomButton(
                label = stringResource(Res.string.gr_ec_save),
                onClick = { onEvent(EditCarEvent.SaveTapped) },
                enabled = state.form is Loadable.Ready && !state.submission.isInFlight,
            )
        },
    ) { padding ->
        when (val form = state.form) {
            Loadable.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { OdoLoadingIndicator() }

            is Loadable.Failed -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                OdoText(
                    form.message.asString(),
                    style = OdoTheme.typography.body,
                    color = OdoTheme.colors.danger,
                )
            }

            is Loadable.Ready -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(vertical = OdoTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
            ) {
                CarDetailFields(
                    fields = form.value,
                    options = state.options,
                    onMakeSelected = { onEvent(EditCarEvent.MakeSelected(it)) },
                    onModelSelected = { onEvent(EditCarEvent.ModelSelected(it)) },
                    onYearSelected = { onEvent(EditCarEvent.YearSelected(it)) },
                    onFuelSelected = { onEvent(EditCarEvent.FuelSelected(it)) },
                )
                OdoRegistrationNumberField(
                    modifier = Modifier.testTag(GarageTestTags.REGISTRATION_FIELD),
                    value = form.value.registration.text,
                    onValueChange = { onEvent(EditCarEvent.PlateChanged(it)) },
                    label = stringResource(Res.string.gr_ec_reg),
                )
                OdoInputField(
                    modifier = Modifier.testTag(GarageTestTags.NICKNAME_FIELD),
                    value = form.value.nickname.text,
                    onValueChange = { onEvent(EditCarEvent.NicknameChanged(it)) },
                    label = stringResource(Res.string.gr_ec_nickname),
                    placeholder = stringResource(Res.string.gr_ec_nickname_hint),
                )
                OdometerNote()
                state.submission.error?.let { message ->
                    OdoText(
                        message.asString(),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.danger,
                    )
                }
            }
        }
    }
}

@Composable
private fun OdometerNote() {
    OdoCard(color = OdoTheme.colors.surfaceRaised) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoIcon(
                IcInfo,
                contentDescription = null,
                tint = OdoTheme.colors.textDim,
                size = OdoTheme.iconSizes.medium,
            )
            OdoText(
                stringResource(Res.string.gr_ec_odo_note),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }
    }
}

@OdoThemePreviews
@Composable
private fun EditCarPreview() = OdoPreview(padded = false) {
    EditCarScreen(state = sampleEditCar(), onEvent = {})
}

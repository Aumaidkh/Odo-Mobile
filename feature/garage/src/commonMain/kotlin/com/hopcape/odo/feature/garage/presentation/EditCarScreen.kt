package com.hopcape.odo.feature.garage.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoCarMake
import com.hopcape.odo.core.designsystem.component.OdoCarModel
import com.hopcape.odo.core.designsystem.component.OdoFuelKind
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoInputField
import com.hopcape.odo.core.designsystem.component.OdoRegistrationNumberField
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcInfo
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_cd_close
import com.hopcape.odo.feature.garage.resources.gr_ec_nickname
import com.hopcape.odo.feature.garage.resources.gr_ec_nickname_hint
import com.hopcape.odo.feature.garage.resources.gr_ec_odo_note
import com.hopcape.odo.feature.garage.resources.gr_ec_reg
import com.hopcape.odo.feature.garage.resources.gr_ec_save
import com.hopcape.odo.feature.garage.resources.gr_ec_title
import com.hopcape.odo.feature.garage.resources.gr_ec_vin
import org.jetbrains.compose.resources.stringResource

/**
 * Edit-car full screen ([com.hopcape.odo.core.navigation.OdoDestination.Garage.EditCar]).
 * Holds the form state itself (sample-seeded); [onSave] / [onClose] pop back to the garage.
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
internal fun EditCarScreen(onClose: () -> Unit, onSave: () -> Unit) {
    var make by remember { mutableStateOf<OdoCarMake?>(sampleMakes.first()) }
    var model by remember { mutableStateOf<OdoCarModel?>(sampleModels(sampleMakes.first()).first()) }
    var year by remember { mutableStateOf<Int?>(2020) }
    var fuel by remember { mutableStateOf<OdoFuelKind?>(OdoFuelKind.PETROL) }
    var reg by remember { mutableStateOf("MH12AB1234") }
    var vin by remember { mutableStateOf("MA3ERLF1S00123456") }
    var nickname by remember { mutableStateOf("") }

    OdoScreen(
        topBar = { CloseTopBar(stringResource(Res.string.gr_ec_title), stringResource(Res.string.gr_cd_close), onClose) },
        bottomBar = { GarageBottomButton(stringResource(Res.string.gr_ec_save), onSave) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            CarDetailFields(
                make = make,
                // A different brand invalidates the model — clear it rather than leave a
                // Hyundai trim sitting under "Tata".
                onMakeChange = { make = it; model = null },
                model = model,
                onModelChange = { model = it },
                year = year,
                onYearChange = { year = it },
                fuel = fuel,
                onFuelChange = { fuel = it },
            )
            OdoRegistrationNumberField(
                value = reg,
                onValueChange = { reg = it },
                label = stringResource(Res.string.gr_ec_reg),
            )
            OdoInputField(value = vin, onValueChange = { vin = it }, label = stringResource(Res.string.gr_ec_vin))
            OdoInputField(
                value = nickname,
                onValueChange = { nickname = it },
                label = stringResource(Res.string.gr_ec_nickname),
                placeholder = stringResource(Res.string.gr_ec_nickname_hint),
            )
            OdoCard(color = OdoTheme.colors.surfaceRaised) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OdoIcon(IcInfo, contentDescription = null, tint = OdoTheme.colors.textDim, size = OdoTheme.iconSizes.medium)
                    OdoText(
                        stringResource(Res.string.gr_ec_odo_note),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.textDim,
                    )
                }
            }
        }
    }
}

@OdoThemePreviews
@Composable
private fun EditCarPreview() = OdoPreview(padded = false) {
    EditCarScreen(onClose = {}, onSave = {})
}

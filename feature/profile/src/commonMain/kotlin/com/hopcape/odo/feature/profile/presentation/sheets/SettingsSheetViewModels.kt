package com.hopcape.odo.feature.profile.presentation.sheets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.domain.cost.fuel.FuelEfficiencyUnit
import com.hopcape.odo.core.domain.settings.model.ThemePreference
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.domain.shared.DistanceUnit
import com.hopcape.odo.feature.profile.domain.usecase.UpdateSettingsUseCase
import com.hopcape.odo.feature.profile.presentation.ProfileTelemetry
import com.hopcape.odo.feature.profile.presentation.toProfileMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the appearance sheet.
 *
 * Writes on every choice rather than on "Done": the theme changes the whole app as soon as
 * it is picked, so a choice that waited for a button would show one thing and mean another.
 */
internal class AppearanceViewModel(
    settings: AppSettingsRepository,
    private val updateSettings: UpdateSettingsUseCase,
    private val telemetry: ProfileTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(AppearanceUiState())
    val state: StateFlow<AppearanceUiState> = _state.asStateFlow()

    init {
        telemetry.settingsOpened(ProfileTelemetry.Screen.APPEARANCE)
        viewModelScope.launch {
            settings.observe()
                .catch { cause -> telemetry.readFailed(ProfileTelemetry.Screen.APPEARANCE, cause) }
                .collect { stored ->
                    _state.update { it.copy(theme = stored.theme, largerText = stored.largerText) }
                }
        }
    }

    fun onEvent(event: AppearanceEvent) = when (event) {
        is AppearanceEvent.ThemeChosen -> save(
            setting = ProfileTelemetry.Setting.THEME,
            value = event.theme.name,
            theme = event.theme,
            largerText = _state.value.largerText,
        )

        is AppearanceEvent.LargerTextToggled -> save(
            setting = ProfileTelemetry.Setting.TEXT_SIZE,
            value = event.enabled.toString(),
            theme = _state.value.theme,
            largerText = event.enabled,
        )
    }

    private fun save(setting: String, value: String, theme: ThemePreference, largerText: Boolean) {
        _state.update { it.copy(error = null) }
        viewModelScope.launch(telemetry.op(ProfileTelemetry.Trace.SAVE_SETTING)) {
            telemetry.settingSave(setting, value) { updateSettings.appearance(theme, largerText) }
                .onLeft { error -> _state.update { it.copy(error = error.toProfileMessage()) } }
        }
    }
}

/**
 * State holder for the units sheet. Same shape as [AppearanceViewModel], and for the same
 * reason: the figures on every other screen restate themselves the moment a unit is picked.
 */
internal class UnitsViewModel(
    settings: AppSettingsRepository,
    private val updateSettings: UpdateSettingsUseCase,
    private val telemetry: ProfileTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(UnitsUiState())
    val state: StateFlow<UnitsUiState> = _state.asStateFlow()

    init {
        telemetry.settingsOpened(ProfileTelemetry.Screen.UNITS)
        viewModelScope.launch {
            settings.observe()
                .catch { cause -> telemetry.readFailed(ProfileTelemetry.Screen.UNITS, cause) }
                .collect { stored ->
                    _state.update {
                        it.copy(
                            distanceUnit = stored.distanceUnit,
                            fuelEfficiencyUnit = stored.fuelEfficiencyUnit,
                        )
                    }
                }
        }
    }

    fun onEvent(event: UnitsEvent) = when (event) {
        is UnitsEvent.DistanceUnitChosen -> save(
            setting = ProfileTelemetry.Setting.DISTANCE_UNIT,
            value = event.unit.name,
            distanceUnit = event.unit,
            fuelEfficiencyUnit = _state.value.fuelEfficiencyUnit,
        )

        is UnitsEvent.FuelEfficiencyUnitChosen -> save(
            setting = ProfileTelemetry.Setting.FUEL_EFFICIENCY_UNIT,
            value = event.unit.name,
            distanceUnit = _state.value.distanceUnit,
            fuelEfficiencyUnit = event.unit,
        )
    }

    private fun save(
        setting: String,
        value: String,
        distanceUnit: DistanceUnit,
        fuelEfficiencyUnit: FuelEfficiencyUnit,
    ) {
        _state.update { it.copy(error = null) }
        viewModelScope.launch(telemetry.op(ProfileTelemetry.Trace.SAVE_SETTING)) {
            telemetry.settingSave(setting, value) {
                updateSettings.units(distanceUnit, fuelEfficiencyUnit)
            }.onLeft { error -> _state.update { it.copy(error = error.toProfileMessage()) } }
        }
    }
}

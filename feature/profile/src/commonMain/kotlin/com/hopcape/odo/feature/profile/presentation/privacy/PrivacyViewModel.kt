package com.hopcape.odo.feature.profile.presentation.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.feature.profile.domain.usecase.UpdatePrivacyUseCase
import com.hopcape.odo.feature.profile.presentation.ProfileTelemetry
import com.hopcape.odo.feature.profile.presentation.toProfileMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the privacy screen.
 *
 * Reads from two places because the three switches are stored in two places: the device's
 * settings hold what this phone keeps, and the profile holds what the account permits. The
 * screen shows them as one list, which is the right thing for an owner and the wrong thing
 * to model as one row.
 *
 * Every switch writes immediately — no Save button, so a toggle that only lived in memory
 * would be lost on the way back.
 *
 * Turning analytics off takes effect here rather than at the next launch: [analytics] is told
 * the moment the switch moves, so the very next screen is not counted. Doing it only on
 * restart would mean an owner who opts out keeps being tracked for the rest of the session,
 * which is the one outcome this screen exists to prevent.
 */
internal class PrivacyViewModel(
    settings: AppSettingsRepository,
    profiles: OwnerProfileRepository,
    private val updatePrivacy: UpdatePrivacyUseCase,
    private val analytics: AnalyticsTracker,
    private val telemetry: ProfileTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(PrivacyUiState())
    val state: StateFlow<PrivacyUiState> = _state.asStateFlow()

    init {
        telemetry.settingsOpened(ProfileTelemetry.Screen.PRIVACY)
        viewModelScope.launch {
            settings.observe()
                .catch { cause -> telemetry.readFailed(ProfileTelemetry.Screen.PRIVACY, cause) }
                .collect { stored ->
                    _state.update {
                        it.copy(
                            keepTripRoutes = stored.privacy.keepTripRoutes,
                            usageAnalytics = stored.privacy.usageAnalytics,
                        )
                    }
                }
        }
        viewModelScope.launch {
            profiles.observe()
                .catch { cause -> telemetry.readFailed(ProfileTelemetry.Screen.PRIVACY, cause) }
                // A device with no profile row yet shows the default rather than nothing:
                // the switch has a real answer (on) even before onboarding writes the row.
                .collect { profile ->
                    _state.update { it.copy(sharePrices = profile?.sharesPricesAnonymously ?: true) }
                }
        }
    }

    fun onEvent(event: PrivacyEvent) {
        _state.update { it.copy(error = null) }
        when (event) {
            is PrivacyEvent.SharePricesToggled -> write(
                setting = ProfileTelemetry.Setting.SHARE_PRICES,
                enabled = event.enabled,
            ) { updatePrivacy.sharePrices(event.enabled) }

            is PrivacyEvent.KeepTripRoutesToggled -> write(
                setting = ProfileTelemetry.Setting.KEEP_TRIP_ROUTES,
                enabled = event.enabled,
            ) { updatePrivacy.keepTripRoutes(event.enabled) }

            is PrivacyEvent.UsageAnalyticsToggled -> write(
                setting = ProfileTelemetry.Setting.USAGE_ANALYTICS,
                enabled = event.enabled,
            ) {
                updatePrivacy.usageAnalytics(event.enabled).onRight {
                    // Only after the write lands. Applying the gate first and then failing to
                    // store it would leave the app's behaviour and the owner's stored answer
                    // disagreeing, with the switch showing the stored one.
                    analytics.setConsent(
                        if (event.enabled) ConsentStatus.GRANTED else ConsentStatus.DENIED,
                    )
                }
            }
        }
    }

    /**
     * Run a switch's write under the shared settings span, and surface a failure.
     *
     * The state is deliberately not updated on success: both reads above are live, so the
     * switch follows what was actually stored. That is what makes a failed write spring the
     * switch back instead of leaving it showing a change that did not happen.
     */
    private fun <T> write(
        setting: String,
        enabled: Boolean,
        save: suspend () -> Either<DomainError, T>,
    ) {
        viewModelScope.launch(telemetry.op(ProfileTelemetry.Trace.SAVE_SETTING)) {
            telemetry.settingSave(setting, enabled.toString()) { save() }
                .onLeft { error -> _state.update { it.copy(error = error.toProfileMessage()) } }
        }
    }
}

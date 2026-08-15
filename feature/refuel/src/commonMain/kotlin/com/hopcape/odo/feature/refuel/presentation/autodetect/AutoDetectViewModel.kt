package com.hopcape.odo.feature.refuel.presentation.autodetect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.cost.model.FillEntrySource
import com.hopcape.odo.core.domain.refuel.PaymentApps
import com.hopcape.odo.core.domain.refuel.RefuelDetectionStore
import com.hopcape.odo.core.platform.notification.BackgroundStartAccess
import com.hopcape.odo.core.platform.notification.NotificationAccess
import com.hopcape.odo.feature.refuel.domain.usecase.CountDetectedFillsUseCase
import com.hopcape.odo.feature.refuel.presentation.RefuelTelemetry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the auto-detect opt-in and the settings behind it.
 *
 * The screen's obligation is to be revocable and honest, so this reads the OS permission
 * separately from Odo's own switch and re-reads it every time the screen resumes. The owner
 * can revoke notification access from system settings without ever coming back here, and a
 * screen that kept showing "on" would be claiming something is being read when nothing is.
 *
 * Turning detection off never deletes anything. The fills it produced are the owner's record
 * of tanks they really bought, and a settings switch that quietly erased history would make
 * every other switch in the app untrustworthy.
 */
internal class AutoDetectViewModel(
    private val store: RefuelDetectionStore,
    private val access: NotificationAccess,
    private val backgroundStart: BackgroundStartAccess,
    private val activeCar: ActiveCarProvider,
    private val countDetected: CountDetectedFillsUseCase,
    private val telemetry: RefuelTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(AutoDetectUiState())
    val state: StateFlow<AutoDetectUiState> = _state.asStateFlow()

    private val _effects = Channel<AutoDetectEffect>(Channel.BUFFERED)
    val effects: Flow<AutoDetectEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch(telemetry.op(OP_LOAD)) {
            combine(
                store.observeSettings(),
                store.observeApps(),
                store.observeIgnoredMerchants(),
            ) { settings, apps, ignored ->
                Triple(settings, apps, ignored)
            }.collect { (settings, apps, ignored) ->
                _state.update {
                    it.copy(
                        loading = false,
                        optedIn = settings.detectEnabled,
                        accessGranted = access.isGranted(),
                        // Their acknowledgement is the only signal there is: no API reports
                        // whether the manufacturer's switch is on.
                        needsAutostart = backgroundStart.needsAttention() &&
                            !settings.autostartAcknowledged,
                        confirmBeforeLog = settings.confirmBeforeLog,
                        predictOdometer = settings.predictOdometer,
                        // The debug shell entry exists so `adb` can drive the flow; it is not
                        // an app anyone installed and has no business in a list the owner is
                        // being asked to trust.
                        apps = apps.filterNot { it.packageName == PaymentApps.SHELL },
                        ignoredMerchants = ignored,
                    )
                }
            }
        }
        refreshCount()
    }

    fun onEvent(event: AutoDetectEvent) = when (event) {
        is AutoDetectEvent.DetectionToggled -> toggleDetection(event.enabled)

        is AutoDetectEvent.NotifyStatusObserved -> {
            _state.update { it.copy(notifyStatus = event.status) }
        }

        // Only the last step writes anything. The other two hand off to the OS, which the
        // route host performs — this is here so the tap is counted wherever it lands.
        AutoDetectEvent.SetupContinued -> {
            val current = _state.value
            telemetry.setupStepTaken(current.setupStep.name)
            when {
                // Still an Android permission to ask for; the route host performs it.
                !current.permissionsSettled -> Unit

                // Both granted, and this phone holds background starts behind a switch of its
                // own. That page is shown *before* detection is turned on, because a feature
                // announced as working and then silenced by the OS the next time the phone
                // reclaims it is worse than one more screen.
                current.needsAutostart && current.optInPage != AutoDetectOptInPage.Autostart ->
                    _state.update { it.copy(optInPage = AutoDetectOptInPage.Autostart) }

                else -> toggleDetection(true)
            }
        }
        is AutoDetectEvent.AppToggled -> launchAndSave {
            store.setAppEnabled(event.packageName, event.enabled)
        }

        is AutoDetectEvent.ConfirmBeforeLogToggled -> launchAndSave {
            store.saveSettings(store.settings().copy(confirmBeforeLog = event.enabled))
        }

        is AutoDetectEvent.PredictOdometerToggled -> launchAndSave {
            store.saveSettings(store.settings().copy(predictOdometer = event.enabled))
        }

        is AutoDetectEvent.MerchantUnignored -> launchAndSave {
            store.unignoreMerchant(event.key)
        }

        AutoDetectEvent.OptInAdvanced -> {
            _state.update { it.copy(optInPage = AutoDetectOptInPage.Permissions) }
        }

        // One page at a time, so back walks the flow rather than jumping out of it.
        AutoDetectEvent.OptInBacked -> {
            _state.update {
                it.copy(
                    optInPage = when (it.optInPage) {
                        AutoDetectOptInPage.Autostart -> AutoDetectOptInPage.Permissions
                        else -> AutoDetectOptInPage.Why
                    },
                )
            }
        }

        AutoDetectEvent.OpenAccessSettings -> access.open()

        AutoDetectEvent.OpenAutostartSettings -> {
            backgroundStart.open()
            Unit
        }

        AutoDetectEvent.AutostartAcknowledged -> launchAndSave {
            store.saveSettings(store.settings().copy(autostartAcknowledged = true))
        }

        // Coming back from the system page is the only way the permission changes while
        // this screen exists, and nothing tells it that happened.
        AutoDetectEvent.Resumed -> {
            _state.update { it.copy(accessGranted = access.isGranted()) }
            refreshCount()
        }

        AutoDetectEvent.NotNowTapped -> {
            _effects.trySend(AutoDetectEffect.Dismiss)
            Unit
        }
    }

    /**
     * Turn detection on or off.
     *
     * Switching it on also sends the owner to the system page, because Odo's own switch alone
     * detects nothing — leaving them on a screen that says "on" while no notification can be
     * read is the one state this screen exists to avoid.
     */
    private fun toggleDetection(enabled: Boolean) {
        viewModelScope.launch(telemetry.op(OP_TOGGLE)) {
            store.saveSettings(store.settings().copy(detectEnabled = enabled))
            telemetry.detectionToggled(enabled)
            if (enabled && !access.isGranted()) access.open()
        }
    }

    private fun refreshCount() {
        viewModelScope.launch(telemetry.op(OP_COUNT)) {
            val carId = activeCar.activeCarId.value ?: return@launch
            val count = countDetected(carId, FillEntrySource.DETECTED)
            _state.update { it.copy(detectedFillCount = count) }
        }
    }

    private fun launchAndSave(block: suspend () -> Unit) {
        viewModelScope.launch(telemetry.op(OP_SAVE)) { block() }
    }

    private companion object {
        const val OP_LOAD = "refuel_autodetect_load"
        const val OP_TOGGLE = "refuel_autodetect_toggle"
        const val OP_SAVE = "refuel_autodetect_save"
        const val OP_COUNT = "refuel_autodetect_count"
    }
}

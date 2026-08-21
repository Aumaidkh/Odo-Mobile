package com.hopcape.odo.feature.refuel.presentation.autodetect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.cost.model.FillEntrySource
import com.hopcape.odo.core.domain.refuel.PaymentApps
import com.hopcape.odo.core.domain.refuel.RefuelDetectionStore
import com.hopcape.odo.core.platform.notification.BackgroundStartAccess
import com.hopcape.odo.core.platform.notification.NotificationAccess
import com.hopcape.odo.core.platform.permission.PermissionStatus
import com.hopcape.odo.core.platform.permission.isGranted
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
 * The screen's obligation is to be revocable and honest, so this reads the OS permissions
 * separately from Odo's own switch and re-reads them every time the screen resumes. The owner
 * can revoke notification access from system settings without ever coming back here, and a
 * screen that kept showing "on" would be claiming something is being read when nothing is.
 *
 * Turning detection off never deletes anything. The fills it produced are the owner's record of
 * tanks they really bought, and a settings switch that quietly erased history would make every
 * other switch in the app untrustworthy.
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
                        autostartAcknowledged = settings.autostartAcknowledged,
                        // The standing reminder in the settings body is only worth its nagging
                        // on the skins that refuse background starts outright.
                        needsAutostart = backgroundStart.needsAttention() &&
                            !settings.autostartAcknowledged,
                        confirmBeforeLog = settings.confirmBeforeLog,
                        predictOdometer = settings.predictOdometer,
                        // The debug shell entry exists so `adb` can drive the flow; it is not an
                        // app anyone installed and has no business in a list the owner is being
                        // asked to trust.
                        apps = apps.filterNot { app -> app.packageName == PaymentApps.SHELL },
                        ignoredMerchants = ignored,
                    )
                }
            }
        }
        refreshCount()
    }

    fun onEvent(event: AutoDetectEvent) {
        when (event) {
            is AutoDetectEvent.DetectionToggled -> toggleDetection(event.enabled)

            is AutoDetectEvent.NotifyStatusObserved -> onNotifyStatus(event.status)

            AutoDetectEvent.ContinueTapped -> advance()

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

            // One page at a time, so back walks the flow rather than jumping out of it. A null
            // previous page means this is the first, and the route host takes it from here.
            AutoDetectEvent.BackTapped -> {
                val previous = _state.value.previousPage
                if (previous == null) {
                    _effects.trySend(AutoDetectEffect.Dismiss)
                } else {
                    _state.update { it.copy(page = previous) }
                }
            }

            // Both Android permissions are already granted by the time this button exists, so the
            // only honest thing it can do is turn detection on. What it skips is the walk to a
            // settings page nothing can read back anyway.
            AutoDetectEvent.BackgroundSkipped -> {
                telemetry.setupStepTaken(SKIPPED_BACKGROUND)
                toggleDetection(true)
            }

            AutoDetectEvent.OpenAccessSettings -> access.open()

            AutoDetectEvent.NotifyFixTapped -> {
                val blocked = _state.value.notifyBlocked
                _effects.trySend(AutoDetectEffect.RequestNotifyPermission(blocked))
            }

            AutoDetectEvent.OpenAutostartSettings -> backgroundStart.open()

            AutoDetectEvent.AutostartAcknowledged -> acknowledgeAutostart()

            // Coming back from a system page is the only way a permission changes while this screen
            // exists, and nothing tells it that happened.
            AutoDetectEvent.Resumed -> {
                _state.update { it.copy(accessGranted = access.isGranted()) }
                resumeFlow()
                refreshCount()
            }

            AutoDetectEvent.NotNowTapped -> _effects.trySend(AutoDetectEffect.Dismiss)
        }
    }

    /**
     * `POST_NOTIFICATIONS` as the OS now reports it.
     *
     * Recorded, not acted on. The flow never waits for this one: the dialog is raised on the way
     * out of the pitch page and the owner answers it over the top of step one, so an answer
     * either way leaves them exactly where they already were. What it is kept for is the settings
     * body, which has to be able to say that detection is on and nothing can reach them.
     */
    private fun onNotifyStatus(status: PermissionStatus) {
        _state.update { it.copy(notifyStatus = status) }
    }

    /**
     * The primary button, on whichever page it was pressed.
     *
     * Some of the pages hand off to something outside this class — the permission dialog needs
     * the Activity, and the settings pages need an Intent — so those branches emit or delegate
     * as well as, or instead of, changing the page. Everything else is a step forward through the
     * explanation.
     */
    private fun advance() {
        val current = _state.value
        telemetry.setupStepTaken(current.page.name)
        when (current.page) {
            // Fixing the step list here is what makes the counter stable for the rest of the
            // run. An owner with nothing left to grant never sees a step at all.
            AutoDetectPage.Why -> {
                // The notifications dialog rides along with leaving this page rather than
                // getting a numbered screen of its own. The drawn notification just above the
                // button is the whole of its explanation; a screen repeating it made the flow
                // read as one ask longer than it is.
                if (current.notifyAskPending) {
                    _effects.trySend(AutoDetectEffect.RequestNotifyPermission(blocked = false))
                }
                val pending = current.pendingSteps
                if (pending.isEmpty()) {
                    toggleDetection(true)
                } else {
                    _state.update { it.copy(steps = pending, page = pending.first().firstPage) }
                }
            }

            AutoDetectPage.Access -> _state.update { it.copy(page = AutoDetectPage.AccessHandoff) }

            AutoDetectPage.AccessHandoff -> access.open()

            AutoDetectPage.Background ->
                _state.update { it.copy(page = AutoDetectPage.BackgroundHandoff) }

            AutoDetectPage.BackgroundHandoff -> {
                backgroundStart.open()
            }
        }
    }

    /**
     * What to do with a flow that was left mid-step and has just come back.
     *
     * The two settings pages are the reason this exists: the owner leaves the app on those and
     * returns with the answer already given, and no callback says so. Notification access can be
     * read back, so it is checked. The background setting cannot be read at all — going to the
     * page is the most that can ever be known about it, so returning from it counts.
     */
    private fun resumeFlow() {
        val current = _state.value
        if (current.optedIn) return
        when (current.page) {
            AutoDetectPage.Access, AutoDetectPage.AccessHandoff ->
                if (current.accessGranted) completeStep(AutoDetectStep.Access)

            AutoDetectPage.BackgroundHandoff -> {
                acknowledgeAutostart()
                completeStep(AutoDetectStep.Background)
            }

            else -> Unit
        }
    }

    /**
     * Move past a step that is now satisfied — to the next ask, or to detection being on.
     *
     * The end of the flow is the only place detection is switched on by the opt-in, so an owner
     * who backed out halfway is never left with a feature they did not finish agreeing to.
     */
    private fun completeStep(step: AutoDetectStep) {
        val next = _state.value.pageAfter(step)
        if (next == null) toggleDetection(true) else _state.update { it.copy(page = next) }
    }

    private fun acknowledgeAutostart() = launchAndSave {
        store.saveSettings(store.settings().copy(autostartAcknowledged = true))
    }

    /**
     * Coming back from the phone's own background-start page, which is the end of the flow.
     *
     * Both things it changes are written together on purpose. Acknowledging the setting and
     * turning detection on are two `copy` calls on the same stored settings, and run as separate
     * coroutines they read the same value and the later save silently drops the earlier one's
     * field — which showed up as the autostart reminder coming back for an owner who had just
     * been walked through it.
     */
    private fun finishBackgroundStep() {
        viewModelScope.launch(telemetry.op(OP_TOGGLE)) {
            val next = _state.value.pageAfter(AutoDetectStep.Background)
            val settings = store.settings()
            if (next == null) {
                store.saveSettings(
                    settings.copy(autostartAcknowledged = true, detectEnabled = true),
                )
                telemetry.detectionToggled(true)
                if (!access.isGranted()) access.open()
            } else {
                store.saveSettings(settings.copy(autostartAcknowledged = true))
                _state.update { it.copy(page = next) }
            }
        }
    }

    /**
     * Turn detection on or off.
     *
     * Switching it on also sends the owner to the system page when access is somehow still
     * missing, because Odo's own switch alone detects nothing — leaving them on a screen that
     * says "on" while no notification can be read is the one state this screen exists to avoid.
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
        const val SKIPPED_BACKGROUND = "BackgroundSkipped"
    }
}

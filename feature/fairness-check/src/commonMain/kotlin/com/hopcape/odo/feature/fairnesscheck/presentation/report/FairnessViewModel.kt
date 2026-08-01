package com.hopcape.odo.feature.fairnesscheck.presentation.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.fairness.analysis.FairnessAnalyzer
import com.hopcape.odo.core.domain.fairness.model.FairnessQuery
import com.hopcape.odo.core.domain.fairness.model.FairnessQueryItem
import com.hopcape.odo.core.domain.owner.CurrentCityProvider
import com.hopcape.odo.feature.fairnesscheck.resources.Res
import com.hopcape.odo.feature.fairnesscheck.resources.fc_error_check_failed
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the check is about — the lines to benchmark, and the entry they came off when there
 * is one.
 *
 * The route builds this from the navigation key so the ViewModel never sees a nav type, and
 * so a caller with no stored entry (a standalone price check) simply passes no ids.
 */
internal data class FairnessCheckInput(
    val items: List<FairnessQueryItem>,
    val logId: String? = null,
    val carId: String? = null,
)

/**
 * State holder for the fairness report. Holds [FairnessUiState], consumes [FairnessEvent]s,
 * and emits [FairnessEffect]s for the route host to navigate on.
 *
 * The city comes from [CurrentCityProvider], not from the caller: it is the owner's, held on
 * their profile, and a screen that took one as an argument would be a second answer to a
 * question the app already has. With no city there is nothing to benchmark against, and the
 * screen says so rather than showing a verdict built on a guess.
 */
internal class FairnessViewModel(
    private val input: FairnessCheckInput,
    private val analyzer: FairnessAnalyzer,
    private val city: CurrentCityProvider,
    private val telemetry: FairnessTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(FairnessUiState())
    val state: StateFlow<FairnessUiState> = _state.asStateFlow()

    private val _effects = Channel<FairnessEffect>(Channel.BUFFERED)
    val effects: Flow<FairnessEffect> = _effects.receiveAsFlow()

    init {
        check()
    }

    fun onEvent(event: FairnessEvent) = when (event) {
        FairnessEvent.ReportTapped -> report()

        FairnessEvent.DoneTapped -> send(FairnessEffect.NavigateBack)

        FairnessEvent.SetCityTapped -> {
            telemetry.setCityTapped()
            send(FairnessEffect.OpenProfile)
        }

        FairnessEvent.RetryTapped -> check()
    }

    /**
     * Run the check.
     *
     * A thrown lookup becomes [FairnessUiState.Content.Failed] with a retry rather than an
     * empty report: the repository already turns an unreachable source into "no benchmarks",
     * so anything that still throws is broken, and showing it as "no data for your city"
     * would blame the pool for a bug.
     */
    private fun check() {
        _state.update { it.copy(content = FairnessUiState.Content.Loading) }
        viewModelScope.launch(telemetry.op(OP_CHECK)) {
            val city = runCatching { city.currentCity() }.getOrNull()
            if (city == null) {
                telemetry.skippedWithoutCity()
                show(FairnessUiState.Content.NoCity)
                return@launch
            }

            runCatching { analyzer.analyze(FairnessQuery(city = city, items = input.items)) }
                .onSuccess { report ->
                    val content = report.toContent(canReport = input.logId != null && input.carId != null)
                    telemetry.checked(
                        outcome = report.outcome::class.simpleName.orEmpty(),
                        sampleSize = report.sampleSize,
                        lineCount = report.items.size,
                        benchmarkedLines = report.items.count { it.estimate != null },
                    )
                    show(content)
                }
                .onFailure { cause ->
                    telemetry.checkFailed(cause)
                    show(FairnessUiState.Content.Failed(UiText(Res.string.fc_error_check_failed)))
                }
        }
    }

    /**
     * File an overcharge report. Guarded by the ids even though the button only appears with
     * them: an effect that navigates with a blank id is worse than one that does nothing.
     */
    private fun report() {
        val logId = input.logId
        val carId = input.carId
        if (logId == null || carId == null) return
        telemetry.reportTapped()
        send(FairnessEffect.OpenReportOvercharge(logId = logId, carId = carId))
    }

    private fun show(content: FairnessUiState.Content) = _state.update { it.copy(content = content) }

    private fun send(effect: FairnessEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        const val OP_CHECK = "check"
    }
}

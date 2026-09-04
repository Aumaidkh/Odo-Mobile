package com.hopcape.odo.feature.billcheck.presentation.howweknow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.left
import com.hopcape.odo.core.common.runCatchingCancellableSuspend
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.billcheck.domain.BandBasisReader
import com.hopcape.odo.feature.billcheck.presentation.BillCheckTelemetry
import com.hopcape.odo.feature.billcheck.resources.Res
import com.hopcape.odo.feature.billcheck.resources.bc_error
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for "How we know".
 *
 * Its own read rather than the result's, because the sheet is opened per line long after the
 * check ran — asking for the whole bill again to explain one row would be the wrong shape.
 */
internal class BasisViewModel(
    private val billId: String,
    private val lineName: String,
    private val reader: BandBasisReader,
    private val telemetry: BillCheckTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(BasisUiState())
    val state: StateFlow<BasisUiState> = _state.asStateFlow()

    private val _effects = Channel<BasisEffect>(Channel.BUFFERED, BufferOverflow.DROP_OLDEST)
    val effects = _effects.receiveAsFlow()

    private var readJob: Job? = null

    init {
        read()
    }

    fun onEvent(event: BasisEvent) = when (event) {
        BasisEvent.ReportPriceClicked -> report()
        BasisEvent.RetryClicked -> read()
    }

    private fun read() {
        readJob?.cancel()
        _state.update { it.copy(content = BasisUiState.Content.Loading) }
        readJob = viewModelScope.launch(telemetry.op(OP_BASIS)) {
            runCatchingCancellableSuspend { reader.basisFor(billId, lineName) }
                .getOrElse { DomainError.PersistenceFailure().left() }
                .fold(
                    ifLeft = { error ->
                        telemetry.readFailed(error)
                        _state.update {
                            it.copy(content = BasisUiState.Content.Failed(UiText(Res.string.bc_error)))
                        }
                    },
                    ifRight = { basis ->
                        _state.update { it.copy(content = BasisUiState.Content.Ready(basis)) }
                    },
                )
        }
    }

    private fun report() {
        telemetry.wrongPriceReported()
        _effects.trySend(BasisEffect.ReportPrice)
    }

    private companion object {
        const val OP_BASIS = "basis"
    }
}

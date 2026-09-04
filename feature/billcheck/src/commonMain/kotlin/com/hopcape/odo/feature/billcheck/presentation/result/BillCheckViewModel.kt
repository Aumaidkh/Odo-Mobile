package com.hopcape.odo.feature.billcheck.presentation.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.left
import com.hopcape.odo.core.common.runCatchingCancellableSuspend
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.billcheck.domain.BillCheck
import com.hopcape.odo.feature.billcheck.domain.BillCheckReader
import com.hopcape.odo.feature.billcheck.domain.Reason
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
 * State holder for the bill check result.
 *
 * The check itself runs behind [BillCheckReader]. Whether the answer is shown or masked is a
 * separate question and is asked here rather than there: the reader's job is what the bill
 * says, and no reader should have to know what the owner has paid for.
 */
internal class BillCheckViewModel(
    private val billId: String,
    private val reader: BillCheckReader,
    /**
     * Whether the owner has a check to spend — free or bought. Suspend and asked per read,
     * not held: a purchase made from the sheet this screen opens has to change the answer
     * without the screen being rebuilt.
     */
    private val unlocked: suspend () -> Boolean,
    private val telemetry: BillCheckTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(BillCheckUiState())
    val state: StateFlow<BillCheckUiState> = _state.asStateFlow()

    private val _effects = Channel<BillCheckEffect>(Channel.BUFFERED, BufferOverflow.DROP_OLDEST)
    val effects = _effects.receiveAsFlow()

    /** The in-flight read, held so a retry cannot be overtaken by the attempt it replaced. */
    private var readJob: Job? = null

    init {
        read()
    }

    fun onEvent(event: BillCheckEvent) = when (event) {
        BillCheckEvent.BackClicked -> emit(BillCheckEffect.NavigateBack)
        is BillCheckEvent.ShareClicked -> share(event.text)
        BillCheckEvent.HowWeKnowClicked -> openBasis()
        BillCheckEvent.AddLastBillClicked -> addLastBill()
        BillCheckEvent.UnlockClicked -> openOffers()
        BillCheckEvent.RetryClicked -> read()
        BillCheckEvent.Resumed -> refreshLock()
    }

    private fun read() {
        readJob?.cancel()
        _state.update { it.copy(content = BillCheckUiState.Content.Loading) }
        readJob = viewModelScope.launch(telemetry.op(OP_READ)) {
            val span = telemetry.readStarted()
            // A throw is the same outcome as a refusal here: the screen has one failure
            // state, and it is the read that failed either way. Closed in a `finally`
            // because backing out mid-read cancels this coroutine, and a span left open
            // reads as an operation that never finished.
            val result = try {
                runCatchingCancellableSuspend { reader.read(billId) }
                    .getOrElse { DomainError.PersistenceFailure().left() }
            } finally {
                telemetry.readEnded(span)
            }

            result.fold(
                ifLeft = { failed(it) },
                ifRight = { show(it, locked = !unlocked()) },
            )
        }
    }

    private fun show(check: BillCheck, locked: Boolean) {
        telemetry.resultShown(
            flagged = check.flagged.size,
            lines = check.lineCount,
            locked = locked,
        )
        _state.update { it.copy(content = BillCheckUiState.Content.Ready(check, locked)) }
        // The offer is made where the pain is, with the estimate still in the owner's hand
        // (AI_ADVISORY_PLAN D3) — not in a settings screen an hour later.
        if (locked) openOffers()
    }

    /**
     * Ask again whether the owner may see the findings, without re-reading the bill.
     *
     * Only ever unmasks. A check spent elsewhere while this screen sat behind a sheet must
     * not take back an answer already on screen — the owner paid for this reading of this
     * bill, not for a subscription to it.
     */
    private fun refreshLock() {
        val ready = _state.value.content as? BillCheckUiState.Content.Ready ?: return
        if (!ready.locked) return
        viewModelScope.launch(telemetry.op(OP_LOCK)) {
            if (!unlocked()) return@launch
            telemetry.resultShown(
                flagged = ready.check.flagged.size,
                lines = ready.check.lineCount,
                locked = false,
            )
            _state.update { it.copy(content = ready.copy(locked = false)) }
        }
    }

    private fun failed(error: DomainError) {
        telemetry.readFailed(error)
        _state.update {
            it.copy(content = BillCheckUiState.Content.Failed(UiText(Res.string.bc_error)))
        }
    }

    private fun share(text: String) {
        telemetry.shareClicked()
        emit(BillCheckEffect.Share(text))
    }

    /**
     * "How we know" explains a band, so it opens on the first line that has one.
     *
     * A schedule question has no band to explain — its claim is about the maker, not about a
     * price — so it is skipped rather than opening a sheet with nothing in it.
     */
    private fun openBasis() {
        val line = _state.value.check
            ?.flagged
            ?.firstOrNull { it.reason is Reason.AboveBand }
            ?: return
        telemetry.basisOpened()
        emit(BillCheckEffect.OpenBasis(line.name))
    }

    private fun openOffers() {
        telemetry.offersOpened()
        emit(BillCheckEffect.OpenOffers)
    }

    private fun addLastBill() {
        telemetry.addLastBillClicked()
        emit(BillCheckEffect.AddLastBill)
    }

    private fun emit(effect: BillCheckEffect) {
        _effects.trySend(effect)
    }

    private companion object {
        const val OP_READ = "read"
        const val OP_LOCK = "lock"
    }
}

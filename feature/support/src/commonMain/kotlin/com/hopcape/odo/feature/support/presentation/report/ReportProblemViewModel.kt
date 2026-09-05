package com.hopcape.odo.feature.support.presentation.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.domain.support.TicketDetail
import com.hopcape.odo.core.domain.support.TicketKind
import com.hopcape.odo.feature.support.domain.ReplyAddress
import com.hopcape.odo.feature.support.domain.maskEmail
import com.hopcape.odo.feature.support.domain.usecase.PickedFile
import com.hopcape.odo.feature.support.domain.usecase.SubmitTicketUseCase
import com.hopcape.odo.feature.support.presentation.SupportTelemetry
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the screen does once the send goes through. */
internal sealed interface ReportEffect {

    data object NavigateBack : ReportEffect

    /** Pick a screenshot. The picker is a platform seam the route owns. */
    data object PickAttachment : ReportEffect

    /**
     * Saved. [reference] is derived from the ticket's own id, so it exists with no signal and
     * is the same code the panel shows against the row.
     */
    data class Sent(
        val reference: String,
        val area: String,
        val photos: Int,
        val logsAttached: Boolean,
        val maskedReplyTo: String,
    ) : ReportEffect
}

/**
 * A problem report, from the first chip to a saved row.
 *
 * **The reply address decides the shape of the form**, so it is read before anything else: an
 * account with one gets a line saying where the answer goes, an account without gets the field
 * that asks. Reading it fails quietly — a form that will not open because a profile could not
 * be read is worse than a form that asks for an address it could have known.
 */
internal class ReportProblemViewModel(
    private val submit: SubmitTicketUseCase,
    private val replyAddress: ReplyAddress,
    /**
     * Opens a diagnostics request and answers with its reference.
     *
     * A function rather than the use case, for the same reason the bill check takes its
     * allowance that way: what this needs is one answer, and naming the class here would make
     * a test build four fakes to get it.
     */
    private val requestDiagnostics: suspend () -> String,
    private val telemetry: SupportTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    private val _effects = Channel<ReportEffect>(Channel.BUFFERED, BufferOverflow.DROP_OLDEST)
    val effects = _effects.receiveAsFlow()

    /** Held unmasked for the ticket; only the mask reaches the screen or a navigation key. */
    private var accountEmail: String? = null

    init {
        viewModelScope.launch {
            accountEmail = replyAddress.current()
            val masked = accountEmail?.let(::maskEmail).orEmpty()
            // Marked loaded either way. A profile that answered "no address" and one that has
            // not answered yet are the same blank, and only this flag separates them.
            _state.update { it.copy(maskedEmail = masked, profileLoaded = true) }
        }
    }

    fun onEvent(event: ReportEvent) {
        when (event) {
            ReportEvent.BackClicked -> emit(ReportEffect.NavigateBack)
            is ReportEvent.AreaPicked -> _state.update { it.copy(area = event.area) }
            is ReportEvent.MessageChanged -> _state.update { it.copy(message = event.message) }
            is ReportEvent.AttachLogsToggled -> _state.update { it.copy(attachLogs = event.on) }
            is ReportEvent.EmailChanged ->
                _state.update { it.copy(email = event.email, emailInvalid = false) }
            ReportEvent.AddAttachmentClicked -> emit(ReportEffect.PickAttachment)
            is ReportEvent.AttachmentPicked -> _state.update {
                it.copy(attachments = it.attachments + ReportAttachment(event.ref, event.name))
            }
            is ReportEvent.AttachmentRemoved -> _state.update {
                it.copy(attachments = it.attachments.filterNot { held -> held.ref == event.ref })
            }
            ReportEvent.SendClicked -> send()
        }
    }

    private fun send() {
        val current = _state.value
        // The button is disabled until then, so this only catches a send raised some other
        // way — but the address it would file is one nobody chose.
        if (!current.profileLoaded) return
        if (!current.emailLooksValid()) {
            _state.update { it.copy(emailInvalid = true) }
            return
        }
        _state.update { it.copy(sending = true) }

        viewModelScope.launch {
            val replyTo = accountEmail ?: current.email
            submit(
                kind = TicketKind.PROBLEM,
                body = current.message,
                details = mapOf(TicketDetail.AREA to current.area.name),
                picked = current.attachments.map { PickedFile(ref = it.ref, name = it.name) },
                replyTo = replyTo,
                // Opened before the ticket is built, because the ticket carries the code.
                // The switch says "helps us find it faster"; this is what makes that true.
                diagnosticsReference = if (current.attachLogs) requestDiagnostics() else null,
            ).fold(
                ifLeft = { error ->
                    telemetry.submitFailed(TicketKind.PROBLEM, error)
                    _state.update { it.copy(sending = false, failed = true) }
                },
                ifRight = { ticket ->
                    telemetry.ticketSubmitted(
                        kind = TicketKind.PROBLEM,
                        attachments = current.attachments.size,
                        logsAttached = current.attachLogs,
                    )
                    _state.update { it.copy(sending = false) }
                    emit(
                        ReportEffect.Sent(
                            reference = ticket.reference,
                            area = current.area.name,
                            photos = current.attachments.size,
                            logsAttached = current.attachLogs,
                            // Masked here, not on the confirmation: the address must not
                            // travel as a navigation argument, which is written to saved state.
                            maskedReplyTo = maskEmail(replyTo),
                        ),
                    )
                },
            )
        }
    }

    private fun emit(effect: ReportEffect) {
        _effects.trySend(effect)
    }
}

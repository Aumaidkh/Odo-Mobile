package com.hopcape.odo.feature.support.presentation.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.common.runCatchingCancellableSuspend
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
            is ReportEvent.MessageChanged ->
                _state.update { it.copy(message = event.message, failed = false) }
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
        // Two taps delivered before the button redraws are two tickets, two references and
        // two diagnostics requests for one report. The button being disabled is what the
        // owner sees; this is what makes it true.
        if (current.sending) return
        // The button is disabled until then, so this only catches a send raised some other
        // way — but the address it would file is one nobody chose.
        if (!current.profileLoaded) return
        if (!current.emailLooksValid()) {
            _state.update { it.copy(emailInvalid = true) }
            return
        }
        // Cleared here, not left over: a save that failed and then succeeded must not leave
        // the screen saying it did not.
        _state.update { it.copy(sending = true, failed = false) }

        viewModelScope.launch {
            val replyTo = accountEmail ?: current.email
            telemetry.timingSubmit {
                submit(
                    kind = TicketKind.PROBLEM,
                    body = current.message,
                    details = mapOf(TicketDetail.AREA to current.area.name),
                    picked = current.attachments.map { PickedFile(ref = it.ref, name = it.name) },
                    replyTo = replyTo,
                    // Opened before the ticket is built, because the ticket carries the code.
                    // The switch says "helps us find it faster"; this makes that true.
                    diagnosticsReference = if (current.attachLogs) diagnosticsReference() else null,
                )
            }.fold(
                ifLeft = { error ->
                    telemetry.submitFailed(TicketKind.PROBLEM, error)
                    _state.update { it.copy(sending = false, failed = true) }
                },
                ifRight = { ticket ->
                    telemetry.ticketSubmitted(
                        kind = TicketKind.PROBLEM,
                        // What was stored, never what was picked. A file that would not copy
                        // is dropped, and saying it travelled is telling the owner their
                        // screenshot is with support when it is nowhere.
                        attachments = ticket.attachments.size,
                        logsAttached = ticket.diagnosticsReference != null,
                    )
                    _state.update { it.copy(sending = false) }
                    emit(
                        ReportEffect.Sent(
                            reference = ticket.reference,
                            area = current.area.name,
                            photos = ticket.attachments.size,
                            logsAttached = ticket.diagnosticsReference != null,
                            // Masked here, not on the confirmation: the address must not
                            // travel as a navigation argument, which is written to saved state.
                            maskedReplyTo = maskEmail(replyTo),
                        ),
                    )
                },
            )
        }
    }

    /**
     * The diagnostics request for this screen, opened once.
     *
     * Held so a retry after a failed save reuses it. Opening a new one per attempt would file
     * a fresh outbox row and a fresh upload nudge each time, for one report.
     *
     * Wrapped, because it writes to the database and schedules work — and a throw escaping
     * here would take the send down with it, losing a report over a log file.
     */
    private suspend fun diagnosticsReference(): String? {
        openedDiagnostics?.let { return it }
        return runCatchingCancellableSuspend { requestDiagnostics() }
            .onFailure { telemetry.submitFailed(TicketKind.PROBLEM, it) }
            .getOrNull()
            ?.also { openedDiagnostics = it }
    }

    private var openedDiagnostics: String? = null

    private fun emit(effect: ReportEffect) {
        _effects.trySend(effect)
    }
}

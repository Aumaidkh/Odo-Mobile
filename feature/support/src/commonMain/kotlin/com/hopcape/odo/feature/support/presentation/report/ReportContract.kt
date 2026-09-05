package com.hopcape.odo.feature.support.presentation.report

import androidx.compose.runtime.Immutable

/**
 * Where a problem happened.
 *
 * Asked first, and asked as a choice rather than as a sentence, because it is what routes the
 * ticket — and because seeing "Bill scan · Reminders · Challan" tells the owner there is a
 * team behind each one before they have typed anything.
 */
internal enum class ReportArea {
    BILL_SCAN,
    REMINDERS,
    CHALLAN,
    REFUEL,
    PAYMENT,
    OTHER,
}

/** A file the owner attached, as the form knows it before anything is stored. */
@Immutable
internal data class ReportAttachment(
    /** The picked reference, resolved to app storage when the report is saved. */
    val ref: String,
    val name: String,
)

@Immutable
internal data class ReportUiState(
    val area: ReportArea = ReportArea.BILL_SCAN,
    val message: String = "",
    val attachments: List<ReportAttachment> = emptyList(),
    val attachLogs: Boolean = true,
    /**
     * The masked account address, or blank when there is none.
     *
     * Blank is what puts the email field on the screen. A ticket nobody can answer wastes the
     * owner's time twice — once writing it, once wondering.
     */
    val maskedEmail: String = "",
    val email: String = "",
    val emailInvalid: Boolean = false,
    val sending: Boolean = false,
) {
    /** True when the account has no address and the owner has to give one. */
    val asksForEmail: Boolean get() = maskedEmail.isBlank()

    val canSend: Boolean
        get() = message.isNotBlank() &&
            !sending &&
            (!asksForEmail || email.isNotBlank())

    /**
     * Whether the typed address could reach anybody.
     *
     * Deliberately shallow — one `@`, something either side, and a dot in the domain. The
     * only thing worth catching here is a field filled in to get past the button, because
     * decision 3 asks for the address *so support can reply*, and "asdf" cannot be replied to.
     * Anything stricter rejects real addresses, and the real check is a mail that bounces.
     */
    fun emailLooksValid(): Boolean {
        if (!asksForEmail) return true
        val at = email.indexOf('@')
        return at > 0 &&
            at < email.lastIndex &&
            email.indexOf('@', at + 1) < 0 &&
            email.substring(at + 1).contains('.') &&
            !email.any { it.isWhitespace() }
    }
}

internal sealed interface ReportEvent {

    data object BackClicked : ReportEvent

    data class AreaPicked(val area: ReportArea) : ReportEvent

    data class MessageChanged(val message: String) : ReportEvent

    data object AddAttachmentClicked : ReportEvent

    /** A file came back from the picker. */
    data class AttachmentPicked(val ref: String, val name: String) : ReportEvent

    data class AttachmentRemoved(val ref: String) : ReportEvent

    data class AttachLogsToggled(val on: Boolean) : ReportEvent

    data class EmailChanged(val email: String) : ReportEvent

    data object SendClicked : ReportEvent
}

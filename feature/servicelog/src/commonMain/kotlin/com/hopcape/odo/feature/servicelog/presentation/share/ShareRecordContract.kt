package com.hopcape.odo.feature.servicelog.presentation.share

/** What the owner did on the share sheet. */
internal sealed interface ShareRecordEvent {

    /** One of the targets, or the download button — every one of them sends the PDF. */
    data class ShareViaClicked(val target: ShareTarget) : ShareRecordEvent

    /**
     * The host finished rendering, with the document's bytes or `null` if it could not be
     * produced. Reported back rather than returned, because rendering needs a UI host and
     * the ViewModel does not have one.
     */
    data class Rendered(val bytes: ByteArray?, val target: ShareTarget) : ShareRecordEvent {
        // A ByteArray in a data class compares by identity, which would make two different
        // documents look equal and two reads of one look different. Nothing depends on
        // comparing these, so the generated versions are replaced with honest ones.
        override fun equals(other: Any?): Boolean = this === other

        override fun hashCode(): Int = bytes.contentHashCode() * 31 + target.hashCode()
    }
}

/**
 * One-shot handoffs, performed by the sheet's host — the two things a ViewModel cannot do
 * because both need whatever is hosting the UI.
 */
internal sealed interface ShareRecordEffect {

    /**
     * Lay [html] out and print it. The document arrives fully built: the ViewModel is what
     * knows the record, so the host only runs the platform's renderer over it.
     */
    data class RenderDocument(
        val html: String,
        val documentName: String,
        val target: ShareTarget,
    ) : ShareRecordEffect

    /** Hand the written file to the system share sheet. */
    data class ShareFile(val storageKey: String, val title: String) : ShareRecordEffect

    /**
     * The whole record is a Pro export and this owner is on the free plan.
     *
     * Checked when the target is tapped rather than when the sheet opens, so a free owner
     * still sees what the export contains before being asked to pay for it. Sharing one
     * entry's bill is never gated — that is the bill they just paid, not the car's history.
     */
    data object OpenPaywall : ShareRecordEffect
}

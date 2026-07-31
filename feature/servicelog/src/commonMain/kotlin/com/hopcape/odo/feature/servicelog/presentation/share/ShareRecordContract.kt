package com.hopcape.odo.feature.servicelog.presentation.share

/** What the owner did on the share sheet. */
internal sealed interface ShareRecordEvent {
    data object CopyLinkClicked : ShareRecordEvent
    data class ShareViaClicked(val target: ShareTarget) : ShareRecordEvent
    data object DownloadPdfClicked : ShareRecordEvent
}

/**
 * One-shot handoffs, performed by the sheet's host. Each carries the link rather than
 * leaving the host to find it — the ViewModel is the one that knows whether there is one.
 */
internal sealed interface ShareRecordEffect {
    data class CopyLink(val url: String) : ShareRecordEffect
    data class ShareLink(val target: ShareTarget, val url: String) : ShareRecordEffect

    /** Render and save the record as a PDF — the offline half of the Resale Passport. */
    data object DownloadPdf : ShareRecordEffect
}

package com.hopcape.odo.feature.documentvault.presentation.share

/** What the owner did on the share sheet, as data. */
internal sealed interface ShareDocumentEvent {

    /** A share target was tapped. */
    data class ShareVia(val target: ShareTarget) : ShareDocumentEvent

    /** "Download PDF" — save a copy where the owner can find it. */
    data object DownloadTapped : ShareDocumentEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface ShareDocumentEffect {

    /** Hand the stored file to the platform's share sheet. */
    data class ShareFile(val storagePath: String, val target: ShareTarget) : ShareDocumentEffect

    data class DownloadFile(val storagePath: String) : ShareDocumentEffect
}

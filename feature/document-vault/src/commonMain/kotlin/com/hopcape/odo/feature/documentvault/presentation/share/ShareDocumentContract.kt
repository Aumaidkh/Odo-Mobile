package com.hopcape.odo.feature.documentvault.presentation.share

/** What the owner did on the share sheet, as data. */
internal sealed interface ShareDocumentEvent {

    /** "Share" — hand the document to whichever app the owner picks. */
    data object ShareTapped : ShareDocumentEvent

    /** "Save a copy" — put a copy where the owner keeps their downloads. */
    data object DownloadTapped : ShareDocumentEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface ShareDocumentEffect {

    /**
     * Hand the file at [storageKey] to the platform's share sheet, declared as [mimeType].
     *
     * The key names the exported copy rather than the stored document: only the export
     * directory is reachable by other apps, which is what keeps the rest of the owner's
     * papers private. The title the sheet offers it under is the screen's to resolve, so it
     * is not carried here.
     */
    data class ShareFile(val storageKey: String, val mimeType: String) : ShareDocumentEffect
}

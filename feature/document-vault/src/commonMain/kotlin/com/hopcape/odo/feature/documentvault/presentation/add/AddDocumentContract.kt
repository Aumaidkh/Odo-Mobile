package com.hopcape.odo.feature.documentvault.presentation.add

import com.hopcape.odo.core.domain.document.model.DocumentType

/** What the owner did on the add screen, as data. */
internal sealed interface AddDocumentEvent {

    /** A type chip was tapped. */
    data class TypeSelected(val type: DocumentType) : AddDocumentEvent

    /** How the owner chose to capture the document. */
    sealed interface Capture : AddDocumentEvent {
        /** Scan with the camera — handed to the scanner, which reads the dates off the paper. */
        data object Scan : Capture

        /** A file was picked, or `null` when the picker was cancelled. */
        data class FilePicked(val pickedRef: String?) : Capture

        /** Import from DigiLocker — not built yet. */
        data object DigiLocker : Capture
    }

    data object CloseTapped : AddDocumentEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface AddDocumentEffect {

    /**
     * The picked file is in app storage under [storageKey]; confirm its dates before filing.
     *
     * An upload goes to the same confirm step a scan does. The dates on an uploaded policy
     * are as worth reading as the ones on a photographed one, and a document filed without
     * them produces no reminder.
     */
    data class OpenReview(val storageKey: String, val type: DocumentType) : AddDocumentEffect

    /**
     * Hand over to the scanner, pointed at a paper of [type].
     *
     * The type travels because the owner has already answered it here — the read's own
     * guess is for the owner who opened the scanner without saying.
     */
    data class OpenScanner(val type: DocumentType) : AddDocumentEffect

    data object NavigateBack : AddDocumentEffect
}

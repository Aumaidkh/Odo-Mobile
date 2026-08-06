package com.hopcape.odo.feature.documentvault.presentation.add

import com.hopcape.odo.core.domain.document.model.DocumentId
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

    /** The document was saved; the flow moves to its confirmation. */
    data class OpenSuccess(val id: DocumentId) : AddDocumentEffect

    /**
     * Hand over to the scanner, pointed at a paper of [type].
     *
     * The type travels because the owner has already answered it here — the read's own
     * guess is for the owner who opened the scanner without saying.
     */
    data class OpenScanner(val type: DocumentType) : AddDocumentEffect

    data object NavigateBack : AddDocumentEffect
}

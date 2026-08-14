package com.hopcape.odo.feature.documentvault.presentation.detail

import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentType

/**
 * What the owner did on a document's detail, as data.
 *
 * Grouped by what the tap is about: [File] acts on the stored file, [Open] leaves for
 * somewhere else. The split is what keeps the ViewModel's `when` short.
 */
internal sealed interface DocumentDetailEvent {

    /** Actions on the file itself. */
    sealed interface File : DocumentDetailEvent {
        /** Open the stored file in a viewer. */
        data object View : File

        /**
         * A replacement file was picked.
         *
         * Nothing in the menu sends this today — the option was taken out while it had no
         * picker behind it. The path stays because what it leads to is finished and tested;
         * putting the option back is a menu row, not a feature.
         */
        data class Replace(val pickedRef: String) : File

        /** "Save a copy" — put the file where the owner keeps their downloads. */
        data object Download : File

        data object Delete : File
    }

    /** Actions that leave the screen. */
    sealed interface Open : DocumentDetailEvent {
        data object Share : Open

        /** "Renew now" — a renewal is a new document of the same type. */
        data object Renew : Open

        /** Correct the dates on this document — where a missing expiry gets filled in. */
        data object EditDates : Open

        data object Back : Open
    }
}

/** One-shot handoffs the route host performs. */
internal sealed interface DocumentDetailEffect {

    data class OpenShare(val id: DocumentId) : DocumentDetailEffect

    /** Open the add flow on this document's type, which is what a renewal is. */
    data class OpenAdd(val prefillType: DocumentType) : DocumentDetailEffect

    /** Open the sheet that corrects this document's dates. */
    data class OpenEditDates(val id: DocumentId) : DocumentDetailEffect

    /** Hand the stored file to a platform viewer. */
    data class OpenFile(val storagePath: String) : DocumentDetailEffect

    /** A copy of the file is in the owner's downloads. */
    data object CopySaved : DocumentDetailEffect

    /** The copy could not be written — no space, or the file went missing under it. */
    data object CopySaveFailed : DocumentDetailEffect

    /** The document is gone, so the screen that was showing it closes. */
    data object NavigateBack : DocumentDetailEffect
}

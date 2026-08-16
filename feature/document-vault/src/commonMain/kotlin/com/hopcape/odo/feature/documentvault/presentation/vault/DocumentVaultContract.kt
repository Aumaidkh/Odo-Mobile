package com.hopcape.odo.feature.documentvault.presentation.vault

import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentType

/**
 * What the owner did on the vault, as data.
 *
 * Every event here leaves the screen, so each becomes a [DocumentVaultEffect]. The vault
 * shows what the car has and nothing about it is adjustable, so there is no view-state
 * group the way the service log's list has one.
 */
internal sealed interface DocumentVaultEvent {

    /** A document on file was tapped. */
    data class DocumentTapped(val id: DocumentId) : DocumentVaultEvent

    /** "Renew" on a document that is expiring or expired. */
    data class RenewTapped(val id: DocumentId, val type: DocumentType) : DocumentVaultEvent

    /** "Add" on a row the vault asks for but does not have. */
    data class AddTapped(val type: DocumentType) : DocumentVaultEvent

    /** The bottom bar's "Add a document", which names no type. */
    data object AddAnyTapped : DocumentVaultEvent

    data object BackTapped : DocumentVaultEvent

    /** The reminders coach mark was tapped away. Seen forever (#231). */
    data object VaultShowcaseDismissed : DocumentVaultEvent

    /** The coach mark's cutout (the add bar) was tapped — open the add flow. Seen forever. */
    data object VaultShowcaseActedOn : DocumentVaultEvent

    /** The screen left composition while the coach mark was up — release the grant, not seen. */
    data object VaultShowcaseLeft : DocumentVaultEvent
}

/**
 * One-shot handoffs the route host performs. Each carries data; the route turns it into a
 * navigation command, which is what keeps the ViewModel free of navigation types.
 */
internal sealed interface DocumentVaultEffect {

    data class OpenDocument(val id: DocumentId) : DocumentVaultEffect

    /**
     * Open the add flow, optionally on a type.
     *
     * A renewal is an add: the new policy is a new document, and the expired one stays as
     * history. That is why "Renew" lands here rather than on an edit screen.
     */
    data class OpenAdd(val prefillType: DocumentType?) : DocumentVaultEffect

    data object NavigateBack : DocumentVaultEffect
}

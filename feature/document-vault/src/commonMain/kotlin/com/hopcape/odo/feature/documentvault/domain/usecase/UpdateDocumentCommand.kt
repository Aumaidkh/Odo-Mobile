package com.hopcape.odo.feature.documentvault.domain.usecase

import com.hopcape.odo.core.domain.document.model.DocumentType
import kotlinx.datetime.LocalDate

/**
 * Raw edit input for a document already in the vault. Fields are unvalidated;
 * `Document.create` checks them, the same as it does for an add.
 *
 * The document's id is passed to [UpdateDocumentUseCase] separately, because it identifies
 * the row rather than being something the screen edits.
 *
 * There is no file here. Replacing the file is [ReplaceDocumentFileUseCase], because that
 * writes bytes and this does not.
 */
internal data class UpdateDocumentCommand(
    val type: DocumentType,
    val title: String? = null,
    val issuedOn: LocalDate? = null,
    val expiresOn: LocalDate? = null,
)

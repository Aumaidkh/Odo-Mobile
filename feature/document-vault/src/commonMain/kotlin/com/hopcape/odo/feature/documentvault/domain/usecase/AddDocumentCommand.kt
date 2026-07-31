package com.hopcape.odo.feature.documentvault.domain.usecase

import com.hopcape.odo.core.domain.document.model.DocumentSource
import com.hopcape.odo.core.domain.document.model.DocumentType
import kotlinx.datetime.LocalDate

/**
 * Raw add-document input from the screen. Fields are unvalidated; `Document.create` checks
 * them.
 *
 * `carId` and `ownerId` are not here. They are context — the selected car and the signed-in
 * owner — so [AddDocumentUseCase] takes them separately, like the service-log commands do.
 *
 * [pickedRef] is whatever the picker or camera returned. The use case copies that file into
 * app storage before the document is written.
 */
internal data class AddDocumentCommand(
    val type: DocumentType,
    val pickedRef: String,
    val source: DocumentSource,
    val title: String? = null,
    val issuedOn: LocalDate? = null,
    val expiresOn: LocalDate? = null,
)

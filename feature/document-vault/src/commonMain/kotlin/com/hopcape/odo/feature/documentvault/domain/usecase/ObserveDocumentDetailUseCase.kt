package com.hopcape.odo.feature.documentvault.domain.usecase

import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentValidity
import com.hopcape.odo.core.domain.document.policy.DocumentReminder
import com.hopcape.odo.core.domain.document.policy.DocumentReminderPolicy
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.feature.documentvault.domain.file.DocumentFileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Reads one document for its detail screen: the document, its validity, how much of its
 * cover has run, its next reminder, and whether the file is still on the device.
 *
 * Emits `null` when no live document has the id, so a screen showing a document deleted
 * elsewhere can close itself instead of showing stale data.
 */
internal class ObserveDocumentDetailUseCase(
    private val documents: DocumentRepository,
    private val files: DocumentFileStore,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    operator fun invoke(id: DocumentId): Flow<DocumentDetail?> =
        documents.observe(id).map { document -> document?.let { detailOf(it) } }

    private suspend fun detailOf(document: Document): DocumentDetail {
        val today = clock.now().toLocalDateTime(timeZone).date
        return DocumentDetail(
            document = document,
            validity = document.validity(today),
            validityProgress = document.validityProgress(today),
            nextReminder = DocumentReminderPolicy.nextReminderFor(document, today),
            // Checked, not assumed: a restore can bring the database back without the
            // private files directory, and then "View" would open nothing.
            isFileAvailable = files.exists(document.storagePath),
        )
    }

    /**
     * How much of the document's cover has run, from 0 to 1. Drives the expiry bar.
     *
     * `null` unless the document has both dates. Without an issue date there is no span to
     * measure against, and using the renewal window instead would invent a start date the
     * owner never gave.
     */
    private fun Document.validityProgress(today: LocalDate): Float? {
        val issued = issuedOn ?: return null
        val expires = expiresOn ?: return null
        val span = issued.daysUntil(expires)
        if (span <= 0) return null
        return (issued.daysUntil(today).toFloat() / span).coerceIn(0f, 1f)
    }
}

/** One document, resolved against a day, with everything its detail screen shows. */
internal data class DocumentDetail(
    val document: Document,
    val validity: DocumentValidity,
    /** How much of the document's life has elapsed (0..1), or `null` if unknown. */
    val validityProgress: Float?,
    val nextReminder: DocumentReminder?,
    val isFileAvailable: Boolean,
) {
    /** The Verified badge. Earned by where the file came from, not by having one. */
    val isVerified: Boolean get() = document.source.isVerified
}

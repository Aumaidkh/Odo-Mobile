package com.hopcape.odo.feature.documentvault.domain.usecase

import arrow.core.EitherNel
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.notification.DocumentReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Edits a document's details: its type, title and dates.
 *
 * The edited document is rebuilt through `Document.create` with the same id, file and
 * source, so an edit is checked by exactly the same rules as an add. There is no separate
 * validation path that could drift from it.
 *
 * A successful edit rebuilds the notification schedule. Changing an expiry date is exactly
 * the case where a stale reminder would otherwise fire on the old day.
 */
internal class UpdateDocumentUseCase(
    private val documents: DocumentRepository,
    private val reminders: DocumentReminderScheduler,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend operator fun invoke(
        id: DocumentId,
        command: UpdateDocumentCommand,
    ): EitherNel<DomainError, Document> = either {
        val existing = documents.observe(id).first()
        ensureNotNull(existing) { nonEmptyListOf(DomainError.DocumentNotFound) }

        val edited = Document.create(
            id = existing.id,
            ownerId = existing.ownerId,
            carId = existing.carId,
            type = command.type,
            storagePath = existing.storagePath,
            source = existing.source,
            today = clock.now().toLocalDateTime(timeZone).date,
            title = command.title,
            issuedOn = command.issuedOn,
            expiresOn = command.expiresOn,
        ).bind()

        documents.update(edited)
            .mapLeft { nonEmptyListOf(it) }
            .onRight { reminders.refresh() }
            .bind()
    }
}

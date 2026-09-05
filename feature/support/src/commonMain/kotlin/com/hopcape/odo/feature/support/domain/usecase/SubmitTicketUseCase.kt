package com.hopcape.odo.feature.support.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.support.SupportTicket
import com.hopcape.odo.core.domain.support.SupportTicketId
import com.hopcape.odo.core.domain.support.SupportTicketRepository
import com.hopcape.odo.core.domain.support.TicketAttachment
import com.hopcape.odo.core.domain.support.TicketKind
import com.hopcape.odo.core.platform.file.PlatformFileStore
import kotlin.time.Clock

/**
 * Turns a filled-in form into a saved ticket.
 *
 * One use case for all three forms, because what they do is identical the moment the fields
 * are collected: name a row, build it, save it. What differs — the area, the band, what was
 * paid — is already flattened into [details] by the screen that asked for it.
 *
 * **Saved, not sent.** This returns when the row is on the device. Reaching the server is the
 * sync engine's errand, which is what makes the confirmation honest with no signal.
 */
internal class SubmitTicketUseCase(
    private val tickets: SupportTicketRepository,
    private val files: PlatformFileStore,
    private val ids: IdGenerator,
    private val clock: Clock,
) {

    suspend operator fun invoke(
        kind: TicketKind,
        body: String,
        details: Map<String, String> = emptyMap(),
        /** What the picker returned. Copied into app storage here, before anything is saved. */
        picked: List<PickedFile> = emptyList(),
        replyTo: String? = null,
        diagnosticsReference: String? = null,
    ): Either<DomainError, SupportTicket> = either {
        val id = SupportTicketId(ids.newId())
        // Validated before anything is copied. A body that will be refused should not leave
        // a screenshot on disk that no row will ever reference.
        val draft = SupportTicket.create(
            id = id,
            kind = kind,
            body = body,
            createdAt = clock.now(),
            // Blank values are dropped rather than stored: a detail column holding "" is a
            // fact nobody can filter on and one more thing for the panel to special-case.
            details = details.filterValues { it.isNotBlank() },
            replyTo = replyTo,
            diagnosticsReference = diagnosticsReference,
        ).bind()

        val stored = picked.copyInto(id)
        tickets.submit(draft.copy(attachments = stored))
            .onLeft {
                // Nothing will ever reference these now, and every retry mints a fresh id —
                // so without this a save that fails three times leaves three copies of every
                // screenshot in the owner's storage.
                stored.forEach { files.delete(it.storageKey) }
            }
            .bind()
    }

    /**
     * Copy each picked file into the app's own storage and answer with what was stored.
     *
     * A picker hands back a permission-scoped pointer that stops resolving once the process
     * dies, so a ticket holding one is a ticket whose screenshot is gone by the time anybody
     * opens it. Copied under the ticket's own id, which is also what makes them findable when
     * the row syncs.
     *
     * **A file that would not copy is dropped, not fatal.** The words are the report; losing
     * the screenshot is worse than losing the report, and refusing to save either is worst.
     */
    private suspend fun List<PickedFile>.copyInto(id: SupportTicketId): List<TicketAttachment> =
        mapIndexedNotNull { index, file ->
            files.save(
                pickedRef = file.ref,
                directory = "$TICKET_DIRECTORY/${id.value}",
                fileName = "$ATTACHMENT_PREFIX$index",
            ).getOrNull()?.let { stored ->
                TicketAttachment(storageKey = stored, name = file.name)
            }
        }

    private companion object {
        const val TICKET_DIRECTORY = "tickets"
        const val ATTACHMENT_PREFIX = "attachment-"
    }
}

/** A file as the picker returned it, before anything has been copied. */
internal data class PickedFile(val ref: String, val name: String)

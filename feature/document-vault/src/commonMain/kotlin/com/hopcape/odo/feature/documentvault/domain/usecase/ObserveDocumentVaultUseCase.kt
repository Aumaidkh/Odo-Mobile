package com.hopcape.odo.feature.documentvault.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.model.DocumentValidity
import com.hopcape.odo.core.domain.document.policy.DocumentReminder
import com.hopcape.odo.core.domain.document.policy.DocumentReminderPolicy
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Builds everything the vault overview shows — the rows and the header counts — from one
 * read of the car's documents.
 *
 * One use case instead of separate flows for rows and counts. Separate reads can emit at
 * different times, which would let the header say "2 need attention" above three amber
 * rows.
 *
 * Every row is resolved against the same day, read once per emission.
 */
internal class ObserveDocumentVaultUseCase(
    private val documents: DocumentRepository,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    operator fun invoke(carId: CarId): Flow<DocumentVaultSnapshot> =
        documents.observe(carId).map(::snapshotOf)

    private fun snapshotOf(documents: List<Document>): DocumentVaultSnapshot {
        val today = clock.now().toLocalDateTime(timeZone).date

        val tracked = TRACKED.map { type ->
            when (val current = documents.currentOfType(type)) {
                null -> VaultSlot.Missing(type)
                else -> current.onFile(today)
            }
        }

        // Loan papers and one-off documents get a row each. The vault does not ask for
        // them, so a missing one is not a gap, and a car can have more than one.
        val extras = documents
            .filterNot { it.type in TRACKED }
            .sortedWith(compareBy({ it.type.ordinal }, { it.expiresOn ?: LocalDate(FAR_FUTURE_YEAR, 1, 1) }))
            .map { it.onFile(today) }

        return DocumentVaultSnapshot(slots = tracked + extras, today = today)
    }

    /**
     * The document to show for a type: the one with the latest expiry.
     *
     * A car can hold last year's policy and this year's. The later one is the policy still
     * covering the owner, and the one a renewal reminder should count from.
     */
    private fun List<Document>.currentOfType(type: DocumentType): Document? =
        filter { it.type == type }.maxByOrNull { it.expiresOn ?: LocalDate(FAR_FUTURE_YEAR, 1, 1) }

    private fun Document.onFile(today: LocalDate) = VaultSlot.OnFile(
        document = this,
        validity = validity(today),
        nextReminder = DocumentReminderPolicy.nextReminderFor(this, today),
    )

    private companion object {
        /**
         * The documents the vault asks for, in display order.
         *
         * The garage tracks its own, shorter list. The two are kept separate on purpose:
         * one feature never imports another, and which documents a screen asks for is that
         * screen's decision.
         */
        val TRACKED = listOf(
            DocumentType.INSURANCE,
            DocumentType.PUC,
            DocumentType.RC,
            DocumentType.LICENCE,
        )

        /** Stands in for "never expires" when sorting by expiry date. */
        const val FAR_FUTURE_YEAR = 9999
    }
}

/** One row of the vault: a document on file, or a document that has not been added. */
internal sealed interface VaultSlot {

    val type: DocumentType

    /** On file, with its validity and next reminder already worked out for the day. */
    data class OnFile(
        val document: Document,
        val validity: DocumentValidity,
        /** The next renewal reminder, or `null` if none is due. */
        val nextReminder: DocumentReminder?,
    ) : VaultSlot {
        override val type: DocumentType get() = document.type

        /** Expired, or close enough to expiry to act on. */
        val needsAttention: Boolean get() = validity.needsAttention
    }

    /** Not added yet — the row offers an "Add" action. */
    data class Missing(override val type: DocumentType) : VaultSlot
}

/** The vault's rows and header counts, all derived from the same read. */
internal data class DocumentVaultSnapshot(
    val slots: List<VaultSlot>,
    /** The day these rows were resolved against. */
    val today: LocalDate,
) {
    /** Rows behind the header's "N documents need attention". */
    val needsAttention: List<VaultSlot.OnFile>
        get() = slots.filterIsInstance<VaultSlot.OnFile>().filter { it.needsAttention }

    /** Documents the vault asks for but does not have. */
    val missing: List<DocumentType>
        get() = slots.filterIsInstance<VaultSlot.Missing>().map { it.type }

    val onFileCount: Int get() = slots.count { it is VaultSlot.OnFile }

    /**
     * True when nothing is missing and nothing is expiring — the green header.
     *
     * Both conditions are needed. A vault holding only a valid PUC is not covered, and
     * neither is a complete vault whose insurance expires next week.
     */
    val isFullyCovered: Boolean
        get() = slots.isNotEmpty() && missing.isEmpty() && needsAttention.isEmpty()

    /** Nothing added yet — the "add your documents" prompt. */
    val isEmpty: Boolean get() = onFileCount == 0
}

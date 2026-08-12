package com.hopcape.odo.core.domain.document.repository

import arrow.core.Either
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.Flow

/**
 * Port for persisting and observing a car's documents. The implementation lives in
 * `:core:data` (local DB as source of truth); the domain stays ignorant of it.
 *
 * A document carries no cross-aggregate invariant of its own — nothing here is the
 * analog of the service log's odometer ordering — so this port is a plain store plus
 * the one count the free-tier cap needs.
 */
interface DocumentRepository {

    /** A car's non-deleted documents. Powers the vault list and the garage's rows. */
    fun observe(carId: CarId): Flow<List<Document>>

    /** A single non-deleted document (detail / edit prefill); emits `null` if absent. */
    fun observe(id: DocumentId): Flow<Document?>

    suspend fun add(document: Document): Either<DomainError, Document>

    suspend fun update(document: Document): Either<DomainError, Document>

    /** Soft delete (sets `deleted_at`); the row is retained for history/audit. */
    suspend fun softDelete(id: DocumentId): Either<DomainError, Unit>

    /**
     * How many live documents the owner holds, for the free tier's 3-document cap
     * (PRD §pricing; enforced in the application layer, never as a DB constraint, so
     * pricing experiments don't need a migration).
     *
     * Counted per **owner**, not per car: the entitlement is sold to a person, so a
     * second car must not multiply the allowance. A count rather than a read of the
     * documents themselves, because the cap check has no use for their contents.
     */
    suspend fun countForOwner(ownerId: OwnerId): Int
}

package com.hopcape.odo.core.data.document

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlinx.coroutines.flow.Flow

/**
 * Local persistence for documents. Hides the SQLDelight database from
 * [DocumentRepositoryImpl]: this owns how rows are read and written, the repository owns
 * what an operation means (error mapping, telemetry, asking for a sync).
 *
 * This stores the *row* that describes a document. The file itself belongs to the vault
 * feature's `DocumentFileStore` — nothing here touches bytes.
 *
 * Write methods throw on storage failure; the repository turns that into a
 * `DomainError.PersistenceFailure`. The observe flows are raw — a read failure propagates
 * to the collector, and the repository decides how to report it.
 */
internal interface DocumentLocalDataSource {

    /** Insert [document] as a `PENDING` row. */
    suspend fun insert(document: Document)

    /**
     * Write [document] over its stored row and return it to `PENDING`. Answers `false`
     * when no live row with that id exists — checked in the same transaction as the
     * write, so a document deleted in between cannot turn into a silent no-op update.
     */
    suspend fun update(document: Document): Boolean

    /** Tombstone the document row. */
    suspend fun softDelete(id: DocumentId)

    /** Every live document for [carId], as it changes. */
    fun observeByCar(carId: CarId): Flow<List<Document>>

    /** The document with [id], as it changes; `null` while no live row has that id. */
    fun observeById(id: DocumentId): Flow<Document?>

    /** How many live documents [ownerId] has across every car — the free-tier gate. */
    suspend fun countLiveForOwner(ownerId: OwnerId): Int
}

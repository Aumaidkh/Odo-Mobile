package com.hopcape.odo.feature.documentvault.domain.file

import arrow.core.Either
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.file.PlatformFileStore

/**
 * Keeps the *file* behind a document — the paper itself, as opposed to the row that
 * describes it.
 *
 * The copying is not vault-specific and lives in `:core:platform`
 * ([PlatformFileStore]); this port exists so the vault's use cases speak in cars and
 * documents rather than in directories. [PlatformDocumentFileStore] is the one place that
 * translates between the two.
 */
internal interface DocumentFileStore {

    /**
     * Copy the file at [pickedRef] (whatever the picker or camera returned) into app
     * storage, and answer with the storage key the document row should carry.
     *
     * Keyed on [carId] + [documentId] rather than the original filename: two insurance
     * PDFs both called `policy.pdf` must not collide, and the id is already unique.
     */
    suspend fun save(
        pickedRef: String,
        carId: CarId,
        documentId: DocumentId,
    ): Either<DomainError, String>

    /**
     * Remove a stored file. Best effort, and deliberately not an `Either`: the caller
     * deletes a file *because* the document is going away, and a byte-blob that outlives
     * its row is wasted space, not a broken vault. Failing the delete of a document the
     * owner asked to remove would be the worse outcome.
     */
    suspend fun delete(storagePath: String)

    /**
     * Whether the stored file is still there. Read before offering to open or share a
     * document, so a file lost to a restore-from-backup shows as missing rather than as a
     * viewer that opens on nothing.
     */
    suspend fun exists(storagePath: String): Boolean
}

/**
 * The vault's naming on top of the shared [PlatformFileStore].
 *
 * Files land at `documents/{carId}/{documentId}.{ext}`, which mirrors the `documents`
 * storage bucket convention (DB_SCHEMA §7, `{owner_id}/{car_id}/{document_id}.{ext}`) minus
 * the owner segment — implicit on a device with one signed-in owner. When sync lands,
 * uploading is prefixing this key with the owner id, not recomputing where the file should
 * have gone.
 */
internal class PlatformDocumentFileStore(
    private val files: PlatformFileStore,
) : DocumentFileStore {

    override suspend fun save(
        pickedRef: String,
        carId: CarId,
        documentId: DocumentId,
    ): Either<DomainError, String> = files.save(
        pickedRef = pickedRef,
        directory = directoryFor(carId),
        fileName = documentId.value,
    )

    override suspend fun delete(storagePath: String) = files.delete(storagePath)

    override suspend fun exists(storagePath: String): Boolean = files.exists(storagePath)

    private companion object {
        /** The directory every document file lives under, inside app-private storage. */
        const val ROOT = "documents"

        fun directoryFor(carId: CarId): String = "$ROOT/${carId.value}"
    }
}

package com.hopcape.odo.feature.documentvault.domain.file

import arrow.core.Either
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Keeps the *file* behind a document — the paper itself, as opposed to the row that
 * describes it.
 *
 * The vault needs this because what the platform picker hands back is a borrowed handle:
 * an Android `content://` URI is a permission-scoped pointer into another app's storage
 * that stops resolving once the process dies. Storing it would give the owner a vault full
 * of documents that open today and 404 next week. So the bytes are copied into the app's
 * own storage the moment they are picked, and the copy is what the document row points at.
 *
 * Feature-owned rather than a `:core:*` port: the vault is the only place in the app that
 * stores an owner-supplied file. If the bill scanner ever needs the same thing, it writes
 * its own against its own concept of a bill — features share through `:core:domain`, and a
 * file store is not domain.
 */
internal interface DocumentFileStore {

    /**
     * Copy the file at [pickedRef] (whatever the picker or camera returned) into app
     * storage, and answer with the [storage key][DocumentStorageKey] the document row
     * should carry.
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
 * Where a document's file lives, relative to the app's private storage.
 *
 * Deliberately a **relative** key rather than an absolute path. Absolute paths are not
 * stable — the private data directory moves between OS versions, users and restores — and
 * a stored absolute path becomes an unopenable document the next time the OS decides to
 * relocate. Only the key is persisted; each platform resolves it against its own root.
 *
 * The shape mirrors the `documents` storage bucket convention (DB_SCHEMA §7,
 * `{owner_id}/{car_id}/{document_id}.{ext}`) minus the owner segment, which is implicit
 * on a device with one signed-in owner. When sync lands, uploading is prefixing this key
 * with the owner id — not recomputing where the file should have gone.
 */
internal object DocumentStorageKey {

    /** The directory every document file lives under, inside app-private storage. */
    const val ROOT = "documents"

    /** Used when the picked file's type can't be established. */
    const val FALLBACK_EXTENSION = "bin"

    private const val MAX_EXTENSION_LENGTH = 5

    /**
     * `documents/{carId}/{documentId}.{ext}`.
     *
     * [rawExtension] is whatever the platform could work out — a MIME lookup, a filename
     * suffix, or nothing. It is normalized here, in common code, so the two platforms
     * cannot disagree about where the same document's file belongs: lowercased, stripped
     * of a leading dot, and rejected unless it is short and alphanumeric. A filename is
     * being built from it, so `../..` must never survive that check.
     */
    fun of(carId: CarId, documentId: DocumentId, rawExtension: String?): String =
        "$ROOT/${carId.value}/${documentId.value}.${normalizeExtension(rawExtension)}"

    private fun normalizeExtension(raw: String?): String {
        val candidate = raw?.trim()?.removePrefix(".")?.lowercase().orEmpty()
        val usable = candidate.isNotEmpty() &&
            candidate.length <= MAX_EXTENSION_LENGTH &&
            candidate.all { it.isLetterOrDigit() }
        return if (usable) candidate else FALLBACK_EXTENSION
    }
}

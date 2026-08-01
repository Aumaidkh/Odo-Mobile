package com.hopcape.odo.core.platform.file

import arrow.core.Either
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Copies files the owner picked into the app's own storage, and manages them there.
 *
 * A picker returns a permission-scoped pointer into another app's storage. On Android that
 * is a `content://` URI, and it stops resolving once the process dies, so storing it would
 * give the owner records that open today and fail next week. Every feature that keeps an
 * owner-supplied file — a document in the vault, a bill photo on a service entry — copies
 * the bytes through here first and stores the key it gets back.
 *
 * The key is **relative** to app-private storage. Absolute paths are not stable: the private
 * data directory moves between OS versions, users and restores, and a stored absolute path
 * becomes a file that will not open. Each platform resolves the key against its own root.
 */
interface PlatformFileStore {

    /**
     * Copy the file at [pickedRef] to `directory/fileName.ext` under app storage and answer
     * with that key.
     *
     * The caller names the file — an id it already holds — rather than reusing the picked
     * file's name, because two documents both called `policy.pdf` must not collide. The
     * extension is worked out by the platform from what the bytes actually are, since a
     * picker URI often carries no filename at all.
     */
    suspend fun save(pickedRef: String, directory: String, fileName: String): Either<DomainError, String>

    /**
     * Remove a stored file. Best effort, and deliberately not an `Either`: the caller
     * deletes a file because the record that owned it is going away, and a leftover blob is
     * wasted space rather than a broken feature.
     */
    suspend fun delete(storageKey: String)

    /**
     * Whether the stored file is still there. Read before offering to open or share it, so
     * a file lost to a restore-from-backup shows as missing rather than as a viewer that
     * opens on nothing.
     */
    suspend fun exists(storageKey: String): Boolean
}

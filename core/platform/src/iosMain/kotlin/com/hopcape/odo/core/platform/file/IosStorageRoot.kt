package com.hopcape.odo.core.platform.file

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.posix.memcpy

/**
 * The directory every file Odo stores lives under on iOS, matching what `filesDir` is on
 * Android: private to the app, and gone when the app is deleted.
 *
 * Documents rather than Caches, because the owner's bills and papers are their records — iOS
 * empties Caches whenever it wants the space back. It is not reachable from the Files app:
 * that needs `UIFileSharingEnabled`, which Odo does not set.
 */
internal fun appStorageRoot(): String =
    NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        .first() as String

/** The absolute path a relative [storageKey] resolves to. */
internal fun absolutePathFor(storageKey: String): String = "${appStorageRoot()}/$storageKey"

/**
 * Create the directories a file at [storageKey] needs, and answer whether it can be written.
 *
 * `withIntermediateDirectories` makes this safe to call for a key that is already there —
 * an existing directory is not an error.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun ensureParentDirectory(storageKey: String): Boolean {
    val parent = absolutePathFor(storageKey).substringBeforeLast('/', missingDelimiterValue = "")
    if (parent.isEmpty()) return false
    return NSFileManager.defaultManager.createDirectoryAtPath(
        path = parent,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
}

/**
 * Copy an `NSData` into a Kotlin [ByteArray].
 *
 * Foundation and Kotlin do not share a heap, so this is a real copy rather than a view. The
 * pinning is what keeps the garbage collector from moving the destination out from under
 * `memcpy` while it runs.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}

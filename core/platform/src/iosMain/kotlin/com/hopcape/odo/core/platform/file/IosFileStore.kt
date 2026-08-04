package com.hopcape.odo.core.platform.file

import arrow.core.Either
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToFile

/**
 * iOS file storage: copies the picked file under the app's own Documents directory and hands
 * back the relative [StorageKey], the same contract [AndroidFileStore] fulfils.
 *
 * Reading a picked URL is what the file store exists for, and it is also what a captured
 * photo needs to be readable afterwards — the camera writes into the same root, so a scan and
 * an uploaded paper are the same kind of file from here on.
 */
internal class IosFileStore : PlatformFileStore {

    override suspend fun save(
        pickedRef: String,
        directory: String,
        fileName: String,
    ): Either<DomainError, String> = withContext(Dispatchers.Default) {
        Either.catch {
            val url = NSURL.URLWithString(pickedRef) ?: error("picked file is not a URL")
            // Files handed over by the system picker live outside the app's sandbox, and the
            // permission to read them is only granted while this call is scoped to it.
            val scoped = url.startAccessingSecurityScopedResource()
            val data = try {
                NSData.dataWithContentsOfURL(url) ?: error("picked file could not be read")
            } finally {
                if (scoped) url.stopAccessingSecurityScopedResource()
            }

            val key = StorageKey.of(directory, fileName, url.pathExtension)
            if (!ensureParentDirectory(key)) error("storage directory could not be created")
            if (!data.writeToFile(absolutePathFor(key), atomically = true)) {
                error("file could not be written")
            }
            key
        }.mapLeft { DomainError.PersistenceFailure(it.message) }
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun delete(storageKey: String) {
        withContext(Dispatchers.Default) {
            NSFileManager.defaultManager.removeItemAtPath(absolutePathFor(storageKey), null)
        }
    }

    override suspend fun exists(storageKey: String): Boolean = withContext(Dispatchers.Default) {
        NSFileManager.defaultManager.fileExistsAtPath(absolutePathFor(storageKey))
    }

    override suspend fun bytes(storageKey: String): Either<DomainError, ByteArray> =
        withContext(Dispatchers.Default) {
            Either.catch {
                val data = NSFileManager.defaultManager.contentsAtPath(absolutePathFor(storageKey))
                    ?: error("stored file is missing")
                data.toByteArray()
            }.mapLeft { DomainError.PersistenceFailure(it.message) }
        }
}

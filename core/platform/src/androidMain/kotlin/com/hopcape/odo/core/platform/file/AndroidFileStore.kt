package com.hopcape.odo.core.platform.file

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import arrow.core.Either
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android file storage: copies the picked file into the app's private `filesDir` and hands
 * back the relative [StorageKey].
 *
 * Private storage, not the shared Downloads directory or external media: these are the
 * owner's insurance policy scans and workshop bills, and nothing outside Odo has any
 * business reading them. It also means the files leave with the app on uninstall, which is
 * what an owner deleting the app expects of their papers.
 *
 * The [Context] is the application context, which Koin already holds (`androidContext()`
 * from the `:app` bootstrap) — see `corePlatformAndroidModule`.
 */
internal class AndroidFileStore(private val context: Context) : PlatformFileStore {

    override suspend fun save(
        pickedRef: String,
        directory: String,
        fileName: String,
    ): Either<DomainError, String> = withContext(Dispatchers.IO) {
        Either.catch {
            val uri = pickedRef.toUri()
            val key = StorageKey.of(directory, fileName, extensionOf(uri))
            val target = File(context.filesDir, key)
            target.parentFile?.mkdirs()

            // The stream is opened first: a picker URI whose permission has already lapsed
            // fails here, before an empty file has been created for a record that has no
            // bytes behind it.
            val source = context.contentResolver.openInputStream(uri)
                ?: error("picked file could not be opened")
            source.use { input -> target.outputStream().use(input::copyTo) }
            key
        }.mapLeft { DomainError.PersistenceFailure(it.message) }
    }

    override suspend fun delete(storageKey: String) {
        withContext(Dispatchers.IO) {
            runCatching { File(context.filesDir, storageKey).delete() }
        }
    }

    override suspend fun exists(storageKey: String): Boolean = withContext(Dispatchers.IO) {
        File(context.filesDir, storageKey).exists()
    }

    /**
     * The file's extension, preferring what the content resolver says the bytes *are* over
     * what the URI is called — a `content://` URI from the picker often has no filename at
     * all, and a wrong extension is what makes a perfectly good PDF refuse to open later.
     */
    private fun extensionOf(uri: Uri): String? {
        val fromMimeType = context.contentResolver.getType(uri)
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        return fromMimeType ?: uri.lastPathSegment?.substringAfterLast('.', missingDelimiterValue = "")
    }
}

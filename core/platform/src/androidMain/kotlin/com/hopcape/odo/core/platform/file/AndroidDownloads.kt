package com.hopcape.odo.core.platform.file

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import arrow.core.Either
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android's downloads, written two ways because the platform changed underneath them.
 *
 * From Android 10 the media store owns the shared Downloads folder and an app may add to it
 * without asking for anything — the copy lands beside every other download the owner has,
 * which is where they will look for it.
 *
 * Below that, writing there needs `WRITE_EXTERNAL_STORAGE`, a permission Odo does not ask
 * for and would not be able to justify for one menu item. Those devices get the app's own
 * downloads folder on external storage instead: no permission, still browsable in a file
 * manager, and the copy still outlives the screen it was made from.
 */
internal class AndroidDownloads(private val context: Context) : PlatformDownloads {

    override suspend fun saveCopy(
        storageKey: String,
        fileName: String,
        mimeType: String,
    ): Either<DomainError, Unit> = withContext(Dispatchers.IO) {
        Either.catch {
            val source = context.storedFile(storageKey)
            // Checked first: the media store would otherwise be left holding an entry for a
            // download that has no bytes behind it.
            if (!source.exists()) error("stored file is missing")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                source.copyToSharedDownloads(fileName, mimeType)
            } else {
                source.copyToAppDownloads(fileName)
            }
        }.mapLeft { DomainError.PersistenceFailure(it.message) }
    }

    /**
     * The shared Downloads folder, through the media store.
     *
     * Written as pending and cleared afterwards, so nothing else in the system offers the
     * owner a half-copied file — a file manager reading it mid-write is the reason the flag
     * exists.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun File.copyToSharedDownloads(fileName: String, mimeType: String) {
        val resolver = context.contentResolver
        val pending = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, pending)
            ?: error("downloads folder refused a new file")

        // A failure after the row exists has to take the row with it, or the owner is left
        // with an entry that opens on nothing.
        runCatching {
            resolver.openOutputStream(uri)?.use { output -> inputStream().use { it.copyTo(output) } }
                ?: error("downloads folder could not be written to")
        }.onFailure {
            resolver.delete(uri, null, null)
            throw it
        }

        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
    }

    /** The app's own downloads folder — the permission-free path on Android 9 and below. */
    private fun File.copyToAppDownloads(fileName: String) {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: error("external storage is not available")
        directory.mkdirs()
        copyTo(File(directory, fileName), overwrite = true)
    }
}

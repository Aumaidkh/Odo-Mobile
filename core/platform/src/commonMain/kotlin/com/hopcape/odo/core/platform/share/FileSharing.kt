package com.hopcape.odo.core.platform.share

import androidx.compose.runtime.Composable

/**
 * Hands a stored file to the system share sheet.
 *
 * The file-carrying counterpart of [rememberTextSharer], and a composable for the same
 * reason: presenting a share sheet needs whatever is hosting the UI — an Activity on
 * Android, the key window on iOS — and no Koin singleton can hold one without leaking it.
 *
 * Fire and forget, again for the same reason: which app the owner picks, or whether they
 * pick one at all, is reported inconsistently across platforms and nothing in Odo depends on
 * the answer. Saving to Files or Drive is one of the targets in that sheet rather than a
 * separate path here — the system already knows how to put a file somewhere.
 *
 * The file must live in the app's own storage, named by the same [StorageKey]
 * [com.hopcape.odo.core.platform.file.PlatformFileStore] writes with. Only the directory the
 * app exports is reachable this way; the rest of the owner's papers stay unreadable to other
 * apps, which is the point of keeping them in private storage.
 *
 * @return a function taking the stored file's key, its MIME type, and the title the sheet
 *   offers the file under.
 */
@Composable
expect fun rememberFileSharer(): (storageKey: String, mimeType: String, title: String) -> Unit

/** MIME types Odo shares files as. */
object ShareMimeType {
    const val PDF: String = "application/pdf"
}

/**
 * Where a shared file has to live.
 *
 * Sharing a file means granting another app permission to read it, and that grant is
 * declared against a directory rather than a file. So exported files sit in their own
 * directory and nothing else does: a grant that covered the whole of the app's storage would
 * hand the reader every bill and policy the owner has ever scanned.
 *
 * The Android `FileProvider` configuration names this exact path. Changing it here without
 * changing `res/xml/file_paths.xml` makes every share fail.
 */
const val EXPORT_DIRECTORY: String = "exports"

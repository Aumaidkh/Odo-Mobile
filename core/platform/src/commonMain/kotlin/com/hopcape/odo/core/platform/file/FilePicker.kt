package com.hopcape.odo.core.platform.file

import androidx.compose.runtime.Composable

/** MIME filters for [rememberFilePicker], named so a call site reads as what it wants. */
object FileTypes {

    /** PDFs and images — what an owner has after scanning or photographing a paper. */
    val PAPERS: List<String> = listOf("application/pdf", "image/*")
}

/**
 * Opens the platform's file picker for an "upload a file" action.
 *
 * Returns a launch lambda to invoke on tap; [onPicked] fires with the picked file's
 * reference (a content URI / path) or `null` if the owner cancelled. Declared here so
 * presentation code stays platform-agnostic — the Android `actual` uses the system document
 * picker; iOS is stubbed for the Android-first MVP.
 *
 * What comes back is a borrowed handle, not a file to keep. Pass it to
 * [PlatformFileStore.save] before storing anything.
 */
@Composable
expect fun rememberFilePicker(
    mimeTypes: List<String> = FileTypes.PAPERS,
    onPicked: (String?) -> Unit,
): () -> Unit

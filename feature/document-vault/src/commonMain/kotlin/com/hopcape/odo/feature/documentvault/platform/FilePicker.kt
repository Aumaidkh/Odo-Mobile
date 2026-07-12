package com.hopcape.odo.feature.documentvault.platform

import androidx.compose.runtime.Composable

/**
 * Opens the platform's document picker (PDF or image) for the "Upload a file" action.
 *
 * Returns a launch lambda to invoke on tap; [onPicked] fires with the picked file's
 * reference (a content URI / path) or `null` if the user cancelled. Declared here so
 * the presentation code stays platform-agnostic — the Android `actual` uses the
 * system document picker; iOS is stubbed for the Android-first MVP.
 */
@Composable
expect fun rememberFilePicker(onPicked: (String?) -> Unit): () -> Unit

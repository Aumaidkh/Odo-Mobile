package com.hopcape.odo.core.platform.share

import androidx.compose.runtime.Composable

/**
 * Hands a line of text to the system share sheet.
 *
 * A composable rather than an injected port, for the same reason as the camera, the file
 * picker and the UPI launcher: presenting a share sheet needs the thing hosting the UI (an
 * Activity on Android, the key window on iOS), and no Koin singleton can hold one without
 * leaking it.
 *
 * Fire and forget. Nothing in Odo needs to know which app the owner picked, or whether they
 * picked one at all — the share sheet reports that inconsistently across platforms, and no
 * behaviour here depends on the answer.
 *
 * @return a function to call with the text to share.
 */
@Composable
expect fun rememberTextSharer(): (String) -> Unit

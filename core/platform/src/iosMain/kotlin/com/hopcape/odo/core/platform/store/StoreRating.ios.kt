package com.hopcape.odo.core.platform.store

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * iOS actual — does nothing, because Odo has no App Store listing yet.
 *
 * An App Store URL needs the numeric app ID that the store assigns at first submission, and
 * there is no submission. Opening a guessed URL would show the owner someone else's app.
 *
 * The caller leaves the "Rate Odo" row out on iOS rather than showing a row that does
 * nothing; this stays bound so the port resolves.
 */
@Composable
actual fun rememberStoreRater(): () -> Unit = remember { {} }

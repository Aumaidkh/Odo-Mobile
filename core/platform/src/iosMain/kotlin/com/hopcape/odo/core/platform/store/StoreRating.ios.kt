package com.hopcape.odo.core.platform.store

import androidx.compose.runtime.Composable

/**
 * iOS actual — null, because Odo has no App Store listing yet.
 *
 * An App Store URL needs the numeric app ID the store assigns at first submission, and there
 * is no submission. Opening a guessed URL would show the owner somebody else's app.
 *
 * Null rather than an empty lambda, so the help sheet leaves the "Rate Odo" row out instead
 * of showing one that swallows the tap. This becomes a real implementation the day there is
 * a listing, and the row appears on its own.
 */
@Composable
actual fun rememberStoreRater(): (() -> Unit)? = null

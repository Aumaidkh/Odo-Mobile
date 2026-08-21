package com.hopcape.odo.core.platform.store

import androidx.compose.runtime.Composable

/**
 * Opens this app's own store listing, where the owner can leave a rating.
 *
 * The listing, not an in-app rating prompt. Google's in-app review API decides for itself
 * whether to show anything, and silently shows nothing most of the time — acceptable for a
 * prompt Odo raises on its own, wrong for a row the owner deliberately tapped.
 *
 * Fire and forget, and it does nothing on a platform with no listing to open.
 *
 * @return a function to call to open the listing.
 */
@Composable
expect fun rememberStoreRater(): () -> Unit

package com.hopcape.odo.core.platform.store

import androidx.compose.runtime.Composable

/**
 * Opens this app's own store listing, where the owner can leave a rating.
 *
 * The listing, not an in-app rating prompt. Google's in-app review API decides for itself
 * whether to show anything, and silently shows nothing most of the time — acceptable for a
 * prompt Odo raises on its own, wrong for a row the owner deliberately tapped.
 *
 * Fire and forget.
 *
 * @return a function to call to open the listing, or `null` on a platform that has no
 *   listing to open. Null is not "it failed" — it means the row should not be offered at
 *   all, the same way a blank
 *   [com.hopcape.odo.core.domain.legal.LegalLinks] URL means the link is left out. A rating
 *   row that visibly does nothing is worse than no rating row.
 */
@Composable
expect fun rememberStoreRater(): (() -> Unit)?

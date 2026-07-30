package com.hopcape.odo.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

/**
 * Collect a ViewModel's one-shot effects at a route host and act on each exactly once.
 *
 * Lives in `:core:navigation` because this is routing plumbing, not any one feature's: an
 * effect stream is how presentation asks for navigation without importing a [NavKey], and
 * every feature that does so needs the same collector. Duplicating it per feature would be
 * duplicating the two things that are easy to get wrong —
 *
 *  - collection is **lifecycle-scoped** to `STARTED`, so an effect can't be acted on while
 *    the screen is in the background (a navigation applied to a stopped host lands the owner
 *    somewhere they never asked to be);
 *  - [onEffect] is read through [rememberUpdatedState], so a recomposition that supplies a
 *    new lambda doesn't restart collection and drop a queued effect.
 *
 * The stream must be a hot, buffered, single-consumer channel flow (the `Channel` +
 * `receiveAsFlow` pattern) — that is what makes an effect emitted while nothing is
 * collecting wait rather than vanish.
 */
@Composable
fun <T> CollectEffects(effects: Flow<T>, onEffect: (T) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val handler by rememberUpdatedState(onEffect)
    LaunchedEffect(effects, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            effects.collect { handler(it) }
        }
    }
}

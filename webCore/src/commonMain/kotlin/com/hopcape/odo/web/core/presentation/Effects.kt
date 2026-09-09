package com.hopcape.odo.web.core.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.compose.getKoin
import org.koin.core.Koin
import kotlinx.coroutines.flow.Flow

/**
 * Runs one-shot effects — navigate, copy a link, focus a field — as they arrive.
 *
 * The handler is read through [rememberUpdatedState] so a recomposition that
 * hands in a new lambda does not restart the collection and lose an effect that
 * was in flight.
 */
@Composable
fun <T> CollectEffects(effects: Flow<T>, onEffect: (T) -> Unit) {
    val handler by rememberUpdatedState(onEffect)
    LaunchedEffect(effects) {
        effects.collect { handler(it) }
    }
}

/**
 * Scopes ViewModels to one route.
 *
 * Without this every screen's ViewModel shares the page's single store and is
 * keyed only by its type — so opening a second article would hand back the first
 * article's ViewModel, fully loaded with the wrong post. Keying the store by the
 * route's own location gives each page its own, and clearing it on the way out
 * means going back re-reads rather than showing what was on screen before.
 *
 * There is no state to preserve across that boundary, because the URL already
 * holds it: a route is reconstructible from the address bar, so a discarded
 * ViewModel loses nothing a reload would not also lose.
 */
@Composable
fun RouteScope(key: String, content: @Composable () -> Unit) {
    val owner = remember(key) { RouteViewModelStoreOwner() }
    DisposableEffect(key) {
        onDispose { owner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        content()
    }
}

/** A store with nothing else attached. One route's worth of ViewModels. */
private class RouteViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

/**
 * A ViewModel keyed by the route argument it was built for.
 *
 * `koinViewModel { parametersOf(id) }` was building two instances here: the one
 * the host drew and a second one that did the loading. The symptom was an editor
 * that stayed blank while every layer under it — the repository, the DI, the
 * ViewModel itself — passed its tests, because nothing had failed; the screen was
 * simply holding a different object than the one that had the data.
 *
 * This constructs it once, explicitly, into the route's own store. [key] is the
 * route argument, so opening a second post builds a second ViewModel rather than
 * handing back the first one still loaded with the wrong post.
 */
@Composable
inline fun <reified T : ViewModel> rememberRouteViewModel(
    key: String,
    crossinline create: Koin.() -> T,
): T {
    val koin = getKoin()
    return viewModel(key = key) { koin.create() }
}

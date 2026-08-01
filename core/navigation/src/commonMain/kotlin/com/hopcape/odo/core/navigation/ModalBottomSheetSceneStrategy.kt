package com.hopcape.odo.core.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * A Nav3 [SceneStrategy] that renders any entry tagged with [bottomSheet] metadata inside a
 * Material [ModalBottomSheet], overlaid on the entry beneath it.
 *
 * This lets a bottom sheet be a **real navigation destination** — you navigate to it, and
 * system-back / a scrim tap / a swipe-down dismisses it by popping the back stack (via
 * [ModalBottomSheet]'s `onDismissRequest`) — instead of a `Boolean` flag hoisted into a screen.
 *
 * Wire it on the [androidx.navigation3.ui.NavDisplay] ahead of the single-pane fallback (see
 * [OdoNavHost]) and tag the destination's entry:
 * ```
 * entry<OdoDestination.HealthScore.Info>(metadata = ModalBottomSheetSceneStrategy.bottomSheet()) {
 *     HowScoreWorksContent(onDismiss = { navigationManager.back() })
 * }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
class ModalBottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val entry = entries.lastOrNull() ?: return null
        val properties = entry.metadata[BottomSheetKey] ?: return null
        val sceneOnBack = onBack
        return object : OverlayScene<T> {
            override val key: Any = entry.contentKey
            override val entries: List<NavEntry<T>> = listOf(entry)
            override val previousEntries: List<NavEntry<T>> = entries.dropLast(1)
            // The entry directly beneath the sheet is drawn behind it.
            override val overlaidEntries: List<NavEntry<T>> = previousEntries.takeLast(1)

            override val content: @Composable () -> Unit = {
                ModalBottomSheet(
                    onDismissRequest = sceneOnBack,
                    containerColor = MaterialTheme.colorScheme.surface,
                    properties = properties,
                    // Never rest half-open. A sheet taller than half the screen is measured
                    // against the space it was given, so at the half-expanded height its
                    // lower half — the buttons included — lands below the screen with
                    // nothing to scroll. Short sheets still wrap their content and look
                    // exactly as before; tall ones now open against the full height and
                    // scroll inside it.
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                ) {
                    entry.Content()
                }
            }
        }
    }

    companion object {
        /** Metadata key marking an entry to be presented as a [ModalBottomSheet]. */
        object BottomSheetKey : NavMetadataKey<ModalBottomSheetProperties>

        /**
         * Tag an entry's `metadata` so [ModalBottomSheetSceneStrategy] renders it as a sheet.
         * No-arg so callers don't touch the experimental [ModalBottomSheetProperties] type.
         */
        fun bottomSheet(): Map<String, Any> = metadata { put(BottomSheetKey, ModalBottomSheetProperties()) }
    }
}

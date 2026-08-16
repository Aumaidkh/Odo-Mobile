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

        // Everything below the top sheet, with any *other* sheets skipped over.
        //
        // Two sheets cannot stack. Nav3 renders this scene's `overlaidEntries` and also
        // computes a scene for the entries beneath it, so an entry named in both is composed
        // twice in one pass — which throws out of SaveableStateHolder ("Key … was used
        // multiple times") and kills the app. It fired reliably on "Set fuel price" from the
        // confirm sheet: two sheet destinations on the stack, and the confirm entry was both
        // the overlaid entry and the next scene's own entry.
        //
        // Skipping to the last non-sheet entry collapses a run of sheets to the one on top.
        // What is drawn behind is the screen the owner came from rather than the sheet they
        // came from, which is also the more honest picture — a sheet stacked on a sheet reads
        // as one dialog interrupting another with no way to tell which is which.
        val beneath = entries.dropLast(1).dropLastWhile { it.metadata[BottomSheetKey] != null }

        // A sheet with nothing under it is not a sheet either. Nav3 requires an OverlayScene
        // to name at least one overlaid entry and throws when it names none: `Overlaid entries
        // ... must not be empty`. That happens when a sheet destination is the whole back
        // stack — the detected-fill notification opens straight into the confirm sheet, so a
        // cold start from the shade can land here before the start destination is on it.
        // Declining the scene hands the entry to the single-pane fallback, which draws the
        // same content full-screen. Worse-looking for one frame, and not a crash.
        if (beneath.isEmpty()) return null

        val sceneOnBack = onBack
        return object : OverlayScene<T> {
            override val key: Any = entry.contentKey
            override val entries: List<NavEntry<T>> = listOf(entry)
            override val previousEntries: List<NavEntry<T>> = beneath
            // The entry directly beneath the sheet is drawn behind it.
            override val overlaidEntries: List<NavEntry<T>> = beneath.takeLast(1)

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

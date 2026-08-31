package com.hopcape.odo

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.hopcape.odo.core.designsystem.modifier.thenIf

/**
 * Maps every `testTag` in the subtree onto the node's `resource-id`, which is the only
 * field UiAutomator exposes that a tag can travel in.
 *
 * Gated on the debuggable flag rather than on the build type so it covers `debug`, `stage`
 * and `minified` — the R8 build the E2E suite actually runs against — and stops at the
 * store `release`. Read off `ApplicationInfo` rather than `BuildConfig` so the check needs
 * no generated field and cannot fall out of step with what was actually built.
 */
@Composable
actual fun Modifier.debugTestTags(): Modifier {
    val context = LocalContext.current
    // The flag cannot change while the process lives, so it is worked out once rather than
    // on every recomposition of the app's root.
    val debuggable = remember(context) {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    return thenIf(debuggable) { semantics { testTagsAsResourceId = true } }
}

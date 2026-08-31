package com.hopcape.odo

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Publishes the subtree's Compose `testTag`s to the platform's accessibility tree, on
 * builds where that is safe to do.
 *
 * An out-of-process driver — UiAutomator, and so Appium, and so the odo-e2e suite — cannot
 * see a `testTag` at all. It reads the accessibility tree, where a tag only appears if the
 * app puts it there. Without this every tag selector in that suite fails as "not found",
 * which reads as a missing screen rather than as a missing opt-in.
 *
 * Deliberately not applied on a store build. There the tags would be readable by any
 * accessibility service on the device, for no benefit to anyone using one — the tags are
 * identifiers for tests, not labels for people. Each platform decides what "safe" means:
 * Android checks the debuggable flag, which covers `debug`, `stage` and `minified` and
 * stops at `release`.
 *
 * A no-op on platforms with no such mechanism, so the call site needs no branch.
 */
@Composable
expect fun Modifier.debugTestTags(): Modifier

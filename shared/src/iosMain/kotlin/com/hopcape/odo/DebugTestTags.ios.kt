package com.hopcape.odo

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * No-op.
 *
 * `testTagsAsResourceId` is an Android idea: it fills in a field of `AccessibilityNodeInfo`
 * that has no counterpart on iOS. XCUITest reads a different tree, and Compose Multiplatform
 * has no equivalent bridge to it today, so there is nothing to opt into here.
 */
@Composable
actual fun Modifier.debugTestTags(): Modifier = this

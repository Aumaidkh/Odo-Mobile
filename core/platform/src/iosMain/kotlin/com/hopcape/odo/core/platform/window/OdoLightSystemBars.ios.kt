package com.hopcape.odo.core.platform.window

import androidx.compose.runtime.Composable

/**
 * A no-op on iOS. The status-bar style there is decided by the hosting
 * `UIViewController`, not by whatever is currently composed, so a screen cannot claim it
 * the way it can on Android. Reported as nothing rather than faked.
 */
@Composable
actual fun OdoLightSystemBars() = Unit

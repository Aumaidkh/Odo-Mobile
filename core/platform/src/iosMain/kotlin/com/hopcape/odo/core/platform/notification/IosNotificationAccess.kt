package com.hopcape.odo.core.platform.notification

/**
 * iOS has no way for one app to read another's notifications, so this reports the truth: the
 * permission does not exist and cannot be asked for.
 *
 * Not a gap waiting to be filled. Detection is the one capture channel that is Android-only,
 * which is why the feature never depends on it — the pump scan and the prefilled form carry
 * it here, and the opt-in screen is simply never reachable.
 */
internal class IosNotificationAccess : NotificationAccess {

    override fun isGranted(): Boolean = false

    override fun isListenerDeclared(): Boolean = false

    override fun open() = Unit

    override fun requestRebind() = Unit

    override fun keepBound() = Unit

    override fun repairBinding() = Unit

    override fun isBound(): Boolean = false

    override fun releaseBinding() = Unit
}

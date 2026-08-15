package com.hopcape.odo.core.platform.notification

/**
 * iOS never detects a fill, because it cannot read another app's notifications, so there is
 * never anything for this to show.
 *
 * A no-op rather than an absent binding: the graph is the same on both platforms, and a
 * missing definition would fail at startup instead of at the one call site that would never
 * be reached anyway.
 */
internal class IosDetectedFillNotifier : DetectedFillNotifier {

    override suspend fun show(notice: DetectedFillNotice, onConfirm: suspend (String) -> Unit) = Unit

    override fun dismiss(noticeId: String) = Unit
}

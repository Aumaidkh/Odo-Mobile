package com.hopcape.odo.core.common.notification

/** The extra that says which kind of notification opened the app, and its values. */
object NotificationOpen {
    const val EXTRA_KIND = "notif_kind"

    /** On an engagement nudge, which nudge it was, so a tap can open its screen. */
    const val EXTRA_NUDGE = "nudge"

    const val DOCUMENT_REMINDER = "document_reminder"
    const val CUSTOM_REMINDER = "custom_reminder"
    const val ENGAGEMENT_NUDGE = "engagement_nudge"
    const val TRIP_TRACKING = "trip_tracking"
    const val DETECTED_FILL = "detected_fill"

    val kinds: List<String> =
        listOf(DOCUMENT_REMINDER, CUSTOM_REMINDER, ENGAGEMENT_NUDGE, TRIP_TRACKING, DETECTED_FILL)

    /**
     * A PendingIntent request code per kind. Intents that differ only in extras are equal, so
     * a shared code would let one kind's `FLAG_UPDATE_CURRENT` overwrite another's extra.
     */
    fun requestCode(kind: String): Int = 4_300 + kinds.indexOf(kind)
}

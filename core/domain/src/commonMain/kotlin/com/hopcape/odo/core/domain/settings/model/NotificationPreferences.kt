package com.hopcape.odo.core.domain.settings.model

/**
 * What the owner agreed to be told about, and how.
 *
 * The first six are topics — a reason Odo would reach out. [push] is the channel they
 * arrive on, and is separate because turning every topic off and turning notifications
 * off are different intentions, and the OS permission can revoke the channel without the
 * owner touching a topic.
 *
 * Defaults are the mockup's: the two deadline reminders and the two money insights on,
 * the two an owner has to opt into off. Nothing here can send anything by itself — the
 * reminder engine (M4) and the server reader are what consume these flags.
 */
data class NotificationPreferences(
    val documentExpiry: Boolean = true,
    val serviceDue: Boolean = true,
    val customReminders: Boolean = false,
    val overchargeAlerts: Boolean = true,
    val monthlySummary: Boolean = true,
    val healthScoreDrops: Boolean = false,
    val push: Boolean = true,
) {

    /**
     * How many topics are on — the "4 on" summary the profile row shows.
     *
     * Topics only: [push] is a channel, and counting it would make "turn everything off"
     * still read as "1 on".
     */
    val enabledTopics: Int
        get() = listOf(
            documentExpiry,
            serviceDue,
            customReminders,
            overchargeAlerts,
            monthlySummary,
            healthScoreDrops,
        ).count { it }
}

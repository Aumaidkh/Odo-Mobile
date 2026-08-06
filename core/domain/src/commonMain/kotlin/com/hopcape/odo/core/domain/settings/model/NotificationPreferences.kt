package com.hopcape.odo.core.domain.settings.model

/**
 * What the owner agreed to be told about, and how.
 *
 * The first seven are topics — a reason Odo would reach out. [push] and [whatsapp] are
 * the channels they arrive on, and are separate because turning every topic off and
 * turning notifications off are different intentions, and the OS permission can revoke
 * a channel without the owner touching a topic.
 *
 * Defaults are the mockup's: the two deadline reminders and the two money insights on,
 * everything an owner has to opt into off. Nothing here can send anything by itself — the
 * reminder engine (M4) and the server reader are what consume these flags.
 *
 * [whatsapp] defaults off because no WhatsApp sender exists yet (a PRD month-2 open
 * question); the flag is stored now so the server enum's `push_whatsapp` channel has a
 * preference to read when the sender lands. The Email channel is deliberately absent —
 * cut with the profile work, and there is no server enum value for it either.
 */
data class NotificationPreferences(
    val documentExpiry: Boolean = true,
    val serviceDue: Boolean = true,
    val customReminders: Boolean = false,
    val overchargeAlerts: Boolean = true,
    val monthlySummary: Boolean = true,
    val healthScoreDrops: Boolean = false,
    val partnerOffers: Boolean = false,
    val push: Boolean = true,
    val whatsapp: Boolean = false,
) {

    /**
     * How many topics are on — the "4 on" summary the profile row shows.
     *
     * Topics only: [push] and [whatsapp] are channels, and counting them would make
     * "turn everything off" still read as on.
     */
    val enabledTopics: Int
        get() = listOf(
            documentExpiry,
            serviceDue,
            customReminders,
            overchargeAlerts,
            monthlySummary,
            healthScoreDrops,
            partnerOffers,
        ).count { it }
}

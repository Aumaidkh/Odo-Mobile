package com.hopcape.odo.core.domain.reminder.model

import com.hopcape.odo.core.domain.document.model.DocumentType

/**
 * Why Odo is reminding the owner — the domain mirror of the server's `reminder_type`
 * enum (DB_SCHEMA §6).
 *
 * [LICENCE_EXPIRY] and [CUSTOM] are not in the server enum yet; both are schema deltas
 * the reminders data slice owes DB_SCHEMA.md. They exist here first because the client
 * derives licence renewals (DocumentReminderPolicy chases licences) and stores custom
 * reminders, whatever the server currently knows how to dispatch.
 */
enum class ReminderKind {
    INSURANCE_EXPIRY,
    PUC_EXPIRY,
    LICENCE_EXPIRY,
    SERVICE_DUE_KM,
    SERVICE_DUE_TIME,
    HEALTH_DROP,
    INACTIVITY,
    CUSTOM,
    ;

    companion object {
        /**
         * The kind that chases this paper, or `null` for types Odo never reminds about
         * (an RC is a lifetime document; a loan letter has no renewal). Matches which
         * types [DocumentReminderPolicy][com.hopcape.odo.core.domain.document.policy.DocumentReminderPolicy]
         * gives lead days.
         */
        fun forDocument(type: DocumentType): ReminderKind? = when (type) {
            DocumentType.INSURANCE -> INSURANCE_EXPIRY
            DocumentType.PUC -> PUC_EXPIRY
            DocumentType.LICENCE -> LICENCE_EXPIRY
            DocumentType.RC, DocumentType.LOAN, DocumentType.OTHER -> null
        }
    }
}

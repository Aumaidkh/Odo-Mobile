package com.hopcape.odo.core.domain.servicelog.model

/**
 * How a [ServiceLogEntry] came to exist. Mirrors the DB `log_source` enum
 * (DB_SCHEMA §9.2). Manual entries created in M1 are [MANUAL] with a null `billId`;
 * [SCANNED] is the M2 Bill Scanner hook.
 */
enum class LogSource {
    MANUAL,
    SCANNED,

    /**
     * The owner said a service happened, but not what it cost — the "when was your last
     * service?" answer at setup.
     *
     * Nothing else stores a last service: the health score, the reminders and the
     * pre-service checklist all read the newest entry, so the answer has to become one.
     * It carries a date and an odometer reading and no money, which is why the running
     * cost leaves it out.
     */
    DECLARED,
}

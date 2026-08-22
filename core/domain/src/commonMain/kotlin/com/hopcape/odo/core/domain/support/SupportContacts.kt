package com.hopcape.odo.core.domain.support

/**
 * How someone reaches Odo's support.
 *
 * A port, and configured remotely, for the same reason as
 * [com.hopcape.odo.core.domain.legal.LegalLinks]: the address is the only way an owner has
 * of reaching a person, and it must be changeable without shipping a release. Mailboxes get
 * renamed, providers get switched, and an address that bounces is worse than one that is a
 * version behind — every message sent to it is lost silently, and the sender believes it
 * arrived.
 *
 * Behind the remote answer sits the build's own, for the launch before the first fetch
 * lands and for a device that never reaches Firebase at all.
 *
 * A plain string, not an address type: every caller hands it straight to the mail composer,
 * and there is nothing here to parse or validate on the way.
 */
interface SupportContacts {

    /** Where "Email us" and the three feedback forms send their mail. Never blank. */
    val email: String

    companion object {
        /**
         * Koin qualifier for the build-time answer, the one the remotely-configured
         * implementation falls back to.
         *
         * A qualifier rather than a second type, for the reason
         * [com.hopcape.odo.core.domain.legal.LegalLinks.Companion.BUILT_IN] gives: the
         * decorator has to resolve the thing it decorates, and Koin's later-definition-wins
         * override leaves no other way to reach a replaced binding.
         */
        const val BUILT_IN: String = "support-contacts-built-in"
    }
}

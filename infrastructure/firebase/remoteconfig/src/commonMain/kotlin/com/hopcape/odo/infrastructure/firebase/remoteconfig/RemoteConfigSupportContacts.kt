package com.hopcape.odo.infrastructure.firebase.remoteconfig

import com.hopcape.odo.core.domain.support.SupportContacts

/**
 * The support address, configured from the Firebase console.
 *
 * Remote rather than compiled in because this is the only way an owner has of reaching a
 * person. A mailbox that is renamed, or a provider that is switched, would otherwise leave
 * every build already installed sending mail into an address that bounces — and a bounce
 * reaches the sender long after they have assumed the message arrived, if at all.
 *
 * **The read falls back to [builtIn].** A blank remote value covers the same three cases
 * [RemoteConfigLegalLinks] describes — key unset in the console, first fetch not landed on a
 * fresh install, Firebase unreachable — and in all three the build's own address is better
 * than nothing. An empty support address is not a degraded state, it is a dead end.
 *
 * Trimmed, because an address pasted into the console picks up whitespace often, and a
 * leading space is enough to make the composer refuse it.
 */
internal class RemoteConfigSupportContacts(
    private val gateway: FirebaseRemoteConfigGateway,
    private val builtIn: SupportContacts,
) : SupportContacts {

    override val email: String
        get() = gateway.string(KEY_SUPPORT_EMAIL)?.trim()?.takeIf { it.isNotEmpty() }
            ?: builtIn.email

    internal companion object {
        const val KEY_SUPPORT_EMAIL = "support_email"

        /**
         * Empty on purpose — see the class comment. The key is declared to the SDK here; the
         * build's own [SupportContacts] is the real default.
         */
        val REMOTE_DEFAULTS: Map<String, Any> = mapOf(KEY_SUPPORT_EMAIL to "")
    }
}

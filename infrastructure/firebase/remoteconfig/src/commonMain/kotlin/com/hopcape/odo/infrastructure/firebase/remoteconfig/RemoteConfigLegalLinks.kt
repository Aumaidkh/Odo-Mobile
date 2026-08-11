package com.hopcape.odo.infrastructure.firebase.remoteconfig

import com.hopcape.odo.core.domain.legal.LegalLinks

/**
 * The published legal URLs, configured from the Firebase console.
 *
 * Remote rather than compiled in because these are the addresses a store reviewer opens and a
 * user in the middle of leaving opens. Moving the pages to a custom domain, or fixing a URL
 * that turns out to be wrong, has to be possible without shipping a release and waiting for
 * everyone to update.
 *
 * **Every read falls back to [builtIn].** A blank remote value covers three cases that all
 * mean the same thing — the key is unset in the console, the first fetch has not landed yet
 * on a fresh install, or Firebase is unreachable on this device — and in all three the
 * build's own answer is better than nothing. That is also why the local defaults in
 * `remote_config_defaults.xml` are deliberately empty: an override, not a duplicate of what
 * the build already knows.
 *
 * Values are trimmed, because a URL pasted into the console picks up whitespace remarkably
 * often and a leading space is enough to break the link.
 */
internal class RemoteConfigLegalLinks(
    private val gateway: FirebaseRemoteConfigGateway,
    private val builtIn: LegalLinks,
) : LegalLinks {

    override val privacyPolicy: String
        get() = override(KEY_PRIVACY_POLICY) ?: builtIn.privacyPolicy

    override val termsOfUse: String
        get() = override(KEY_TERMS_OF_USE) ?: builtIn.termsOfUse

    override val deleteAccount: String
        get() = override(KEY_DELETE_ACCOUNT) ?: builtIn.deleteAccount

    /** The console's answer for [key], or null when there is not a usable one. */
    private fun override(key: String): String? =
        gateway.string(key)?.trim()?.takeIf { it.isNotEmpty() }

    internal companion object {
        const val KEY_PRIVACY_POLICY = "legal_privacy_policy_url"
        const val KEY_TERMS_OF_USE = "legal_terms_url"
        const val KEY_DELETE_ACCOUNT = "legal_delete_account_url"

        /**
         * Empty on purpose — see the class comment. These exist so the keys are declared to
         * the SDK; the build's own [LegalLinks] is the real default.
         *
         * Kept in sync by hand with `androidMain/res/xml/remote_config_defaults.xml`, which
         * is the canonical copy on Android. Change one, change both.
         */
        val REMOTE_DEFAULTS: Map<String, Any> = mapOf(
            KEY_PRIVACY_POLICY to "",
            KEY_TERMS_OF_USE to "",
            KEY_DELETE_ACCOUNT to "",
        )
    }
}

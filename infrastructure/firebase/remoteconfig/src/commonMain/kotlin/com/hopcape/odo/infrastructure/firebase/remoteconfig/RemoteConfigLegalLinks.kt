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
 * build's own answer is better than nothing. That is also why every default in
 * [LegalConfig] is deliberately blank: an override, not a duplicate of what the build
 * already knows.
 *
 * Values are trimmed, because a URL pasted into the console picks up whitespace remarkably
 * often and a leading space is enough to break the link.
 */
internal class RemoteConfigLegalLinks(
    private val config: LegalConfig,
    private val builtIn: LegalLinks,
) : LegalLinks {

    override val privacyPolicy: String
        get() = config.privacyPolicyUrl.orBuiltIn(builtIn.privacyPolicy)

    override val termsOfUse: String
        get() = config.termsUrl.orBuiltIn(builtIn.termsOfUse)

    override val deleteAccount: String
        get() = config.deleteAccountUrl.orBuiltIn(builtIn.deleteAccount)

    /**
     * Blank is "no override", not "the empty string". Whitespace was already removed by the
     * source, which trims every value it reads.
     */
    private fun String.orBuiltIn(fallback: String): String = takeIf { it.isNotEmpty() } ?: fallback
}

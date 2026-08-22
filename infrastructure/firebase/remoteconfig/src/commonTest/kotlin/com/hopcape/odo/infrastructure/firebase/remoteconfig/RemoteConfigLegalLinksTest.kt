package com.hopcape.odo.infrastructure.firebase.remoteconfig

import com.hopcape.odo.core.domain.legal.LegalLinks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The console overriding the build's own legal URLs, and — more importantly — not overriding
 * them when it has nothing to say.
 *
 * The fallback is the half that matters. These are the addresses a store reviewer and a
 * leaving user open; "Firebase was slow, so the privacy policy row disappeared" is not an
 * acceptable failure mode, and every test below is about making sure it cannot happen.
 */
class RemoteConfigLegalLinksTest {

    private val builtIn = FixedLinks(
        privacyPolicy = "https://project.supabase.co/functions/v1/legal/privacy",
        termsOfUse = "https://project.supabase.co/functions/v1/legal/terms",
        deleteAccount = "https://project.supabase.co/functions/v1/legal/delete-account",
    )

    private fun links(values: Map<String, Any> = emptyMap()) =
        RemoteConfigLegalLinks(gateway = FakeGateway(values), builtIn = builtIn)

    @Test
    fun `the console wins when it has a value`() {
        val links = links(
            mapOf(
                RemoteConfigLegalLinks.KEY_PRIVACY_POLICY to "https://odo.app/privacy",
                RemoteConfigLegalLinks.KEY_TERMS_OF_USE to "https://odo.app/terms",
                RemoteConfigLegalLinks.KEY_DELETE_ACCOUNT to "https://odo.app/delete",
            ),
        )

        // The whole point: a move to a custom domain needs a console edit, not a release.
        assertEquals("https://odo.app/privacy", links.privacyPolicy)
        assertEquals("https://odo.app/terms", links.termsOfUse)
        assertEquals("https://odo.app/delete", links.deleteAccount)
    }

    @Test
    fun `an unset key falls back to the build's own address`() {
        // What a fresh install answers before its first fetch lands, and what a device that
        // never reaches Firebase answers forever.
        assertEquals(builtIn.privacyPolicy, links().privacyPolicy)
        assertEquals(builtIn.termsOfUse, links().termsOfUse)
        assertEquals(builtIn.deleteAccount, links().deleteAccount)
    }

    @Test
    fun `a blank value is treated as no override, not as a blank URL`() {
        // This is exactly what the local defaults in REMOTE_DEFAULTS hold, so it
        // is the ordinary case rather than an edge one.
        val links = links(mapOf(RemoteConfigLegalLinks.KEY_PRIVACY_POLICY to ""))

        assertEquals(builtIn.privacyPolicy, links.privacyPolicy)
    }

    @Test
    fun `whitespace around a pasted URL is trimmed away`() {
        val links = links(mapOf(RemoteConfigLegalLinks.KEY_TERMS_OF_USE to "  https://odo.app/terms\n"))

        // A URL pasted into the console picks up whitespace remarkably often, and one leading
        // space is enough to break the link.
        assertEquals("https://odo.app/terms", links.termsOfUse)
    }

    @Test
    fun `a value that is only whitespace falls back`() {
        val links = links(mapOf(RemoteConfigLegalLinks.KEY_DELETE_ACCOUNT to "   "))

        assertEquals(builtIn.deleteAccount, links.deleteAccount)
    }

    @Test
    fun `overriding one link leaves the others on the build's answer`() {
        val links = links(mapOf(RemoteConfigLegalLinks.KEY_PRIVACY_POLICY to "https://odo.app/privacy"))

        // Each key stands alone, so a partial console setup cannot blank the rest.
        assertEquals("https://odo.app/privacy", links.privacyPolicy)
        assertEquals(builtIn.termsOfUse, links.termsOfUse)
        assertEquals(builtIn.deleteAccount, links.deleteAccount)
    }

    @Test
    fun `an unconfigured build with no console value stays blank`() {
        val unconfigured = FixedLinks("", "", "")
        val links = RemoteConfigLegalLinks(gateway = FakeGateway(emptyMap()), builtIn = unconfigured)

        // Blank means "not configured", and the screens leave the row out rather than
        // offering a link that goes nowhere.
        assertEquals("", links.privacyPolicy)
    }

    @Test
    fun `defaults are declared for every key the class reads`() {
        // The map is what non-Android platforms hand the SDK. A key read but not declared
        // there is one the console can never override on those platforms.
        assertEquals(
            setOf(
                RemoteConfigLegalLinks.KEY_PRIVACY_POLICY,
                RemoteConfigLegalLinks.KEY_TERMS_OF_USE,
                RemoteConfigLegalLinks.KEY_DELETE_ACCOUNT,
            ),
            RemoteConfigLegalLinks.REMOTE_DEFAULTS.keys,
        )
    }

    private class FixedLinks(
        override val privacyPolicy: String,
        override val termsOfUse: String,
        override val deleteAccount: String,
    ) : LegalLinks

    /** [lastFetchAt] is irrelevant here — these reads serve whatever is in force either way. */
    private class FakeGateway(private val values: Map<String, Any>) : FirebaseRemoteConfigGateway {
        override val lastFetchAt: Instant? = null
        override suspend fun fetchAndActivate(): Boolean = true
        override fun long(key: String): Long? = values[key] as? Long
        override fun string(key: String): String? = values[key] as? String
    }
}

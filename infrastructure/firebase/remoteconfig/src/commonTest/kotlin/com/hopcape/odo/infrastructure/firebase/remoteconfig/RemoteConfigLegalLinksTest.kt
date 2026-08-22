package com.hopcape.odo.infrastructure.firebase.remoteconfig

import com.hopcape.odo.core.config.ConfigRegistry
import com.hopcape.odo.core.domain.legal.LegalLinks
import kotlin.test.Test
import kotlin.test.assertEquals

private const val BUILT_IN_PRIVACY = "https://built-in.example/privacy"
private const val BUILT_IN_TERMS = "https://built-in.example/terms"
private const val BUILT_IN_DELETE = "https://built-in.example/delete"

class RemoteConfigLegalLinksTest {

    @Test
    fun `the console wins when it has a value`() {
        val links = links(LegalConfigContribution.LEGAL_PRIVACY_POLICY_URL to "https://console.example/privacy")

        assertEquals("https://console.example/privacy", links.privacyPolicy)
    }

    @Test
    fun `an unset key falls back to the build's own address`() {
        assertEquals(BUILT_IN_PRIVACY, links().privacyPolicy)
    }

    @Test
    fun `a blank value is treated as no override rather than as a blank URL`() {
        val links = links(LegalConfigContribution.LEGAL_PRIVACY_POLICY_URL to "")

        assertEquals(BUILT_IN_PRIVACY, links.privacyPolicy)
    }

    @Test
    fun `whitespace around a pasted URL is trimmed away`() {
        val links = links(LegalConfigContribution.LEGAL_TERMS_URL to "  https://console.example/terms  ")

        assertEquals("https://console.example/terms", links.termsOfUse)
    }

    @Test
    fun `a value that is only whitespace falls back`() {
        val links = links(LegalConfigContribution.LEGAL_DELETE_ACCOUNT_URL to "   ")

        assertEquals(BUILT_IN_DELETE, links.deleteAccount)
    }

    @Test
    fun `overriding one link leaves the others on the build's answer`() {
        val links = links(LegalConfigContribution.LEGAL_TERMS_URL to "https://console.example/terms")

        assertEquals("https://console.example/terms", links.termsOfUse)
        assertEquals(BUILT_IN_PRIVACY, links.privacyPolicy)
        assertEquals(BUILT_IN_DELETE, links.deleteAccount)
    }

    @Test
    fun `defaults are declared for every key the class reads`() {
        val defaults = ConfigRegistry(listOf(LegalConfigContribution)).defaults()

        assertEquals(
            setOf(
                LegalConfigContribution.LEGAL_PRIVACY_POLICY_URL,
                LegalConfigContribution.LEGAL_TERMS_URL,
                LegalConfigContribution.LEGAL_DELETE_ACCOUNT_URL,
            ),
            defaults.keys,
        )
        // Blank on purpose: the key is declared to the SDK, and the build's own address is
        // the real default. A URL here would freeze one project's address into every build.
        assertEquals(setOf(""), defaults.values.toSet())
    }

    private fun links(vararg values: Pair<String, String>): RemoteConfigLegalLinks {
        val gateway = FakeGateway(values.toMap().toMutableMap())
        return RemoteConfigLegalLinks(
            config = LegalConfigImpl(resolverOver(gateway, LegalConfigContribution)),
            builtIn = BuiltInLinks,
        )
    }

    private object BuiltInLinks : LegalLinks {
        override val privacyPolicy = BUILT_IN_PRIVACY
        override val termsOfUse = BUILT_IN_TERMS
        override val deleteAccount = BUILT_IN_DELETE
    }
}

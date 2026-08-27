package com.hopcape.odo.infrastructure.firebase.remoteconfig

import com.hopcape.odo.core.config.ConfigRegistry
import com.hopcape.odo.core.domain.support.SupportContacts
import kotlin.test.Test
import kotlin.test.assertEquals

private const val BUILT_IN_EMAIL = "built-in@example.com"
private const val KEY = SupportConfigContribution.SUPPORT_EMAIL

class RemoteConfigSupportContactsTest {

    @Test
    fun `the console wins when it has an address`() {
        assertEquals("console@example.com", contacts(KEY to "console@example.com").email)
    }

    @Test
    fun `an unset key falls back to the build's own address`() {
        assertEquals(BUILT_IN_EMAIL, contacts().email)
    }

    @Test
    fun `a blank value is treated as no override rather than as a blank address`() {
        // An empty support address is a dead end, not a degraded state.
        assertEquals(BUILT_IN_EMAIL, contacts(KEY to "").email)
    }

    @Test
    fun `a value that is only whitespace falls back`() {
        assertEquals(BUILT_IN_EMAIL, contacts(KEY to "   ").email)
    }

    @Test
    fun `whitespace around a pasted address is trimmed away`() {
        // One leading space is enough to make the mail composer refuse it.
        assertEquals("console@example.com", contacts(KEY to "  console@example.com  ").email)
    }

    @Test
    fun `the address is read fresh so a fetch landing mid-session is picked up`() {
        val gateway = FakeGateway()
        val subject = RemoteConfigSupportContacts(
            config = SupportConfigImpl(resolverOver(gateway, SupportConfigContribution)),
            builtIn = BuiltInContacts,
        )
        assertEquals(BUILT_IN_EMAIL, subject.email)

        gateway[KEY] = "moved@example.com"

        assertEquals("moved@example.com", subject.email)
    }

    @Test
    fun `defaults are declared for every key the class reads`() {
        val defaults = ConfigRegistry(listOf(SupportConfigContribution)).defaults()

        assertEquals(mapOf(KEY to ""), defaults)
    }

    private fun contacts(vararg values: Pair<String, String>): RemoteConfigSupportContacts {
        val gateway = FakeGateway(values.toMap().toMutableMap())
        return RemoteConfigSupportContacts(
            config = SupportConfigImpl(resolverOver(gateway, SupportConfigContribution)),
            builtIn = BuiltInContacts,
        )
    }

    private object BuiltInContacts : SupportContacts {
        override val email = BUILT_IN_EMAIL
    }
}

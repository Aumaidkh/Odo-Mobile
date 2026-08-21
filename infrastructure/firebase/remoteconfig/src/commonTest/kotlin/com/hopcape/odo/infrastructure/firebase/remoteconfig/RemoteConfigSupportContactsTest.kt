package com.hopcape.odo.infrastructure.firebase.remoteconfig

import com.hopcape.odo.core.domain.support.SupportContacts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The console moving the support mailbox, and — the half that matters — never emptying it.
 *
 * A blank support address is not a degraded state. Every row that contacts support goes
 * through this one value, and an empty one turns all of them into buttons that open a mail
 * app addressed to nobody. Every test below exists to make sure that cannot happen.
 */
class RemoteConfigSupportContactsTest {

    private val builtIn = FixedContacts("support@odoapp.in")

    private fun contacts(values: Map<String, Any> = emptyMap()) =
        RemoteConfigSupportContacts(gateway = FakeGateway(values), builtIn = builtIn)

    @Test
    fun `the console wins when it has an address`() {
        val contacts = contacts(mapOf(RemoteConfigSupportContacts.KEY_SUPPORT_EMAIL to "help@odoapp.in"))

        // The whole point: moving support to another mailbox needs a console edit, not a
        // release that half the installed fleet will not take for weeks.
        assertEquals("help@odoapp.in", contacts.email)
    }

    @Test
    fun `an unset key falls back to the build's own address`() {
        // A fresh install before its first fetch lands, and a device that never reaches
        // Firebase at all.
        assertEquals(builtIn.email, contacts().email)
    }

    @Test
    fun `a blank value is treated as no override, not as a blank address`() {
        // Exactly what remote_config_defaults.xml holds, so this is the ordinary case.
        assertEquals(builtIn.email, contacts(mapOf(RemoteConfigSupportContacts.KEY_SUPPORT_EMAIL to "")).email)
    }

    @Test
    fun `a value that is only whitespace falls back`() {
        assertEquals(builtIn.email, contacts(mapOf(RemoteConfigSupportContacts.KEY_SUPPORT_EMAIL to "   ")).email)
    }

    @Test
    fun `whitespace around a pasted address is trimmed away`() {
        val contacts = contacts(mapOf(RemoteConfigSupportContacts.KEY_SUPPORT_EMAIL to "  help@odoapp.in\n"))

        // A leading space is enough to make the mail composer refuse the recipient.
        assertEquals("help@odoapp.in", contacts.email)
    }

    @Test
    fun `the address is read fresh, so a fetch landing mid-session is picked up`() {
        val values = mutableMapOf<String, Any>()
        val contacts = RemoteConfigSupportContacts(gateway = FakeGateway(values), builtIn = builtIn)
        assertEquals(builtIn.email, contacts.email)

        values[RemoteConfigSupportContacts.KEY_SUPPORT_EMAIL] = "help@odoapp.in"

        // Not captured at construction: the sheet resolves this on each composition, and a
        // config that arrives while the app is open should reach the next screen that asks.
        assertEquals("help@odoapp.in", contacts.email)
    }

    @Test
    fun `defaults are declared for every key the class reads`() {
        // The map is what non-Android platforms hand the SDK. A key read but not declared
        // there is one the console can never override on those platforms.
        assertEquals(
            setOf(RemoteConfigSupportContacts.KEY_SUPPORT_EMAIL),
            RemoteConfigSupportContacts.REMOTE_DEFAULTS.keys,
        )
    }

    private class FixedContacts(override val email: String) : SupportContacts

    /** [lastFetchAt] is irrelevant here — these reads serve whatever is in force either way. */
    private class FakeGateway(private val values: Map<String, Any>) : FirebaseRemoteConfigGateway {
        override val lastFetchAt: Instant? = null
        override suspend fun fetchAndActivate(): Boolean = true
        override fun long(key: String): Long? = values[key] as? Long
        override fun string(key: String): String? = values[key] as? String
    }
}

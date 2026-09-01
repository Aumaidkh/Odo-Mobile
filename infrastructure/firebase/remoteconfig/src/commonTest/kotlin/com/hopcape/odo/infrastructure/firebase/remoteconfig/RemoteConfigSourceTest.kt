package com.hopcape.odo.infrastructure.firebase.remoteconfig

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class RemoteConfigSourceTest {

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Test
    fun `each type is parsed from the stored string`() {
        val source = RemoteConfigSource(
            FakeGateway(
                values = mapOf(
                    "a_flag" to "true",
                    "an_int" to "42",
                    "a_long" to "9999999999",
                    "a_double" to "1.5",
                    "a_string" to "hello",
                ),
            ),
        )

        assertEquals(true, source.boolean("a_flag"))
        assertEquals(42, source.int("an_int"))
        assertEquals(9_999_999_999L, source.long("a_long"))
        assertEquals(1.5, source.double("a_double"))
        assertEquals("hello", source.string("a_string"))
    }

    @Test
    fun `an unset key reads as null on every type`() {
        // Remote Config answers "" for a key the console never set, and the contract
        // requires that be indistinguishable from absent — otherwise a legal URL whose
        // default is deliberately blank would stop falling back to the built-in one.
        val source = RemoteConfigSource(FakeGateway(values = mapOf("blank" to "")))

        assertNull(source.boolean("blank"))
        assertNull(source.int("blank"))
        assertNull(source.long("blank"))
        assertNull(source.double("blank"))
        assertNull(source.string("blank"))
        assertNull(source.string("never_set_at_all"))
    }

    @Test
    fun `a value that does not parse reads as null rather than as zero`() {
        val source = RemoteConfigSource(FakeGateway(values = mapOf("an_int" to "forty two")))

        assertNull(source.int("an_int"))
    }

    @Test
    fun `the SDK's loose truthiness is deliberately not inherited`() {
        // Firebase's own asBoolean accepts yes, on, 1, t, y. Reading through the string
        // value means only true and false count, and anything else falls through to the
        // compiled default instead of being guessed at.
        val source = RemoteConfigSource(
            FakeGateway(values = mapOf("yes" to "yes", "one" to "1", "on" to "on")),
        )

        assertNull(source.boolean("yes"))
        assertNull(source.boolean("one"))
        assertNull(source.boolean("on"))
    }

    // ── Refresh and the generation counter ────────────────────────────────────

    @Test
    fun `a fetch that activates new values bumps the generation`() = runTest {
        val gateway = FakeGateway(activates = true)
        val source = RemoteConfigSource(gateway)

        assertEquals(0L, source.generation.value)
        source.refresh()
        assertEquals(1L, source.generation.value)
        source.refresh()
        assertEquals(2L, source.generation.value)
    }

    @Test
    fun `a fetch that activates nothing leaves the generation alone`() = runTest {
        // A throttled fetch on a fresh install, an unreachable backend and a fetch that
        // simply had no news all look the same from here. None of them should make a
        // screen re-render.
        val source = RemoteConfigSource(FakeGateway(activates = false))

        source.refresh()

        assertEquals(0L, source.generation.value)
    }

    @Test
    fun `refresh does not throw when the gateway reports a failure`() = runTest {
        // The gateway swallows every SDK failure into a diagnostic and returns false, so
        // there is nothing here to catch — this pins that contract rather than assuming it.
        val source = RemoteConfigSource(FakeGateway(activates = false))

        source.refresh()

        assertEquals(0L, source.generation.value)
    }

    private class FakeGateway(
        private val values: Map<String, String> = emptyMap(),
        private val activates: Boolean = true,
    ) : FirebaseRemoteConfigGateway {
        override val lastFetchAt: Instant? = null
        override suspend fun fetchAndActivate(): Boolean = activates
        override fun long(key: String): Long? = values[key]?.toLongOrNull()
        override fun string(key: String): String? = values[key]
    }
}

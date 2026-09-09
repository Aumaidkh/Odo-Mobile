package com.hopcape.odo.core.config

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The chain is what lets one key be answered by Remote Config and the next by the
 * `app_config` table, without either backend knowing the other exists.
 */
class ChainedConfigSourceTest {

    @Test
    fun `the first source holding the key answers`() {
        val first = FakeConfigSource()
        val second = FakeConfigSource()
        first.activate("a_string" to "from first")
        second.activate("a_string" to "from second")

        val chain = ChainedConfigSource(listOf(first, second))

        assertEquals("from first", chain.string("a_string"))
    }

    @Test
    fun `a key the first source has nothing for falls through to the second`() {
        // The case the whole chain exists for: a flag nobody has set in the Remote Config
        // console is still whatever the admin panel wrote to app_config, rather than
        // dropping all the way to the compiled default.
        val first = FakeConfigSource()
        val second = FakeConfigSource()
        second.activate("a_string" to "from second")

        val chain = ChainedConfigSource(listOf(first, second))

        assertEquals("from second", chain.string("a_string"))
    }

    @Test
    fun `precedence is per key rather than per source`() {
        val first = FakeConfigSource()
        val second = FakeConfigSource()
        first.activate("a_flag" to "true")
        second.activate("an_int" to "42")

        val chain = ChainedConfigSource(listOf(first, second))

        assertEquals(true, chain.boolean("a_flag"))
        assertEquals(42, chain.int("an_int"))
    }

    @Test
    fun `a key no source holds reads as null on every type`() {
        val chain = ChainedConfigSource(listOf(FakeConfigSource(), FakeConfigSource()))

        assertNull(chain.boolean("a_flag"))
        assertNull(chain.int("an_int"))
        assertNull(chain.long("a_long"))
        assertNull(chain.double("a_double"))
        assertNull(chain.string("a_string"))
    }

    @Test
    fun `an empty chain answers null and never bumps`() = runTest {
        val chain = ChainedConfigSource(emptyList())

        chain.refresh()

        assertNull(chain.string("a_string"))
        assertEquals(0L, chain.generation.value)
    }

    @Test
    fun `refreshing refreshes every source`() = runTest {
        val first = RecordingSource()
        val second = RecordingSource()

        ChainedConfigSource(listOf(first, second)).refresh()

        assertEquals(1, first.refreshes)
        assertEquals(1, second.refreshes)
    }

    @Test
    fun `a source that cannot refresh does not stop the ones after it`() {
        // NoRemoteConfigSource is a ConfigSource and nothing else, and it is what a build
        // with no backend for one of the two slots resolves to.
        val last = RecordingSource()

        runTest { ChainedConfigSource(listOf(NoRemoteConfigSource, last)).refresh() }

        assertEquals(1, last.refreshes)
    }

    @Test
    fun `the generation follows every source in the chain`() = runTest {
        // ConfigResolver.observe maps this counter, so a bump in either backend has to
        // reach it — a flag flipped in the console that leaves every screen showing the
        // previous answer is the same bug as not fetching at all.
        val first = FakeConfigSource()
        val second = FakeConfigSource()
        val chain = ChainedConfigSource(listOf(first, second))

        assertEquals(0L, chain.generation.value)

        first.activate("a_string" to "one")
        chain.refresh()
        assertEquals(1L, chain.generation.value)

        second.activate("a_string" to "two")
        chain.refresh()
        assertEquals(2L, chain.generation.value)
    }

    @Test
    fun `a refresh that activates nothing leaves the generation alone`() = runTest {
        val chain = ChainedConfigSource(listOf(FakeConfigSource(), FakeConfigSource()))

        chain.refresh()
        chain.refresh()

        assertEquals(0L, chain.generation.value)
    }
}

package com.hopcape.odo.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConfigResolverTest {

    // ── Resolution order ──────────────────────────────────────────────────────

    @Test
    fun `override wins over remote and default`() {
        val source = FakeConfigSource()
        source.activate(SampleConfigContribution.ENABLED to "false")
        val overrides = FakeOverrides().apply { set(SampleConfigContribution.ENABLED, "true") }

        assertEquals(true, resolver(source, overrides).boolean(SampleConfigContribution.ENABLED))
    }

    @Test
    fun `remote wins over default`() {
        val source = FakeConfigSource()
        source.activate(SampleConfigContribution.ENABLED to "false")

        assertEquals(false, resolver(source).boolean(SampleConfigContribution.ENABLED))
    }

    @Test
    fun `release build has no override store and still resolves`() {
        // overrides = null is exactly what a release build injects. The branch is not
        // compiled out; there is simply nothing behind it.
        val source = FakeConfigSource()
        source.activate(SampleConfigContribution.RETRY_COUNT to "7")

        assertEquals(7, resolver(source, overrides = null).int(SampleConfigContribution.RETRY_COUNT))
    }

    @Test
    fun `fresh install with no network answers with the compiled default`() {
        // Nothing activated, no overrides: the first seconds of every install's life.
        val subject = resolver()

        assertEquals(true, subject.boolean(SampleConfigContribution.ENABLED))
        assertEquals(3, subject.int(SampleConfigContribution.RETRY_COUNT))
        assertEquals(SampleMode.OFF.name, subject.enumName(SampleConfigContribution.MODE))
    }

    @Test
    fun `a blank remote value means no override rather than the empty string`() {
        val source = FakeConfigSource()
        source.activate(SampleConfigContribution.ENABLED to "")

        assertEquals(true, resolver(source).boolean(SampleConfigContribution.ENABLED))
    }

    // ── Values that must be skipped ───────────────────────────────────────────

    @Test
    fun `an override that does not parse falls through to remote`() {
        val source = FakeConfigSource()
        source.activate(SampleConfigContribution.RETRY_COUNT to "5")
        val overrides = FakeOverrides().apply { set(SampleConfigContribution.RETRY_COUNT, "many") }

        assertEquals(5, resolver(source, overrides).int(SampleConfigContribution.RETRY_COUNT))
    }

    @Test
    fun `a remote value outside the declared range falls through to the default`() {
        // The build can only vouch for the compiled default. A console typo is the
        // case this rule exists for.
        val source = FakeConfigSource()
        source.activate(SampleConfigContribution.RETRY_COUNT to "9999")

        assertEquals(3, resolver(source).int(SampleConfigContribution.RETRY_COUNT))
    }

    @Test
    fun `a remote value at either end of the range is accepted`() {
        val low = FakeConfigSource().apply { activate(SampleConfigContribution.RETRY_COUNT to "0") }
        val high = FakeConfigSource().apply { activate(SampleConfigContribution.RETRY_COUNT to "10") }

        assertEquals(0, resolver(low).int(SampleConfigContribution.RETRY_COUNT))
        assertEquals(10, resolver(high).int(SampleConfigContribution.RETRY_COUNT))
    }

    @Test
    fun `an enum name that is not declared falls through`() {
        // A constant removed in a later release, or a typo in the console. The
        // generated code calls valueOf on this, so it must never be a surprise.
        val source = FakeConfigSource()
        source.activate(SampleConfigContribution.MODE to "sideways")

        assertEquals(SampleMode.OFF.name, resolver(source).enumName(SampleConfigContribution.MODE))
    }

    @Test
    fun `an enum name is matched ignoring case and comes back canonical`() {
        // The console holds "on"; the Kotlin constant is ON, and generated code calls
        // valueOf, which is case-sensitive.
        val source = FakeConfigSource()
        source.activate(SampleConfigContribution.MODE to "on")

        assertEquals("ON", resolver(source).enumName(SampleConfigContribution.MODE))
    }

    // ── describe(), which is what the QA screen reads ─────────────────────────

    @Test
    fun `describe reports which step of the order answered`() {
        val source = FakeConfigSource()
        source.activate(SampleConfigContribution.RETRY_COUNT to "5")
        val overrides = FakeOverrides().apply { set(SampleConfigContribution.ENABLED, "false") }
        val subject = resolver(source, overrides)

        assertEquals(ConfigValueSource.OVERRIDE, subject.describe(SampleConfigContribution.ENABLED).source)
        assertEquals(ConfigValueSource.REMOTE, subject.describe(SampleConfigContribution.RETRY_COUNT).source)
        assertEquals(ConfigValueSource.DEFAULT, subject.describe(SampleConfigContribution.MODE).source)
    }

    @Test
    fun `describe agrees with the value the read returns`() {
        val source = FakeConfigSource()
        source.activate(SampleConfigContribution.RETRY_COUNT to "5")
        val subject = resolver(source)

        assertEquals("5", subject.describe(SampleConfigContribution.RETRY_COUNT).value)
        assertEquals(5, subject.int(SampleConfigContribution.RETRY_COUNT))
    }

    @Test
    fun `describe skips a value the read would skip`() {
        // Out of range, so the read falls through to the default — and describe has to say
        // DEFAULT, not REMOTE, or the screen would explain the wrong thing.
        val source = FakeConfigSource()
        source.activate(SampleConfigContribution.RETRY_COUNT to "9999")
        val described = resolver(source).describe(SampleConfigContribution.RETRY_COUNT)

        assertEquals(ConfigValueSource.DEFAULT, described.source)
        assertEquals("3", described.value)
    }

    @Test
    fun `describeAll covers every registered key`() {
        assertEquals(3, resolver().describeAll().size)
    }

    // ── Programming errors, which should not be papered over ──────────────────

    @Test
    fun `reading an unregistered key fails loudly`() {
        val failure = assertFailsWith<IllegalStateException> {
            resolver().boolean("sample_not_declared")
        }
        assertTrue(failure.message.orEmpty().contains("not registered"))
    }

    @Test
    fun `reading a key as the wrong type fails loudly`() {
        val failure = assertFailsWith<IllegalStateException> {
            resolver().int(SampleConfigContribution.ENABLED)
        }
        assertTrue(failure.message.orEmpty().contains("BOOLEAN"))
    }
}

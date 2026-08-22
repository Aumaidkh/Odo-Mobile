package com.hopcape.odo.core.config

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The end-to-end proof the slice exists for: a config group written by hand, in the
 * shape KSP will generate, resolving through the real registry and resolver.
 *
 * A consumer injects [SampleConfig] and reads a property. A test injects a
 * hand-written fake of the same interface — no backend, no DI container, no build
 * flag to branch on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SampleConfigTest {

    @Test
    fun `a group reads its compiled defaults with nothing behind it`() {
        val config: SampleConfig = SampleConfigImpl(resolver())

        assertEquals(true, config.enabled)
        assertEquals(3, config.retryCount)
        assertEquals(SampleMode.OFF, config.mode)
    }

    @Test
    fun `a group reads remote values once a fetch has landed`() {
        val source = FakeConfigSource()
        source.activate(
            SampleConfigContribution.ENABLED to "false",
            SampleConfigContribution.RETRY_COUNT to "6",
            SampleConfigContribution.MODE to "degraded",
        )

        val config: SampleConfig = SampleConfigImpl(resolver(source))

        assertEquals(false, config.enabled)
        assertEquals(6, config.retryCount)
        assertEquals(SampleMode.DEGRADED, config.mode)
    }

    @Test
    fun `a group's flows follow a fetch without an app restart`() = runTest {
        val source = FakeConfigSource()
        val flows = SampleConfigFlows(resolver(source))
        val seen = mutableListOf<SampleMode>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { flows.mode.toList(seen) }

        source.activate(SampleConfigContribution.MODE to "on")

        assertEquals(listOf(SampleMode.OFF, SampleMode.ON), seen)
        job.cancel()
    }

    @Test
    fun `a test can substitute the interface with no machinery at all`() {
        val fake = object : SampleConfig {
            override val enabled: Boolean = false
            override val retryCount: Int = 99
            override val mode: SampleMode = SampleMode.ON
        }

        assertEquals(false, fake.enabled)
        assertEquals(99, fake.retryCount)
    }
}

package com.hopcape.odo.core.config

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ConfigFlowTest {

    @Test
    fun `a flow starts with the current value`() = runTest {
        val seen = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            resolver().booleanFlow(SampleConfigContribution.ENABLED).toList(seen)
        }

        assertEquals(listOf(true), seen)
        job.cancel()
    }

    @Test
    fun `a flow re-emits when a fetch activates a new value`() = runTest {
        val source = FakeConfigSource()
        val seen = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            resolver(source).booleanFlow(SampleConfigContribution.ENABLED).toList(seen)
        }

        source.activate(SampleConfigContribution.ENABLED to "false")

        assertEquals(listOf(true, false), seen)
        job.cancel()
    }

    @Test
    fun `a fetch that changes nothing this key cares about emits nothing`() = runTest {
        // One generation counter serves every key, so most fetches bump it without
        // changing any given value. distinctUntilChanged is what keeps that free.
        val source = FakeConfigSource()
        val seen = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            resolver(source).booleanFlow(SampleConfigContribution.ENABLED).toList(seen)
        }

        source.activate(SampleConfigContribution.RETRY_COUNT to "5")
        source.activate(SampleConfigContribution.RETRY_COUNT to "6")

        assertEquals(listOf(true), seen)
        job.cancel()
    }

    @Test
    fun `a flow re-emits when a QA override is written and cleared`() = runTest {
        val overrides = FakeOverrides()
        val seen = mutableListOf<Int>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            resolver(overrides = overrides).intFlow(SampleConfigContribution.RETRY_COUNT).toList(seen)
        }

        overrides.set(SampleConfigContribution.RETRY_COUNT, "9")
        overrides.clear(SampleConfigContribution.RETRY_COUNT)

        assertEquals(listOf(3, 9, 3), seen)
        job.cancel()
    }

    @Test
    fun `a flow works with no override store behind it`() = runTest {
        // combine() over an empty override stream must still emit. A release build is
        // exactly this case, so getting it wrong would mean no screen ever updates.
        val source = FakeConfigSource()
        val seen = mutableListOf<Int>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            resolver(source, overrides = null).intFlow(SampleConfigContribution.RETRY_COUNT).toList(seen)
        }

        source.activate(SampleConfigContribution.RETRY_COUNT to "8")

        assertEquals(listOf(3, 8), seen)
        job.cancel()
    }
}

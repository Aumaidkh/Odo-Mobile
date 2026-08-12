package com.hopcape.odo.core.common

import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RunCatchingCancellableTest {

    @Test
    fun success_wrapsTheResult() {
        assertEquals(42, runCatchingCancellable { 42 }.getOrNull())
    }

    @Test
    fun aRegularException_isCaught_notRethrown() {
        val result = runCatchingCancellable { throw IllegalStateException("boom") }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun cancellationException_isNotCaught_itPropagates() {
        assertFailsWith<CancellationException> {
            runCatchingCancellable { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun suspendVariant_success_wrapsTheResult() = runTest {
        assertEquals(42, runCatchingCancellableSuspend { 42 }.getOrNull())
    }

    @Test
    fun suspendVariant_aRegularException_isCaught_notRethrown() = runTest {
        val result = runCatchingCancellableSuspend { throw IllegalStateException("boom") }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun suspendVariant_cancellationException_isNotCaught_itPropagates() = runTest {
        assertFailsWith<CancellationException> {
            runCatchingCancellableSuspend { throw CancellationException("cancelled") }
        }
    }
}

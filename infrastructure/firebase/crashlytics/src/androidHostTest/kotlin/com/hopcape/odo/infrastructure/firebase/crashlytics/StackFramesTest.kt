package com.hopcape.odo.infrastructure.firebase.crashlytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Crashlytics groups by these frames, so they decide one issue per cause or one for all. */
class StackFramesTest {

    @Test
    fun restoreFrames_putsTheOriginOnTop() {
        val throwable = RuntimeException("boom")

        throwable.restoreFrames(
            """
            java.lang.IllegalStateException: boom
                at com.hopcape.odo.core.data.car.CarRepositoryImpl.save(CarRepositoryImpl.kt:84)
                at com.hopcape.odo.feature.garage.GarageViewModel.onSave(GarageViewModel.kt:120)
            """.trimIndent(),
        )

        val top = throwable.stackTrace.first()
        assertEquals("com.hopcape.odo.core.data.car.CarRepositoryImpl", top.className)
        assertEquals("save", top.methodName)
        assertEquals("CarRepositoryImpl.kt", top.fileName)
        assertEquals(84, top.lineNumber)
        assertEquals(2, throwable.stackTrace.size)
    }

    @Test
    fun restoreFrames_handlesAFrameWithNoLineNumber() {
        val throwable = RuntimeException("boom")

        throwable.restoreFrames("\tat java.lang.reflect.Method.invoke(Native Method)")

        val top = throwable.stackTrace.first()
        assertEquals("java.lang.reflect.Method", top.className)
        assertEquals("invoke", top.methodName)
        assertEquals(-1, top.lineNumber)
    }

    @Test
    fun restoreFrames_leavesTheRecordingStackWhenNothingParses() {
        val throwable = RuntimeException("boom")
        val original = throwable.stackTrace

        throwable.restoreFrames("java.lang.IllegalStateException: boom")

        assertTrue(original.contentEquals(throwable.stackTrace))
    }

    @Test
    fun restoreFrames_skipsLinesItCannotRead() {
        val throwable = RuntimeException("boom")

        throwable.restoreFrames(
            """
            at nonsense
            at com.hopcape.odo.Real.work(Real.kt:7)
            ... 12 more
            """.trimIndent(),
        )

        assertEquals(1, throwable.stackTrace.size)
        assertEquals("com.hopcape.odo.Real", throwable.stackTrace.first().className)
    }
}

package com.hopcape.odo.core.platform.sync

import com.hopcape.odo.core.sync.SyncReason
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class SyncJitterTest {

    /** A fixed seed, so a band that moves fails here rather than in production. */
    private fun jitter(seed: Int = 42) = SyncJitter(Random(seed))

    @Test
    fun `a manual refresh waits for nothing`() {
        assertEquals(Duration.ZERO, jitter().initialDelay(SyncReason.Manual))
    }

    /**
     * Signing in is the one whose delay would be most visible: the initial pull is what puts
     * the owner's data on the screen, and an empty app is what they see until it lands.
     */
    @Test
    fun `signing in waits for nothing`() {
        assertEquals(Duration.ZERO, jitter().initialDelay(SyncReason.SignIn))
    }

    @Test
    fun `a local write still waits out the debounce`() {
        repeat(200) { seed ->
            val delay = jitter(seed).initialDelay(SyncReason.LocalWrite)
            assertTrue(delay >= 5.seconds, "local write fired before the debounce: $delay")
            assertTrue(delay <= 15.seconds, "local write waited too long: $delay")
        }
    }

    /** The band is wider the more likely every install was triggered by the same event. */
    @Test
    fun `every background reason stays inside its band`() {
        val bands = mapOf(
            SyncReason.RemoteChange to 120.seconds,
            SyncReason.Reconnected to 60.seconds,
            SyncReason.AppForeground to 20.seconds,
        )
        bands.forEach { (reason, ceiling) ->
            repeat(200) { seed ->
                val delay = jitter(seed).initialDelay(reason)
                assertTrue(delay >= Duration.ZERO, "$reason drew a negative delay: $delay")
                assertTrue(delay <= ceiling, "$reason drew $delay, past its $ceiling band")
            }
        }
    }

    /**
     * The point of the whole class. A thousand installs reacting to one push must not land on
     * one instant — if they do, the spread is decorative.
     */
    @Test
    fun `a thousand installs reacting to one push do not land together`() {
        val delays = (1..1000).map { seed -> jitter(seed).initialDelay(SyncReason.RemoteChange) }
        val busiestSecond = delays.groupingBy { it.inWholeSeconds }.eachCount().values.max()

        assertTrue(busiestSecond < 40, "$busiestSecond of 1000 installs landed in one second")
        assertTrue(delays.distinct().size > 900, "only ${delays.distinct().size} distinct delays")
    }

    /**
     * WorkManager clamps anything under ten seconds, so a base below it would silently become
     * a different number and the spread would collapse at the bottom of the band.
     */
    @Test
    fun `the backoff base clears WorkManager's floor and stays inside its band`() {
        repeat(500) { seed ->
            val base = jitter(seed).backoffBase()
            assertTrue(base >= 20.seconds, "backoff base $base is under the floor")
            assertTrue(base <= 45.seconds, "backoff base $base is over the ceiling")
        }
    }

    /**
     * Retries are the half a fixed base gets wrong: the formula is deterministic, so two
     * installs that fail together come back in the same second, over and over, for as long
     * as the outage lasts.
     */
    @Test
    fun `two installs failing together climb different curves`() {
        val first = jitter(1).backoffBase()
        val second = jitter(2).backoffBase()
        assertTrue(first != second, "both installs drew $first")

        // Third attempt, where WorkManager has doubled twice.
        assertTrue(first * 4 != second * 4, "the curves converge by the third attempt")
    }

    @Test
    fun `the base is drawn per request, not once per install`() {
        val shared = jitter()
        val draws = (1..50).map { shared.backoffBase() }
        assertTrue(draws.distinct().size > 1, "every request drew the same base")
    }
}

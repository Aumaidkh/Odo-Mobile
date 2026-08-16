package com.hopcape.odo.core.domain.showcase

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #227's contract, verbatim: two due hooks show exactly one; dismissing one does not
 * immediately show the other; a seen hook is never due again. Plus the release that
 * writes nothing — a surface navigated away from mid-mark keeps its one showing.
 */
class ShowcaseArbiterTest {

    private val store = FakeSeenStore()
    private val arbiter = ShowcaseArbiter(store)

    @Test
    fun twoDueHooks_exactlyOneGranted() = runTest {
        assertTrue(arbiter.request(ShowcaseHookId.SCAN_BUTTON))
        assertFalse(arbiter.request(ShowcaseHookId.HEALTH_SCORE_BREAKDOWN))
        assertEquals(ShowcaseHookId.SCAN_BUTTON, arbiter.active.value)
    }

    @Test
    fun dismissing_doesNotPromoteTheOneThatWasDenied() = runTest {
        arbiter.request(ShowcaseHookId.SCAN_BUTTON)
        arbiter.request(ShowcaseHookId.HEALTH_SCORE_BREAKDOWN)

        arbiter.dismissed(ShowcaseHookId.SCAN_BUTTON)

        // No chaining: the grant is released, not handed to whoever asked second.
        assertNull(arbiter.active.value)
    }

    @Test
    fun aDeniedHook_canBeGrantedOnItsNextRequest() = runTest {
        arbiter.request(ShowcaseHookId.SCAN_BUTTON)
        assertFalse(arbiter.request(ShowcaseHookId.HEALTH_SCORE_BREAKDOWN))
        arbiter.dismissed(ShowcaseHookId.SCAN_BUTTON)

        // The next visit to that surface asks again, and now nothing stands in the way.
        assertTrue(arbiter.request(ShowcaseHookId.HEALTH_SCORE_BREAKDOWN))
    }

    @Test
    fun aSeenHook_isNeverGrantedAgain() = runTest {
        arbiter.request(ShowcaseHookId.SCAN_BUTTON)
        arbiter.dismissed(ShowcaseHookId.SCAN_BUTTON)

        assertFalse(arbiter.request(ShowcaseHookId.SCAN_BUTTON))
    }

    @Test
    fun actedOn_alsoWritesSeen() = runTest {
        arbiter.request(ShowcaseHookId.FAIRNESS_CHECK)
        arbiter.actedOn(ShowcaseHookId.FAIRNESS_CHECK)

        assertTrue(store.seen.contains(ShowcaseHookId.FAIRNESS_CHECK))
        assertFalse(arbiter.request(ShowcaseHookId.FAIRNESS_CHECK))
    }

    @Test
    fun surfaceLeft_releasesWithoutWritingSeen() = runTest {
        arbiter.request(ShowcaseHookId.SCAN_BUTTON)

        // The trip-logged redirect (or a tab switch) disposed the surface mid-mark.
        arbiter.surfaceLeft(ShowcaseHookId.SCAN_BUTTON)

        assertNull(arbiter.active.value)
        assertTrue(store.seen.isEmpty())
        // The hook kept its one showing for the next, calmer visit.
        assertTrue(arbiter.request(ShowcaseHookId.SCAN_BUTTON))
    }

    @Test
    fun surfaceLeft_ofANonActiveHook_doesNotClobberTheActiveGrant() = runTest {
        arbiter.request(ShowcaseHookId.SCAN_BUTTON)

        arbiter.surfaceLeft(ShowcaseHookId.HEALTH_SCORE_BREAKDOWN)

        assertEquals(ShowcaseHookId.SCAN_BUTTON, arbiter.active.value)
    }

    @Test
    fun aHookSeenInAnEarlierSession_isDeniedOnFirstRequest() = runTest {
        store.seen += ShowcaseHookId.DOCUMENT_REMINDERS

        assertFalse(arbiter.request(ShowcaseHookId.DOCUMENT_REMINDERS))
        assertNull(arbiter.active.value)
    }

    private class FakeSeenStore : ShowcaseSeenStore {
        val seen = mutableSetOf<ShowcaseHookId>()
        override suspend fun isSeen(hook: ShowcaseHookId): Boolean = hook in seen
        override suspend fun markSeen(hook: ShowcaseHookId) {
            seen += hook
        }

        override suspend fun clearAll() = seen.clear()
    }
}

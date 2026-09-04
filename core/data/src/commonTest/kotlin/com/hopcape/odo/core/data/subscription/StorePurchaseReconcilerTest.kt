package com.hopcape.odo.core.data.subscription

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.subscription.CompletedPurchase
import com.hopcape.odo.core.domain.subscription.OneTimeGrant
import com.hopcape.odo.core.domain.subscription.PurchaseGrants
import com.hopcape.odo.core.domain.subscription.OneTimePurchaser
import com.hopcape.odo.core.domain.subscription.OneTimeProducts
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Money already taken, credited exactly once.
 *
 * Both halves matter and both are invisible from any screen: crediting nothing means an
 * owner paid and got nothing, and crediting twice hands out checks nobody paid for.
 */
class StorePurchaseReconcilerTest {

    @Test
    fun `a purchase the device never saw is credited`() = runTest {
        val grants = RecordingGrants()
        val reconciler = reconciler(
            purchases = listOf(CompletedPurchase("txn-1", OneTimeProducts.BILL_CHECK_PACK)),
            grants = grants,
        )

        reconciler.claimOutstanding()

        assertEquals(listOf(OneTimeGrant.BILL_CHECK_PACK), grants.awarded)
    }

    /**
     * The store reports the same purchase on every launch. Without the ledger it would be
     * credited on every one of them.
     */
    @Test
    fun `the same purchase is never credited twice`() = runTest {
        val grants = RecordingGrants()
        val reconciler = reconciler(
            purchases = listOf(CompletedPurchase("txn-1", OneTimeProducts.BILL_CHECK_SINGLE)),
            grants = grants,
        )

        reconciler.claimOutstanding()
        reconciler.claimOutstanding()
        reconciler.claimOutstanding()

        assertEquals(1, grants.awarded.size)
    }

    /** Two packs are two transactions, and the product id alone could not tell them apart. */
    @Test
    fun `two purchases of the same pack are both credited`() = runTest {
        val grants = RecordingGrants()
        val reconciler = reconciler(
            purchases = listOf(
                CompletedPurchase("txn-1", OneTimeProducts.BILL_CHECK_PACK),
                CompletedPurchase("txn-2", OneTimeProducts.BILL_CHECK_PACK),
            ),
            grants = grants,
        )

        reconciler.claimOutstanding()

        assertEquals(2, grants.awarded.size)
    }

    /**
     * A product this build does not know is left unclaimed rather than claimed and dropped,
     * so a release that understands it can still honour the purchase.
     */
    @Test
    fun `a product this build does not know is left for one that does`() = runTest {
        val grants = RecordingGrants()
        val reconciler = reconciler(
            purchases = listOf(CompletedPurchase("txn-1", "odo_something_later")),
            grants = grants,
        )

        reconciler.claimOutstanding()

        assertTrue(grants.awarded.isEmpty())
        assertTrue(grants.claimed.isEmpty(), "claiming it would lose the purchase for good")
    }

    /** Nothing on screen is waiting for this, so an unreachable store is simply next time. */
    @Test
    fun `a store that cannot be reached credits nothing and does not throw`() = runTest {
        val grants = RecordingGrants()

        reconciler(purchases = null, grants = grants).claimOutstanding()

        assertTrue(grants.awarded.isEmpty())
    }

    @Test
    fun `a purchaser that throws credits nothing and does not throw`() = runTest {
        val grants = RecordingGrants()

        reconciler(purchases = null, grants = grants, throwing = true).claimOutstanding()

        assertTrue(grants.awarded.isEmpty())
    }

    /**
     * What the record being owner-scoped buys: a fresh install pulls the claim back, so the
     * store's report of the same purchase is recognised rather than honoured again. This used
     * to be the reinstall loop — a ₹49 check re-credited on every install.
     */
    @Test
    fun `a purchase honoured before a reinstall is not honoured again`() = runTest {
        val grants = RecordingGrants()
        val purchase = CompletedPurchase("txn-1", OneTimeProducts.BILL_CHECK_SINGLE)

        // The claim as it came back from the server, then the reconciler on the new install.
        grants.claim(purchase.transactionId, OneTimeGrant.BILL_CHECK_SINGLE)
        grants.awarded.clear()

        reconciler(purchases = listOf(purchase), grants = grants).claimOutstanding()

        assertTrue(grants.awarded.isEmpty())
    }

    /**
     * Two passes at once — the watcher's and a screen's. The screen spends what the claim
     * credited the moment this returns, so returning while another pass is still inside
     * `award` would have it spend a balance that arrives a moment later.
     */
    @Test
    fun `a claim returns only once the award it raced is finished`() = runTest {
        val grants = SlowGrants()
        val reconciler = reconciler(
            purchases = listOf(CompletedPurchase("txn-1", OneTimeProducts.RECORD_EXPORT)),
            grants = grants,
        )

        val watcher = launch { reconciler.claimOutstanding() }
        val screen = launch { reconciler.claimOutstanding() }
        listOf(watcher, screen).joinAll()

        assertEquals(1, grants.awarded, "the record still lets exactly one through")
        assertTrue(grants.finished, "both calls returned, so the award had run")
    }

    /* ------------------------------ Fixtures ------------------------------ */

    private fun reconciler(
        purchases: List<CompletedPurchase>?,
        grants: PurchaseGrants,
        throwing: Boolean = false,
    ) = StorePurchaseReconciler(
        purchaser = FakePurchaser(purchases, throwing),
        grants = grants,
        telemetry = DataTelemetry(NoopLogger, NoopTracer, NoopCrash),
    )

    private class FakePurchaser(
        private val purchases: List<CompletedPurchase>?,
        private val throwing: Boolean,
    ) : OneTimePurchaser {
        override suspend fun purchase(productId: String): Either<DomainError, Unit> =
            error("not called")

        override suspend fun pricesOf(productIds: List<String>): Either<DomainError, Map<String, String>> =
            error("not called")

        override suspend fun completedPurchases(): Either<DomainError, List<CompletedPurchase>> {
            if (throwing) error("store exploded")
            return purchases?.right() ?: DomainError.StoreUnavailable.left()
        }
    }

    /**
     * Honours each transaction once, the way real storage does — the unique index is what
     * makes the second call false, and the reconciler is built on that answer.
     */
    private class RecordingGrants : PurchaseGrants {
        val claimed = mutableSetOf<String>()
        val awarded = mutableListOf<OneTimeGrant>()

        override suspend fun claim(transactionId: String, grant: OneTimeGrant): Boolean {
            if (!claimed.add(transactionId)) return false
            awarded += grant
            return true
        }
    }

    /** Writes slowly, so a second pass has time to overtake it if nothing stops it. */
    private class SlowGrants : PurchaseGrants {
        private val claimed = mutableSetOf<String>()
        var awarded = 0
            private set
        var finished = false
            private set

        override suspend fun claim(transactionId: String, grant: OneTimeGrant): Boolean {
            if (!claimed.add(transactionId)) return false
            awarded++
            delay(50)
            finished = true
            return true
        }
    }

    private object NoopLogger : Logger {
        override fun log(
            level: LogLevel,
            tag: String,
            event: String,
            traceContext: TraceContext?,
            fields: Map<String, Any?>,
        ) = Unit

        override fun flush() = Unit
    }

    private class NoopSpan(
        override val spanId: String,
        override val traceId: String,
        override val parentSpanId: String?,
        override val name: String,
    ) : Span {
        override fun setAttribute(key: String, value: Any?): Span = this
    }

    private object NoopTracer : PerformanceTracer {
        override fun startSpan(name: String, traceId: String, parentSpanId: String?): Span =
            NoopSpan("span", traceId, parentSpanId, name)

        override fun endSpan(span: Span) = Unit
        override fun flush() = Unit
    }

    private object NoopCrash : CrashRecorder {
        override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) = Unit
        override fun leaveBreadcrumb(tag: String, message: String) = Unit
        override fun setCustomKey(key: String, value: Any?) = Unit
        override fun setUserId(userId: String?) = Unit
    }
}

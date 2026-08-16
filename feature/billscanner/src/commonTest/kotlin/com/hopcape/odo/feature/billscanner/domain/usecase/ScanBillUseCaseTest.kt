package com.hopcape.odo.feature.billscanner.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.scan.BillExtractor
import com.hopcape.odo.core.domain.scan.entitlement.ScanAllowance
import com.hopcape.odo.core.domain.scan.entitlement.ScanLimit
import com.hopcape.odo.core.domain.scan.entitlement.ScanUsage
import com.hopcape.odo.core.domain.scan.model.BillType
import com.hopcape.odo.core.domain.scan.model.ExtractedBill
import com.hopcape.odo.core.domain.scan.model.ExtractedLineItem
import com.hopcape.odo.core.domain.scan.model.ExtractionConfidence
import com.hopcape.odo.core.domain.scan.model.ScanId
import com.hopcape.odo.core.domain.scan.model.ScannedImage
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ScanBillUseCaseTest {

    private val clock = FixedClock(Instant.parse("2026-08-04T09:00:00Z"))
    private val ids = SequentialIds()
    private val carId = CarId("car-1")
    private val ownerId = OwnerId("owner-1")
    private val usage = RecordingScanUsage()

    /* ------------------------------ ScanBillUseCase ------------------------------ */

    @Test
    fun a_spent_quota_is_refused_before_the_extractor_is_ever_called() = runTest {
        var called = false
        val useCase = ScanBillUseCase(
            extractor = { _ -> called = true; readableBill().right() },
            allowance = { ScanLimit.UpTo(max = 3, used = 3) },
            usage = usage,
            ids = ids,
            clock = clock,
        )

        val result = useCase("scans/a.jpg")

        assertEquals(DomainError.ScanQuotaExhausted(3), result.leftOrNull())
        // The point of checking first: an owner out of scans should not wait through an
        // upload that was always going to be refused.
        assertTrue(!called, "the extractor must not run once the quota is spent")
    }

    @Test
    fun an_unlimited_plan_always_scans() = runTest {
        val useCase = ScanBillUseCase(
            extractor = { readableBill().right() },
            allowance = { ScanLimit.Unlimited },
            usage = usage,
            ids = ids,
            clock = clock,
        )
        assertTrue(useCase("scans/a.jpg").isRight())
    }

    @Test
    fun a_read_with_nothing_in_it_is_rejected_rather_than_reviewed() = runTest {
        // A review screen with every field blank asks the owner to confirm nothing.
        val useCase = ScanBillUseCase(
            extractor = { emptyBill().right() },
            allowance = { ScanLimit.UpTo(max = 3, used = 0) },
            usage = usage,
            ids = ids,
            clock = clock,
        )
        assertEquals(DomainError.ScanRejected, useCase("scans/a.jpg").leftOrNull())
    }

    @Test
    fun the_extractors_own_failure_travels_unchanged() = runTest {
        val useCase = ScanBillUseCase(
            extractor = { DomainError.ScanUnavailable.left() },
            allowance = { ScanLimit.UpTo(max = 3, used = 0) },
            usage = usage,
            ids = ids,
            clock = clock,
        )
        assertEquals(DomainError.ScanUnavailable, useCase("scans/a.jpg").leftOrNull())
    }

    @Test
    fun the_photo_key_and_capture_time_reach_the_extractor() = runTest {
        var seen: ScannedImage? = null
        val useCase = ScanBillUseCase(
            extractor = { image -> seen = image; readableBill().right() },
            allowance = { ScanLimit.Unlimited },
            usage = usage,
            ids = ids,
            clock = clock,
        )
        useCase("scans/abc.jpg")

        assertEquals("scans/abc.jpg", seen?.storageKey)
        assertEquals(clock.now(), seen?.capturedAt)
    }

    @Test
    fun a_usable_read_spends_one_scan() = runTest {
        val useCase = ScanBillUseCase(
            extractor = { readableBill().right() },
            allowance = { ScanLimit.UpTo(max = 3, used = 0) },
            usage = usage,
            ids = ids,
            clock = clock,
        )

        useCase("scans/a.jpg")

        assertEquals(1, usage.recorded)
    }

    @Test
    fun a_read_that_gave_nothing_back_spends_nothing() = runTest {
        // Both ways a scan can come to nothing: the extractor failing, and it returning a
        // bill with no fields. Neither costs the owner one of their three.
        ScanBillUseCase(
            extractor = { DomainError.ScanUnavailable.left() },
            allowance = { ScanLimit.UpTo(max = 3, used = 0) },
            usage = usage,
            ids = ids,
            clock = clock,
        )("scans/a.jpg")
        ScanBillUseCase(
            extractor = { emptyBill().right() },
            allowance = { ScanLimit.UpTo(max = 3, used = 0) },
            usage = usage,
            ids = ids,
            clock = clock,
        )("scans/b.jpg")

        assertEquals(0, usage.recorded)
    }


    private fun readableBill() = ExtractedBill(
        scanId = ScanId("scan-1"),
        billType = BillType.PRINTED_THERMAL,
        confidence = ExtractionConfidence.of(92).getOrNull()!!,
        lineItems = listOf(ExtractedLineItem("Oil change", Amount.of(280_000).getOrNull()!!)),
        total = Amount.of(280_000).getOrNull()!!,
    )

    private fun emptyBill() = ExtractedBill(
        scanId = ScanId("scan-1"),
        billType = BillType.UNKNOWN,
        confidence = ExtractionConfidence.NONE,
    )
}


/** Counts what was charged, so a test can assert nothing was. */
private class RecordingScanUsage : ScanUsage {
    var recorded = 0
        private set

    override suspend fun used(): Int = recorded

    override suspend fun recordScan() {
        recorded++
    }
}

private class SequentialIds : IdGenerator {
    private var next = 0
    override fun newId(): String = "id-${next++}"
}

private class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

private fun <A, B> Either<A, B>.leftOrNull(): A? = fold({ it }, { null })

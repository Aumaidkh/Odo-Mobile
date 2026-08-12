package com.hopcape.odo.feature.billscanner.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.cost.model.FuelFill
import com.hopcape.odo.core.domain.cost.repository.FuelFillRepository
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.payment.model.UpiPaymentResult
import com.hopcape.odo.core.domain.payment.model.UpiPaymentStatus
import com.hopcape.odo.core.domain.scan.BillExtractor
import com.hopcape.odo.core.domain.scan.entitlement.ScanAllowance
import com.hopcape.odo.core.domain.scan.entitlement.ScanLimit
import com.hopcape.odo.core.domain.scan.model.BillType
import com.hopcape.odo.core.domain.scan.model.ExtractedBill
import com.hopcape.odo.core.domain.scan.model.ExtractedLineItem
import com.hopcape.odo.core.domain.scan.model.ExtractionConfidence
import com.hopcape.odo.core.domain.scan.model.ScanId
import com.hopcape.odo.core.domain.scan.model.ScannedImage
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ScanAndLogUseCasesTest {

    private val clock = FixedClock(Instant.parse("2026-08-04T09:00:00Z"))
    private val ids = SequentialIds()
    private val carId = CarId("car-1")
    private val ownerId = OwnerId("owner-1")

    /* ------------------------------ ScanBillUseCase ------------------------------ */

    @Test
    fun a_spent_quota_is_refused_before_the_extractor_is_ever_called() = runTest {
        var called = false
        val useCase = ScanBillUseCase(
            extractor = { _ -> called = true; readableBill().right() },
            allowance = { ScanLimit.UpTo(max = 3, used = 3) },
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
            ids = ids,
            clock = clock,
        )
        useCase("scans/abc.jpg")

        assertEquals("scans/abc.jpg", seen?.storageKey)
        assertEquals(clock.now(), seen?.capturedAt)
    }

    /* ------------------------------ LogFuelFillUseCase ------------------------------ */

    @Test
    fun a_fill_is_written_only_after_a_confirmed_payment() = runTest {
        val fills = RecordingFuelFills()
        val useCase = LogFuelFillUseCase(fills = fills, ids = ids, clock = clock)

        val result = useCase(command(status = UpiPaymentStatus.Success), carId, ownerId)

        assertTrue(result.isRight())
        assertEquals(1, fills.added.size)
        assertEquals("REF9", fills.added.single().transactionRef)
    }

    @Test
    fun a_pending_payment_writes_nothing_and_says_so() = runTest {
        // The money may or may not have moved. A fill recorded here would be a fabricated
        // entry in a history the app promises is trustworthy.
        val fills = RecordingFuelFills()
        val useCase = LogFuelFillUseCase(fills = fills, ids = ids, clock = clock)

        val result = useCase(command(status = UpiPaymentStatus.Pending), carId, ownerId)

        assertEquals(listOf(DomainError.PaymentPending), result.leftOrNull()?.toList())
        assertTrue(fills.added.isEmpty())
    }

    @Test
    fun a_failed_payment_writes_nothing() = runTest {
        val fills = RecordingFuelFills()
        val useCase = LogFuelFillUseCase(fills = fills, ids = ids, clock = clock)

        val result = useCase(command(status = UpiPaymentStatus.Failed), carId, ownerId)

        assertEquals(listOf(DomainError.PaymentFailed), result.leftOrNull()?.toList())
        assertTrue(fills.added.isEmpty())
    }

    @Test
    fun a_confirmed_payment_with_no_odometer_still_cannot_be_logged() = runTest {
        // Odometer is mandatory on every entry Odo keeps — it is what the mileage is
        // measured from, and a fill without one is only a receipt.
        val fills = RecordingFuelFills()
        val useCase = LogFuelFillUseCase(fills = fills, ids = ids, clock = clock)

        val result = useCase(
            command(status = UpiPaymentStatus.Success).copy(odometerKm = null),
            carId,
            ownerId,
        )

        assertTrue(result.leftOrNull()?.contains(DomainError.MissingOdometer) == true)
        assertTrue(fills.added.isEmpty())
    }

    private fun command(status: UpiPaymentStatus) = LogFuelFillCommand(
        payment = UpiPaymentResult(status = status, transactionRef = "REF9"),
        amount = Amount.of(320_000).getOrNull()!!,
        odometerKm = 40_000,
        quantityMilli = 32_000,
        unit = FuelUnit.LITRE,
    )

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

private class RecordingFuelFills : FuelFillRepository {
    val added = mutableListOf<FuelFill>()

    override suspend fun add(fill: FuelFill): Either<DomainError, FuelFill> {
        added += fill
        return fill.right()
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

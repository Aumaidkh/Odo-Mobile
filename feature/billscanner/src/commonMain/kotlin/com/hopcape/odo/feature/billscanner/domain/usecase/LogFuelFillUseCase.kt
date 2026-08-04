package com.hopcape.odo.feature.billscanner.domain.usecase

import arrow.core.EitherNel
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.cost.model.FuelFill
import com.hopcape.odo.core.domain.cost.model.FuelFillId
import com.hopcape.odo.core.domain.cost.repository.FuelFillRepository
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.payment.model.PaymentMethod
import com.hopcape.odo.core.domain.payment.model.UpiPaymentResult
import com.hopcape.odo.core.domain.payment.model.UpiPaymentStatus
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Records a tank of fuel after the payment for it went through.
 *
 * The rule this exists to enforce is the first line of the body: **nothing is written unless
 * the bank confirmed the payment.** A UPI hand-off can come back pending, and a fill logged
 * against a payment that later fails is a record of something that did not happen — in an app
 * whose whole promise is a maintenance history someone can trust at resale, that is worse
 * than not logging it at all. Pending is reported as [DomainError.PaymentPending] so the
 * screen can tell the owner to check their bank app rather than silently doing nothing.
 *
 * The odometer comes from the confirm step, because a QR code carries no reading and the
 * fill is useless for measuring mileage without one.
 */
internal class LogFuelFillUseCase(
    private val fills: FuelFillRepository,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend operator fun invoke(
        command: LogFuelFillCommand,
        carId: CarId,
        ownerId: OwnerId,
    ): EitherNel<DomainError, FuelFill> = either {
        ensure(command.payment.isConfirmed) {
            nonEmptyListOf(
                when (command.payment.status) {
                    UpiPaymentStatus.Pending -> DomainError.PaymentPending
                    else -> DomainError.PaymentFailed
                },
            )
        }

        val today = clock.now().toLocalDateTime(timeZone).date
        val fill = FuelFill.create(
            id = FuelFillId.new(ids),
            carId = carId,
            ownerId = ownerId,
            // The day the money moved. A fill is always recorded as it happens here — the
            // flow starts at a pump — so there is no date for the owner to get wrong.
            filledOn = today,
            odometerKm = command.odometerKm,
            quantityMilli = command.quantityMilli,
            unit = command.unit,
            amountPaise = command.amount.paise,
            today = today,
            stationName = command.stationName,
            paidVia = PaymentMethod.UPI,
            transactionRef = command.payment.transactionRef ?: command.payment.transactionId,
        ).bind()

        fills.add(fill).mapLeft { nonEmptyListOf(it) }.bind()
    }
}

/**
 * A fill the owner confirmed at the pump.
 *
 * [amount] is what was actually paid, carried through from the payment rather than re-read
 * from the QR: the code often names no sum, and the figure that belongs on the record is the
 * one that left the owner's account.
 */
internal data class LogFuelFillCommand(
    val payment: UpiPaymentResult,
    val amount: Amount,
    val odometerKm: Int?,
    val quantityMilli: Long?,
    val unit: FuelUnit,
    val stationName: String? = null,
)

package com.hopcape.odo.core.domain.servicelog.model

import arrow.core.Either
import arrow.core.EitherNel
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import arrow.core.raise.zipOrAccumulate
import arrow.core.right
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.shared.Notes
import com.hopcape.odo.core.domain.shared.WorkshopName
import kotlinx.datetime.LocalDate

/**
 * The ServiceLog aggregate root — one maintenance/expense event for a car.
 *
 * Construct only via [create], which enforces every field invariant and accumulates
 * *all* validation failures (so an add/edit form can show them at once). The private
 * constructor guarantees an invalid entry can never exist.
 *
 * The cross-entity "odometer may never move backwards" rule is NOT enforced here —
 * it compares against the car's other readings and lives in the use case
 * (`AddServiceLogUseCase` / `UpdateServiceLogUseCase`), the ServiceLog analog of
 * Car's "one primary per owner" living in the repository.
 *
 * [categories] are the "what was done" tags; [lineItems] an optional priced breakdown;
 * [billPhotoRef] the manually-attached bill photo that makes the entry `Verified`
 * (see [verification]). [totalAmount] stays authoritative regardless of line items.
 */
class ServiceLogEntry private constructor(
    val id: ServiceLogId,
    val carId: CarId,
    val ownerId: OwnerId,
    val serviceDate: LocalDate,
    val odometer: Distance,
    val totalAmount: Amount,
    val workshopName: WorkshopName?,
    val notes: Notes?,
    val categories: Set<ServiceCategory>,
    val lineItems: List<ServiceLogLineItem>,
    val source: LogSource,
    val billId: BillId?,
    val billPhotoRef: String?,
) {
    companion object {
        /**
         * Validating factory — the single entry point for building an entry. Takes
         * raw, domain-meaningful inputs and accumulates every field failure.
         * [today] is injected (the domain owns no clock) so the future-date guard is
         * deterministic and testable.
         */
        fun create(
            id: ServiceLogId,
            carId: CarId,
            ownerId: OwnerId,
            serviceDate: LocalDate?,
            odometerKm: Int?,
            totalAmountPaise: Long?,
            today: LocalDate,
            workshopName: String? = null,
            notes: String? = null,
            categories: Set<ServiceCategory> = emptySet(),
            lineItems: List<ServiceLogLineItemDraft> = emptyList(),
            source: LogSource = LogSource.MANUAL,
            billId: BillId? = null,
            billPhotoRef: String? = null,
        ): EitherNel<DomainError, ServiceLogEntry> = either {
            zipOrAccumulate(
                { validateServiceDate(serviceDate, today).bind() },
                { Distance.of(odometerKm).bind() },
                { Amount.of(totalAmountPaise).bind() },
                { WorkshopName.of(workshopName).bind() },
                { Notes.of(notes).bind() },
                { lineItems.map { ServiceLogLineItem.of(it.label, it.category, it.amountPaise).bind() } },
            ) { validDate, validOdometer, validAmount, validWorkshop, validNotes, validLineItems ->
                ServiceLogEntry(
                    id = id,
                    carId = carId,
                    ownerId = ownerId,
                    serviceDate = validDate,
                    odometer = validOdometer,
                    totalAmount = validAmount,
                    workshopName = validWorkshop,
                    notes = validNotes,
                    categories = categories,
                    lineItems = validLineItems,
                    source = source,
                    billId = billId,
                    billPhotoRef = billPhotoRef,
                )
            }
        }

        /**
         * Rehydrate an entry from already-persisted, trusted data (the local DB,
         * which mirrors these invariants via CHECK constraints).
         *
         * Unlike [create], this does not accumulate errors: a value that fails to
         * reconstruct signals local data corruption — a programming/storage error,
         * not user input — and fails fast. Construction stays inside the domain, so
         * the value objects' private constructors remain private. [lineItems] arrive
         * already built (the data layer maps its own rows).
         */
        fun reconstitute(
            id: ServiceLogId,
            carId: CarId,
            ownerId: OwnerId,
            serviceDate: LocalDate,
            odometerKm: Int,
            totalAmountPaise: Long,
            workshopName: String?,
            notes: String?,
            source: LogSource,
            billId: BillId?,
            categories: Set<ServiceCategory> = emptySet(),
            lineItems: List<ServiceLogLineItem> = emptyList(),
            billPhotoRef: String? = null,
        ): ServiceLogEntry = ServiceLogEntry(
            id = id,
            carId = carId,
            ownerId = ownerId,
            serviceDate = serviceDate,
            odometer = Distance.of(odometerKm)
                .getOrElse { error("corrupt service_log.odometer=$odometerKm for ${id.value}") },
            totalAmount = Amount.of(totalAmountPaise)
                .getOrElse { error("corrupt service_log.amount=$totalAmountPaise for ${id.value}") },
            workshopName = WorkshopName.of(workshopName)
                .getOrElse { error("corrupt service_log.workshopName for ${id.value}") },
            notes = Notes.of(notes)
                .getOrElse { error("corrupt service_log.notes for ${id.value}") },
            categories = categories,
            lineItems = lineItems,
            source = source,
            billId = billId,
            billPhotoRef = billPhotoRef,
        )

        private fun validateServiceDate(
            date: LocalDate?,
            today: LocalDate,
        ): Either<DomainError, LocalDate> = when {
            date == null -> DomainError.MissingServiceDate.left()
            date > today -> DomainError.ServiceDateInFuture.left()
            else -> date.right()
        }
    }
}

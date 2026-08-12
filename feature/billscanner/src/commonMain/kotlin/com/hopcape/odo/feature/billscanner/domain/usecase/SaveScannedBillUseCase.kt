package com.hopcape.odo.feature.billscanner.domain.usecase

import arrow.core.EitherNel
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.analysis.ServiceCategoryGuesser
import com.hopcape.odo.core.domain.servicelog.model.BillId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogLineItemDraft
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Writes a reviewed scan into the service log.
 *
 * What arrives here is what the **owner confirmed**, not what the model read. That is why the
 * command carries plain fields rather than an `ExtractedBill`: by this point the values have
 * been through a review screen, and anything the extractor got wrong has been corrected. The
 * scan's own confidence has no say in what gets stored.
 *
 * The entry is written with [LogSource.SCANNED] and the photo's storage key as
 * `billPhotoRef`, which is what earns it the **Verified** badge — the trust model says a
 * badge means there is a bill behind the claim, and here there is.
 *
 * Line-item categories are guessed at this boundary, not by the extractor. The workshop's own
 * wording is what the owner recognises, so it is stored as written; the category is a
 * separate reading of it, used for the fairness pool, and a line the guesser does not know
 * travels uncategorised rather than being forced into the nearest match.
 */
internal class SaveScannedBillUseCase(
    private val logs: ServiceLogRepository,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend operator fun invoke(
        command: SaveScannedBillCommand,
        carId: CarId,
        ownerId: OwnerId,
    ): EitherNel<DomainError, ServiceLogEntry> = either {
        val entry = ServiceLogEntry.create(
            id = ServiceLogId(ids.newId()),
            carId = carId,
            ownerId = ownerId,
            serviceDate = command.serviceDate,
            odometerKm = command.odometerKm,
            totalAmountPaise = command.totalPaise,
            today = clock.now().toLocalDateTime(timeZone).date,
            workshopName = command.workshopName,
            categories = command.lineItems
                .mapNotNull { ServiceCategoryGuesser.of(it.label) }
                .toSet(),
            lineItems = command.lineItems.map {
                ServiceLogLineItemDraft(
                    label = it.label,
                    // A line the guesser does not recognise is filed as OTHER rather than
                    // forced into the nearest match. The fairness pool is keyed by category,
                    // so a wrong one would compare a wiper blade against a clutch job.
                    category = ServiceCategoryGuesser.of(it.label) ?: ServiceCategory.OTHER,
                    amountPaise = it.amountPaise,
                )
            },
            source = LogSource.SCANNED,
            billId = command.scanId?.let(::BillId),
            billPhotoRef = command.photoStorageKey,
        ).bind()

        logs.add(entry).mapLeft { nonEmptyListOf(it) }.bind()
    }
}

/** One reviewed line, as the owner left it. */
internal data class ReviewedLineItem(
    val label: String,
    val amountPaise: Long,
)

/**
 * The reviewed bill, ready to be written.
 *
 * [photoStorageKey] is not optional in practice — it is what makes the entry verified — but
 * it is nullable so the same use case can save a scan whose photo failed to store rather than
 * losing the reading the owner just checked.
 */
internal data class SaveScannedBillCommand(
    val serviceDate: LocalDate?,
    val odometerKm: Int?,
    val totalPaise: Long?,
    val workshopName: String?,
    val lineItems: List<ReviewedLineItem>,
    val photoStorageKey: String?,
    /** The scan this came from, kept so the entry can be traced back to its extraction. */
    val scanId: String? = null,
)

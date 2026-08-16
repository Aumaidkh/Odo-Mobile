package com.hopcape.odo.core.domain.record.analysis

import com.hopcape.odo.core.domain.activity.analysis.ActivityFeedBuilder
import com.hopcape.odo.core.domain.activity.model.ActivityEvent
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.model.DocumentValidity
import com.hopcape.odo.core.domain.document.model.latestOfType
import com.hopcape.odo.core.domain.health.model.HealthSnapshot
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.record.model.DocumentOnFile
import com.hopcape.odo.core.domain.record.model.Ownership
import com.hopcape.odo.core.domain.record.model.RecordRow
import com.hopcape.odo.core.domain.record.model.RecordStatus
import com.hopcape.odo.core.domain.record.model.ServiceRecord
import com.hopcape.odo.core.domain.servicelog.analysis.OdometerTimeline
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.shared.sum
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Builds the shareable record from everything the app holds about a car.
 *
 * Pure and deterministic, like [ActivityFeedBuilder] next to which it sits: no clock, no
 * storage, no formatting. The day it is produced on comes in as a parameter, because the
 * document is dated and every validity in it is relative to that day.
 *
 * The rules that are not obvious from the types:
 *
 * - **The rows come from [ActivityFeedBuilder]**, not from a second merge here. A printed
 *   record that ordered the same events differently from the Timeline tab would be a bug
 *   nobody could explain, so there is one merge and this reuses it.
 * - **Score changes are not printed.** They are the app talking about itself. A buyer reading
 *   a service history does not need a line saying the app's own number moved four points,
 *   and the score is already one of the four figures at the top.
 * - **A document row counts as verified**, because a document is a file — the owner uploaded
 *   the paper. **The opening milestone does not**: nobody proved the car was bought.
 * - **A licence is not printed among the papers.** It is the owner's document rather than the
 *   car's (see [DocumentType.LICENCE]), and a record handed to a buyer should not carry the
 *   seller's driving licence.
 * - **Odometer consistency is judged across the logged entries only**, which is exactly the
 *   claim the printed line makes.
 */
object ServiceRecordBuilder {

    /**
     * The papers a car's record prints, in the order it prints them. Everything the vault can
     * hold except a licence; a type with nothing filed is dropped rather than printed empty.
     */
    private val PRINTED_DOCUMENTS = listOf(
        DocumentType.INSURANCE,
        DocumentType.PUC,
        DocumentType.RC,
        DocumentType.LOAN,
        DocumentType.OTHER,
    )

    /**
     * @param car the car the record is about; `null` while it is still loading, which yields
     *   a record with no identity rather than a failure.
     * @param owner whose record it is, for the ownership block.
     * @param today the day the document is produced on — every "valid till" is read against it.
     * @param zone the owner's zone, used to place stored instants on a calendar day.
     */
    fun build(
        car: Car?,
        owner: OwnerProfile?,
        entries: List<ServiceLogEntry>,
        documents: List<Document>,
        scores: List<HealthSnapshot>,
        today: LocalDate,
        zone: TimeZone,
    ): ServiceRecord {
        // Fills are deliberately not passed in. The printed record is what a buyer reads at
        // resale, and it is an account of how the car was looked after — a year of refuelling
        // is a hundred rows that say nothing about that, and they would bury the services
        // that do. The feed on screen shows them; the document does not.
        val rows = ActivityFeedBuilder.build(car, entries, documents, scores, zone)
            .filterNot { it is ActivityEvent.ScoreChanged }
            .map { event -> RecordRow(event = event, status = event.status()) }

        return ServiceRecord(
            carName = car?.displayName.orEmpty(),
            modelName = car?.modelName.orEmpty(),
            modelYear = car?.year?.value,
            fuelType = car?.fuelType,
            registrationNumber = car?.registrationNumber?.value,
            odometer = car?.odometer,
            healthScore = scores.maxByOrNull { it.computedAt }?.score?.total,
            // The oldest line, not the day the car was added to Odo: history is logged
            // backwards, so a service from before the car was in the app still belongs to
            // the span this record covers.
            recordSince = rows.minOfOrNull { it.date },
            ownership = Ownership(
                ownerName = owner?.name?.value,
                ownedSince = car?.purchaseYear?.value,
                odometerConsistent = entries.isOdometerConsistent(),
            ),
            documents = documents.onFile(today),
            rows = rows,
            total = rows.mapNotNull { it.amount }.sum(),
            issuedOn = today,
        )
    }

    /**
     * Whether the entries' readings only ever count upwards.
     *
     * Vacuously true for a car with no entries: nothing has been logged, so nothing
     * contradicts anything. The renderer decides whether a claim about zero entries is worth
     * printing at all.
     */
    private fun List<ServiceLogEntry>.isOdometerConsistent(): Boolean =
        OdometerTimeline.anomalies(
            map { OdometerReading(logId = it.id, date = it.serviceDate, odometer = it.odometer) },
        ).isEmpty()

    /** The newest paper of each printed type, with how long it is good for on [today]. */
    private fun List<Document>.onFile(today: LocalDate): List<DocumentOnFile> =
        PRINTED_DOCUMENTS.mapNotNull { type ->
            latestOfType(type)?.let { document ->
                DocumentOnFile(
                    type = type,
                    validity = DocumentValidity.of(expiresOn = document.expiresOn, today = today),
                )
            }
        }

    /** Whether a printed line rests on a file, or on the owner's word. */
    private fun ActivityEvent.status(): RecordStatus = when (this) {
        is ActivityEvent.Service -> when (verification) {
            VerificationStatus.VERIFIED -> RecordStatus.VERIFIED
            VerificationStatus.SELF_REPORTED -> RecordStatus.SELF_REPORTED
        }
        // The owner uploaded the paper, so there is something to check the line against.
        is ActivityEvent.DocumentFiled -> RecordStatus.VERIFIED
        is ActivityEvent.CarAdded -> RecordStatus.SELF_REPORTED
        // Neither of these reaches a printed row — one is filtered out above, the other is
        // never built into the feed this reads. Kept exhaustive so adding an event kind is a
        // compile error here rather than a silently unbadged row.
        is ActivityEvent.ScoreChanged -> RecordStatus.SELF_REPORTED
        is ActivityEvent.FuelFilled -> RecordStatus.SELF_REPORTED
    }
}

package com.hopcape.odo.core.data.servicelog

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.BillId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.shared.Distance
import arrow.core.getOrElse
import kotlinx.datetime.LocalDate

/**
 * DB row → domain. The boundary where generated columns become a [ServiceLogEntry]; the
 * domain never sees a row type.
 *
 * Written as a positional function rather than an extension on a generated class because
 * `selectByCar` and `selectById` return two *different* generated types with identical
 * shapes (both add the gathered `categories` column). SQLDelight's mapper overload lets
 * both queries feed this one function, so there is a single place where a column becomes a
 * field — and adding a column breaks it once, not twice.
 *
 * The sync columns are accepted and ignored: they exist for the engine, and letting them
 * reach the domain is exactly what the layering forbids.
 */
@Suppress("LongParameterList", "UNUSED_PARAMETER")
internal fun serviceLogFromRow(
    id: String,
    carId: String,
    ownerId: String,
    serviceDate: String,
    odometerKm: Long,
    totalAmountPaise: Long,
    workshopName: String?,
    notes: String?,
    source: String,
    billId: String?,
    billPhotoPath: String?,
    fairnessSnapshot: String?,
    createdAt: String,
    updatedAt: String,
    deletedAt: String?,
    remoteVersion: String?,
    syncStatus: String,
    categories: String?,
): ServiceLogEntry = ServiceLogEntry.reconstitute(
    id = ServiceLogId(id),
    carId = CarId(carId),
    ownerId = OwnerId(ownerId),
    serviceDate = LocalDate.parse(serviceDate),
    odometerKm = odometerKm.toInt(),
    totalAmountPaise = totalAmountPaise,
    workshopName = workshopName,
    notes = notes,
    // An unrecognised source means the row was written by a newer build; read it as a
    // manual entry rather than crashing, since the only thing source decides is a label.
    source = LogSource.entries.firstOrNull { it.name == source } ?: LogSource.MANUAL,
    billId = billId?.let(::BillId),
    categories = categories.toCategories(),
    billPhotoRef = billPhotoPath,
    fairness = fairnessSnapshot.toFairnessSnapshot(),
)

/**
 * The gathered `GROUP_CONCAT` column back into a set. An unknown name is dropped rather
 * than failing the read — a tag written by a newer build must not make an entry unopenable.
 */
private fun String?.toCategories(): Set<ServiceCategory> =
    this?.split(',')
        ?.mapNotNull { name -> ServiceCategory.entries.firstOrNull { it.name == name.trim() } }
        ?.toSet()
        .orEmpty()

/**
 * One row of the odometer timeline. `logId` is null for the car's onboarding baseline,
 * which is what tells [com.hopcape.odo.core.domain.servicelog.analysis.OdometerTimeline]
 * that the reading belongs to no entry and can never be excluded as "itself".
 */
internal fun odometerReadingFromRow(
    logId: String?,
    readingDate: String,
    odometerKm: Long,
): OdometerReading = OdometerReading(
    logId = logId?.let(::ServiceLogId),
    date = LocalDate.parse(readingDate),
    odometer = Distance.of(odometerKm.toInt())
        .getOrElse { error("corrupt odometer reading km=$odometerKm for log=$logId") },
)

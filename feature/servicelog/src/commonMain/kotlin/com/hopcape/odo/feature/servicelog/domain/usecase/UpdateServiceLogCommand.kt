package com.hopcape.odo.feature.servicelog.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogLineItemDraft
import kotlinx.datetime.LocalDate

/**
 * Raw, unvalidated edit input for an existing service log. Carries the entry's [id]
 * plus its owning [carId]/[ownerId] context; the remaining fields are the editable
 * form values, re-validated by
 * [com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry.create].
 */
data class UpdateServiceLogCommand(
    val id: ServiceLogId,
    val carId: CarId,
    val ownerId: OwnerId,
    val serviceDate: LocalDate?,
    val odometerKm: Int?,
    val totalAmountPaise: Long? = null,
    val workshopName: String? = null,
    val notes: String? = null,
    val categories: Set<ServiceCategory> = emptySet(),
    val lineItems: List<ServiceLogLineItemDraft> = emptyList(),
    val billPhotoRef: String? = null,
)

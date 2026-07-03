package com.hopcape.odo.feature.servicelog.domain.usecase

import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogLineItemDraft
import kotlinx.datetime.LocalDate

/**
 * Raw, unvalidated add-service-log input from the presentation layer. Fields are
 * nullable; validation/normalization happens in
 * [com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry.create].
 *
 * `carId` and `ownerId` are context (a selected car + the signed-in owner), not form
 * input, so they are passed to the use case's `invoke` rather than living here —
 * mirroring `AddCarCommand` / `AddCarUseCase`. [categories] are the "what was done"
 * tags, [lineItems] an optional priced breakdown, [billPhotoRef] a manually-attached
 * bill photo (makes the entry Verified).
 */
data class AddServiceLogCommand(
    val serviceDate: LocalDate?,
    val odometerKm: Int?,
    val totalAmountPaise: Long? = null,
    val workshopName: String? = null,
    val notes: String? = null,
    val categories: Set<ServiceCategory> = emptySet(),
    val lineItems: List<ServiceLogLineItemDraft> = emptyList(),
    val billPhotoRef: String? = null,
)

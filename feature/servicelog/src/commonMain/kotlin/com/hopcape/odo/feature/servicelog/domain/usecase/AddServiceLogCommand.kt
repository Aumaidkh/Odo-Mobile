package com.hopcape.odo.feature.servicelog.domain.usecase

import kotlinx.datetime.LocalDate

/**
 * Raw, unvalidated add-service-log input from the presentation layer. Fields are
 * nullable; validation/normalization happens in
 * [com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry.create].
 *
 * `carId` and `ownerId` are context (a selected car + the signed-in owner), not form
 * input, so they are passed to the use case's `invoke` rather than living here —
 * mirroring `AddCarCommand` / `AddCarUseCase`.
 */
data class AddServiceLogCommand(
    val serviceDate: LocalDate?,
    val odometerKm: Int?,
    val totalAmountPaise: Long? = null,
    val workshopName: String? = null,
    val notes: String? = null,
)

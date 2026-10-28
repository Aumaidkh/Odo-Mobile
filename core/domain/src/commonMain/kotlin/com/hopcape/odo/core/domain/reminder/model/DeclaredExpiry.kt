package com.hopcape.odo.core.domain.reminder.model

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlinx.datetime.LocalDate

/** An expiry date the owner told Odo without filing the paper, such as insurance in setup. */
data class DeclaredExpiry(
    val id: String,
    val carId: CarId,
    val ownerId: OwnerId,
    val kind: ReminderKind,
    val dueOn: LocalDate,
)

package com.hopcape.odo.units

import arrow.core.getOrElse
import com.hopcape.odo.core.designsystem.component.OdoDistanceUnit
import com.hopcape.odo.core.designsystem.units.OdoDistanceFormat
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.DistanceUnit
import com.hopcape.odo.core.domain.shared.displayValue
import com.hopcape.odo.core.domain.shared.format
import com.hopcape.odo.core.domain.shared.of
import com.hopcape.odo.core.domain.shared.perDistanceUnit
import com.hopcape.odo.core.domain.shared.suffix

/**
 * The app's [OdoDistanceFormat], backed by the domain kernel.
 *
 * This is the seam between the two unit enums: the design system has its own so it can stay
 * free of domain types, and the domain owns the conversion and the digit grouping, which
 * are tested there. Nothing else in the app converts a distance.
 */
internal class DomainDistanceFormat(private val distanceUnit: DistanceUnit) : OdoDistanceFormat {

    override val unit: OdoDistanceUnit = when (distanceUnit) {
        DistanceUnit.KILOMETRE -> OdoDistanceUnit.KM
        DistanceUnit.MILE -> OdoDistanceUnit.MILES
    }

    override val suffix: String = distanceUnit.suffix()

    override fun display(km: Int): Int =
        Distance.of(km).fold({ km }, { it.displayValue(distanceUnit) })

    override fun store(displayed: Int, currentKm: Int?): Int {
        val current = currentKm?.let { Distance.of(it).getOrNull() }
        return Distance.of(displayed, distanceUnit, current).fold({ displayed }, { it.km })
    }

    override fun format(km: Int): String =
        Distance.of(km).fold({ "$km $suffix" }, { it.format(distanceUnit) })

    override fun ratePaise(perKmPaise: Long): Long =
        Amount.of(perKmPaise).getOrElse { Amount.ZERO }.perDistanceUnit(distanceUnit).paise
}

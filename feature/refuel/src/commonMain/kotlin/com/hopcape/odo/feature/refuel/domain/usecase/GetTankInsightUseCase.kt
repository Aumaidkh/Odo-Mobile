package com.hopcape.odo.feature.refuel.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.cost.analysis.TankMileage
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.cost.model.FuelFill
import com.hopcape.odo.core.domain.cost.repository.FuelFillRepository
import kotlinx.coroutines.flow.first

/**
 * What the tank just logged actually returned, and whether that is normal for this car.
 *
 * The one line on the success screen that gives the owner something back for logging. It is
 * also the first measured mileage the app has ever been able to show — every other figure
 * came from a table of typical numbers by fuel type.
 *
 * `null` whenever the pair of fills cannot support a figure, which is the usual case early
 * on: the first fill has nothing to measure from, and two fills a few kilometres apart
 * produce a number that swings on how full each tank was. The success screen simply omits
 * the line then rather than showing a hedged one.
 */
internal class GetTankInsightUseCase(
    private val fills: FuelFillRepository,
) {
    suspend operator fun invoke(carId: CarId): TankInsight? {
        val history = fills.observeForCar(carId).first()
        val mileage = TankMileage.forLatest(history) ?: return null
        val latest: FuelFill = history.first()

        // The average of every *earlier* pair, so the tank being reported is compared with
        // the car's past rather than with a figure it is itself part of.
        val average = TankMileage.average(history.drop(1))

        return TankInsight(
            distancePerUnit = mileage,
            unit = latest.unit,
            comparison = average?.let { compare(mileage, it) } ?: TankComparison.NoBaseline,
        )
    }

    /**
     * How this tank sits against the car's own average.
     *
     * The band is deliberately wide. Real mileage moves several percent between a week of
     * traffic and a clear run, and calling that a drop would make the line noise the owner
     * learns to ignore.
     */
    private fun compare(mileage: Double, average: Double): TankComparison {
        if (average <= 0) return TankComparison.NoBaseline
        val ratio = mileage / average
        return when {
            ratio >= 1 + SIGNIFICANT -> TankComparison.Better
            ratio <= 1 - SIGNIFICANT -> TankComparison.Worse
            else -> TankComparison.Typical
        }
    }

    private companion object {
        /** How far from the average a tank has to be before it is worth mentioning. */
        const val SIGNIFICANT = 0.08
    }
}

/** The measured mileage of the tank just logged. */
internal data class TankInsight(
    val distancePerUnit: Double,
    val unit: FuelUnit,
    val comparison: TankComparison,
)

/** How the tank compares with what this car usually returns. */
internal enum class TankComparison {
    Better,
    Typical,
    Worse,

    /** Not enough earlier fills to have an average worth comparing against. */
    NoBaseline,
}

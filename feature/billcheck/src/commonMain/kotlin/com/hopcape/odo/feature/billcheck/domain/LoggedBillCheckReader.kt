package com.hopcape.odo.feature.billcheck.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.benchmark.FairnessContributor
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.owner.CurrentCityProvider
import com.hopcape.odo.core.domain.owner.model.QuestionKeys
import com.hopcape.odo.core.domain.owner.repository.QuestionnaireRepository
import com.hopcape.odo.core.domain.scan.entitlement.ScanCharger
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.shared.WorkshopTier
import com.hopcape.odo.feature.billcheck.domain.usecase.BillLine
import com.hopcape.odo.feature.billcheck.domain.usecase.CheckBillPriceUseCase
import kotlinx.coroutines.flow.first

/**
 * Checks a bill the owner has already logged.
 *
 * The bill is a service-log entry, because that is where a scan lands: the entry carries the
 * lines as the workshop printed them, the date, the odometer and the total. Nothing here
 * re-reads a photo.
 *
 * **The check is charged only once it has said something**, and its prices go back to the
 * shared pool at the same moment — which is what makes the "How we know" sheet's promise that
 * a band tightens on its own true.
 *
 * **The charge, in full:** A read that could not name a
 * single line has cost the owner nothing and takes nothing — which is what the offers sheet
 * promises ("if a check fails the credit comes back"), delivered by never taking it rather
 * than by refunding.
 */
internal class LoggedBillCheckReader(
    private val entries: ServiceLogRepository,
    private val cars: CarRepository,
    private val activeCar: ActiveCarProvider,
    private val cities: CurrentCityProvider,
    private val questionnaire: QuestionnaireRepository,
    private val check: CheckBillPriceUseCase,
    private val charger: ScanCharger,
    private val contributor: FairnessContributor,
) : BillCheckReader {

    override suspend fun read(billId: String): Either<DomainError, BillCheck> {
        val entry = entries.observe(ServiceLogId(billId)).first()
            ?: return DomainError.ServiceLogNotFound.left()
        val carId = activeCar.activeCarId.value ?: return DomainError.CarNotFound.left()
        val car = cars.observe(carId).first() ?: return DomainError.CarNotFound.left()

        // Everything past this point degrades rather than fails. With no city there is no
        // band to ask for, and the check still has the owner's own record and the maker's
        // schedule — a thinner screen, and far better than none.
        val result = check(
            car = car,
            city = cities.currentCity(),
            workshop = workshopTier(),
            lines = entry.lineItems.mapNotNull { item ->
                item.label?.let { BillLine(label = it, amount = item.amount) }
            },
            billTotal = entry.totalAmount,
            billDate = entry.serviceDate,
            history = entries.observe(carId).first().filter { it.id != entry.id },
        )

        // Charged here rather than by the screen: what the check found is what decides
        // whether it was one, and only this layer knows.
        if (result.check.saidSomething) {
            charger.chargeOne()
            // After the answer, never before it. The owner is not waiting on this, and the
            // server refuses it outright unless they have agreed to share prices.
            contributor.contribute(result.observations)
        }

        return result.check.right()
    }

    /**
     * Where the owner said they get the car serviced.
     *
     * Their answer to the onboarding question, which is the labour rate every price
     * comparison is quoted at. Unanswered falls to the middle tier — the same choice the
     * question itself makes for "both / not sure", and the one that is least wrong when the
     * truth is unknown.
     */
    private suspend fun workshopTier(): WorkshopTier {
        val answer = questionnaire.answersFor(QuestionKeys.Workshop).getOrNull()?.firstOrNull()
        return WorkshopTier.entries.firstOrNull { it.name == answer?.value } ?: DEFAULT_TIER
    }

    private companion object {
        val DEFAULT_TIER = WorkshopTier.MULTI_BRAND
    }
}

/**
 * Whether the check found anything to say.
 *
 * A bill where every line came back unchecked is not a result — it is a read that produced
 * nothing, and the owner must not be charged for it.
 */
private val BillCheck.saidSomething: Boolean
    get() = flagged.isNotEmpty() || fine.isNotEmpty()

package com.hopcape.odo.feature.refuel.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.cost.analysis.Band
import com.hopcape.odo.core.domain.cost.analysis.UsualFillBand
import com.hopcape.odo.core.domain.cost.model.FieldOrigin
import com.hopcape.odo.core.domain.cost.model.FillEntrySource
import com.hopcape.odo.core.domain.cost.model.FuelFillDraft
import com.hopcape.odo.core.domain.cost.repository.FuelFillRepository
import com.hopcape.odo.core.domain.refuel.FuelMerchantClassifier
import com.hopcape.odo.core.domain.refuel.PaymentNotice
import com.hopcape.odo.core.domain.refuel.RefuelDetectionStore
import kotlinx.coroutines.flow.first

/**
 * Turns a payment the phone was told about into a draft fill, or decides it was not one.
 *
 * Every reason to say no is checked before any reason to say yes, and they are all cheap:
 * detection off, an app the owner did not allow, a merchant they already rejected, a
 * merchant that does not read as fuel, or a payment with no amount in it. What survives all
 * of that is a draft the confirm step can open on.
 *
 * The bias throughout is towards silence. A fill Odo missed costs the owner the ten seconds
 * of logging it; a payment wrongly turned into a fill puts a fabricated record in a history
 * whose whole value is that someone can trust it at resale.
 *
 * Returning a [Detection] rather than writing anything is deliberate. Whether it goes
 * straight to storage or to a notification is the caller's decision, because it depends on
 * the owner's confirm-before-logging setting, and this has no business reading that.
 */
internal class DetectFillFromNoticeUseCase(
    private val fills: FuelFillRepository,
    private val detection: RefuelDetectionStore,
    private val buildDraft: BuildFillDraftUseCase,
) {
    suspend operator fun invoke(carId: CarId, notice: PaymentNotice): Detection? {
        val settings = detection.settings()
        if (!settings.detectEnabled) return null

        val apps = detection.observeApps().first()
        // A package nobody has enabled is one the owner never agreed to have read. An empty
        // list means the app roster has not been built yet, which is also not consent.
        if (apps.none { it.packageName == notice.sourcePackage && it.enabled }) return null

        val amount = notice.amount ?: return null
        if (!FuelMerchantClassifier.isFuelMerchant(notice.merchant, detection.ignoredMerchantKeys())) {
            return null
        }

        val history = fills.observeForCar(carId).first()
        val band = UsualFillBand.of(history)

        val draft = buildDraft(
            carId = carId,
            partial = FuelFillDraft(
                source = FillEntrySource.DETECTED,
                amount = amount,
                amountOrigin = FieldOrigin.PAYMENT,
                stationName = notice.merchant,
            ),
            predictOdometer = settings.predictOdometer,
        )

        return Detection(
            draft = draft,
            // An amount far below the owner's usual tank turns the confirm step into a
            // question. It never blocks the draft: a small fill is still a fill, and the
            // owner is the one who knows which this was.
            unusuallySmall = band?.isUnusuallySmall(amount) == true,
            usualBand = band,
        )
    }
}

/**
 * A payment that reads as a fill, and what the confirm step should make of it.
 *
 * [usualBand] travels with it so the question can show its reasoning — "your usual fill is
 * ₹1,800–2,200" — rather than only doubting the owner.
 */
internal data class Detection(
    val draft: FuelFillDraft,
    val unusuallySmall: Boolean,
    val usualBand: Band?,
)

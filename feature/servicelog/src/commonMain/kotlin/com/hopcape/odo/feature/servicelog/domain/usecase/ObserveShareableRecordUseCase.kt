package com.hopcape.odo.feature.servicelog.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.repository.CarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The car's record as something shareable — what the "share verified record" sheet needs
 * before a buyer ever sees it: which car it is, and how much of its history is bill-backed.
 *
 * The counts come from the same feed the list reads, so the sheet can never claim a
 * different number of verified services than the screen behind it.
 */
internal class ObserveShareableRecordUseCase(
    private val observeFeed: ObserveServiceLogFeedUseCase,
    private val cars: CarRepository,
) {
    operator fun invoke(carId: CarId): Flow<ShareableRecord> =
        // The car port exposes only the primary car, which is the same car `ActiveCarProvider`
        // hands the route in an MVP garage of one — so the name and the counts agree today.
        // TODO(multi-car): when a garage can hold two, this must read the car by [carId]
        //  (a `CarRepository.observe(carId)` the port doesn't have yet). Sharing one car's
        //  record under another car's name is the failure mode that change exists to prevent.
        combine(observeFeed(carId), cars.observePrimaryCar()) { feed, car ->
            ShareableRecord(
                carName = car?.displayName,
                serviceCount = feed.summary.serviceCount,
                verifiedCount = feed.verifiedCount,
                // TODO(passport): the Resale Passport (a paid, server-issued shareable link)
                //  is Phase 2. Until it exists there is no link to hand out, and inventing
                //  one would publish a URL that answers nothing.
                passportUrl = null,
            )
        }
}

/**
 * A car's record, ready to leave the app. [passportUrl] is `null` until the Resale Passport
 * feature issues one — the sheet shows the summary and its share targets, never a link that
 * does not resolve.
 */
internal data class ShareableRecord(
    val carName: String?,
    val serviceCount: Int,
    val verifiedCount: Int,
    val passportUrl: String?,
)

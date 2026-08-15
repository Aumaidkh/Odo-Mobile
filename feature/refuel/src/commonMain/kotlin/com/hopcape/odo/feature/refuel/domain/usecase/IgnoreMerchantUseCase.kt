package com.hopcape.odo.feature.refuel.domain.usecase

import com.hopcape.odo.core.common.runCatchingCancellableSuspend
import com.hopcape.odo.core.domain.refuel.RefuelDetectionStore

/**
 * Remembers a merchant the owner said was not fuel.
 *
 * The only way detection improves. The classifier is a fixed table, so it cannot learn that
 * one particular forecourt shop shares its name with a pump — the owner telling it once is
 * what closes that gap, and it must hold for every later payment at the same merchant.
 *
 * A storage failure is swallowed. This runs as the owner dismisses a screen, and turning
 * "that wasn't fuel" into an error dialog would punish them for correcting Odo. The cost of
 * losing it is one repeated question.
 */
internal class IgnoreMerchantUseCase(
    private val store: RefuelDetectionStore,
) {
    suspend operator fun invoke(merchant: String) {
        if (merchant.isBlank()) return
        runCatchingCancellableSuspend { store.ignoreMerchant(merchant) }
    }
}

package com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase

import com.hopcape.odo.core.common.runCatchingCancellableSuspend
import com.hopcape.odo.core.domain.car.catalog.VehicleCatalog
import com.hopcape.odo.core.domain.shared.PublicAssetUrls

/** The logo URL for one make, or null when it has none or it cannot be read. */
internal class LoadMakeLogoUseCase(
    private val catalog: VehicleCatalog,
    private val assetUrls: PublicAssetUrls,
) {
    suspend operator fun invoke(make: String): String? {
        val paths = runCatchingCancellableSuspend { catalog.makeLogoPaths() }.getOrNull() ?: return null
        val path = paths.entries.firstOrNull { it.key.equals(make.trim(), ignoreCase = true) }?.value ?: return null
        return assetUrls.urlFor(path)
    }
}

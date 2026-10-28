package com.hopcape.odo.feature.onboarding.domain.usecase

import com.hopcape.odo.core.common.runCatchingCancellableSuspend
import com.hopcape.odo.core.domain.car.catalog.VehicleCatalog
import com.hopcape.odo.core.domain.shared.PublicAssetUrls

/** The makes Welcome offers: the most popular as tiles, and every make for "Other". */
internal class LoadMakeChoicesUseCase(
    private val catalog: VehicleCatalog,
    private val assetUrls: PublicAssetUrls = PublicAssetUrls { null },
) {
    suspend operator fun invoke(): MakeChoices = MakeChoices(
        tiles = catalog.popularMakes(TILE_COUNT),
        all = catalog.makes(),
        logos = logoUrls(),
    )

    /** Logos are decoration: if they cannot be read, the makes still load with monograms. */
    private suspend fun logoUrls(): Map<String, String> =
        runCatchingCancellableSuspend { catalog.makeLogoPaths() }
            .getOrDefault(emptyMap())
            .mapNotNull { (make, path) -> assetUrls.urlFor(path)?.let { make to it } }
            .toMap()

    companion object {
        /** Eight tiles plus "Other" fill a 3 x 3 grid. */
        const val TILE_COUNT = 8
    }
}

internal data class MakeChoices(
    val tiles: List<String>,
    val all: List<String>,
    /** Logo URL by make name; makes without one are absent. */
    val logos: Map<String, String> = emptyMap(),
)

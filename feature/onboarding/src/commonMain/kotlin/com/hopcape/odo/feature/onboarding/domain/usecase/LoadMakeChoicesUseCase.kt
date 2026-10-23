package com.hopcape.odo.feature.onboarding.domain.usecase

import com.hopcape.odo.core.domain.car.catalog.VehicleCatalog

/** The makes Welcome offers: the most popular as tiles, and every make for "Other". */
internal class LoadMakeChoicesUseCase(
    private val catalog: VehicleCatalog,
) {
    suspend operator fun invoke(): MakeChoices = MakeChoices(
        tiles = catalog.popularMakes(TILE_COUNT),
        all = catalog.makes(),
    )

    companion object {
        /** Eight tiles plus "Other" fill a 3 x 3 grid. */
        const val TILE_COUNT = 8
    }
}

internal data class MakeChoices(
    val tiles: List<String>,
    val all: List<String>,
)

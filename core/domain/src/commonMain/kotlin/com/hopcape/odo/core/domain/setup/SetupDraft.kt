package com.hopcape.odo.core.domain.setup

import com.hopcape.odo.core.domain.car.model.FuelType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * The answers given so far in an unfinished first-run setup, kept on the device so a
 * returning owner can carry on. [make] is always set: the draft starts with the Welcome tile.
 */
data class SetupDraft(
    val make: String,
    val modelName: String? = null,
    val modelVariant: String? = null,
    val year: Int? = null,
    val fuel: FuelType? = null,
    val updatedAt: Instant,
) {
    /** A draft older than [MAX_AGE] is treated as absent. */
    fun isStale(now: Instant): Boolean = now - updatedAt > MAX_AGE

    companion object {
        val MAX_AGE: Duration = 30.days
    }
}

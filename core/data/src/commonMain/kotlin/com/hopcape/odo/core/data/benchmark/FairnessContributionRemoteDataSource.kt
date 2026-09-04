package com.hopcape.odo.core.data.benchmark

/** The server side of the shared price pool. */
interface FairnessContributionRemoteDataSource {

    /** Add [observations] to the pool. Refused server-side unless the owner has consented. */
    suspend fun contribute(observations: List<FairnessContributionDto>)
}

/**
 * One row of `fairness_data_points`, keyed the way the client holds things.
 *
 * The category and the city are named, not their uuids: the client never carries the lookup
 * ids, and resolving them belongs to whoever owns the taxonomy.
 */
data class FairnessContributionDto(
    val categorySlug: String,
    val city: String,
    val amountPaise: Long,
    val segment: String?,
    val fuel: String,
    val workshopTier: String,
    val carMake: String?,
)

/** Keeps nothing. The pool needs a server, and a build without one contributes to nothing. */
internal class FakeFairnessContributionRemoteDataSource : FairnessContributionRemoteDataSource {
    override suspend fun contribute(observations: List<FairnessContributionDto>) = Unit
}

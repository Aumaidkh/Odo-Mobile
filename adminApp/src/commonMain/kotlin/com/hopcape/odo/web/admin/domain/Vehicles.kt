package com.hopcape.odo.web.admin.domain

import arrow.core.Either
import com.hopcape.odo.web.core.domain.WebError

/** A manufacturer. [id] is a text slug like `make-tata`, built by the database. */
data class VehicleMake(
    val id: String,
    val name: String,
    val displayOrder: Long,
)

/**
 * One pickable vehicle.
 *
 * [variant] is null for the trim-less row every model also gets, which is what an
 * owner who does not know their exact trim picks. Deleting that row and leaving
 * the trims is a real way to make a model unpickable by half its owners, so the
 * panel warns rather than treating it as just another line.
 */
data class VehicleModel(
    val id: String,
    val makeId: String,
    val name: String,
    val variant: String?,
    val displayOrder: Long,
) {
    val isBaseRow: Boolean get() = variant == null
}

/** A car an owner named that the catalog did not have. */
data class VehicleSubmission(
    val id: String,
    val make: String,
    val model: String,
    val variant: String?,
    val status: String,
    val createdAt: String,
)

/**
 * The vehicle catalog and its queue.
 *
 * Adding goes through one RPC rather than three inserts. A single entry can
 * create a make, a trim-less model row and a named trim, they belong in one
 * transaction, and their text ids have to come from the database's own slug
 * function — a client rebuilding that algorithm is a client that will eventually
 * disagree with the seed data.
 */
interface VehiclesRepository {

    suspend fun makes(): Either<WebError, List<VehicleMake>>

    suspend fun models(): Either<WebError, List<VehicleModel>>

    suspend fun submissions(): Either<WebError, List<VehicleSubmission>>

    /** Creates whatever of the make, the base model row and the trim is missing. */
    suspend fun add(make: String, model: String, variant: String?): Either<WebError, Unit>

    suspend fun renameMake(id: String, name: String): Either<WebError, Unit>

    suspend fun editModel(id: String, name: String, variant: String?): Either<WebError, Unit>

    /** Cascades to every model under it — the foreign key, not a choice made here. */
    suspend fun deleteMake(id: String): Either<WebError, Unit>

    suspend fun deleteModel(id: String): Either<WebError, Unit>

    /**
     * Accept or reject a submission.
     *
     * Unlike a city, accepting needs nothing filled in first: a vehicle
     * submission already carries the make and the model, and the trim is
     * genuinely optional. The promote trigger does the rest.
     */
    suspend fun decideSubmission(id: String, accepted: Boolean): Either<WebError, Unit>

    suspend fun deleteSubmission(id: String): Either<WebError, Unit>
}

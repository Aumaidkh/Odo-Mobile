package com.hopcape.odo.web.admin.domain

import arrow.core.Either
import com.hopcape.odo.web.core.domain.WebError

/**
 * One row of the shared cities lookup.
 *
 * [isActive] is a soft retire, not a delete. The client stores it and its picker
 * filters on it locally, which is the only way a retirement can reach a device
 * that has already synced the row — see `20260831140000_cities_admin.sql`.
 */
data class City(
    val id: String,
    val name: String,
    val state: String,
    /** 1, 2 or 3. A low-confidence label the app reads; 3 is the unreviewed default. */
    val tier: Int,
    val isActive: Boolean,
)

/**
 * A city an owner typed that the catalog did not have.
 *
 * [state] and [tier] are null on arrival because the app only ever asks for a
 * name. `cities.state` is NOT NULL, so a reviewer has to supply it before the row
 * can be promoted — which is why approving is an edit, not a single click.
 */
data class CitySubmission(
    val id: String,
    val name: String,
    val state: String?,
    val tier: Int?,
    val status: String,
    val createdAt: String,
)

/**
 * The cities catalog and its queue.
 *
 * Approving is deliberately not a method of its own. It is
 * [decideSubmission] with `accepted`, and the promotion into `cities` is done by
 * a database trigger — the same trigger that has always done it from the SQL
 * editor. Re-implementing that here would mean two places that know how a
 * submission becomes a city, and only one of them running inside the transaction.
 */
interface CitiesRepository {

    /** Every city, retired ones included — the panel is the one caller that needs them. */
    suspend fun cities(): Either<WebError, List<City>>

    suspend fun submissions(): Either<WebError, List<CitySubmission>>

    /** [WebError.Conflict] when the name is already taken, which is a unique index. */
    suspend fun add(name: String, state: String, tier: Int): Either<WebError, Unit>

    suspend fun edit(id: String, name: String, state: String, tier: Int): Either<WebError, Unit>

    /** Retire or restore. The row stays; `is_active` is what the app reads. */
    suspend fun setActive(id: String, active: Boolean): Either<WebError, Unit>

    /**
     * Accept or reject a submission.
     *
     * [state] and [tier] are written alongside the status because the promote
     * trigger reads them from the row it fires on. Accepting without a state
     * silently does nothing — the trigger's guard — so the caller must supply one.
     */
    suspend fun decideSubmission(
        id: String,
        accepted: Boolean,
        state: String?,
        tier: Int?,
    ): Either<WebError, Unit>

    /** For the nonsense a free-text field attracts. */
    suspend fun deleteSubmission(id: String): Either<WebError, Unit>
}

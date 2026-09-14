package com.hopcape.odo.infrastructure.supabase.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.car.lookup.RegisteredVehicle
import com.hopcape.odo.core.domain.car.lookup.VehicleRegistryLookup
import com.hopcape.odo.core.domain.car.lookup.VehicleSource
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.model.ModelYear
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.infrastructure.supabase.http.SupabaseRequestFailed
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException

/**
 * A plate answered from the owner's **own** cars on the server, for a device that no longer
 * holds them — the case after signing out or reinstalling.
 *
 * A plain table read rather than an RPC: `cars` is owner-scoped by RLS, so the server
 * already refuses anyone else's rows and nothing needs `security definer` to say so.
 *
 * [COLUMNS] is the security boundary and is deliberately not `*`. `owner_id`, `nickname`
 * and `current_odometer_km` are never requested, so they cannot be logged, cached or shown
 * by mistake. Changing that list is a privacy change, not a formatting one.
 */
internal class SupabaseVehicleRegistryLookup(
    private val postgrest: PostgrestClient,
    private val owners: CurrentOwnerProvider,
) : VehicleRegistryLookup {

    override suspend fun lookup(
        registrationNumber: RegistrationNumber,
    ): Either<DomainError, RegisteredVehicle> {
        val ownerId = owners.currentOwnerId()
        // Pre-sign-in rows carry a placeholder that is not a uuid, and the server's
        // owner_id is. Asking anyway is a round trip with a guaranteed empty answer.
        if (ownerId == OwnerId.LOCAL_PLACEHOLDER) return DomainError.RegistrationNotFound.left()

        val rows = try {
            postgrest.select(
                table = TABLE,
                serializer = VehicleAttributesRow.serializer(),
                columns = COLUMNS,
                filters = mapOf(
                    COLUMN_OWNER_ID to "eq.${ownerId.value}",
                    COLUMN_REGISTRATION to "eq.${registrationNumber.value}",
                    COLUMN_DELETED_AT to "is.null",
                ),
                order = "$COLUMN_UPDATED_AT.desc",
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (refused: SupabaseRequestFailed) {
            // The server answered, and it answered no. That is the service being unwell,
            // not a statement about this plate.
            return DomainError.LookupUnavailable.left()
        } catch (_: Throwable) {
            // Nothing came back at all — a timeout, a dropped connection, no DNS. Worth
            // retrying, unlike a miss, which is why it is a different error.
            return DomainError.LookupOffline.left()
        }

        return rows.firstOrNull()?.toRegisteredVehicle(VehicleSource.OWN_RECORD)?.right()
            ?: DomainError.RegistrationNotFound.left()
    }

    private companion object {
        const val TABLE = "cars"
        const val COLUMNS = "make,model,variant,year,fuel_type"
        const val COLUMN_OWNER_ID = "owner_id"
        const val COLUMN_REGISTRATION = "registration_number"
        const val COLUMN_DELETED_AT = "deleted_at"
        const val COLUMN_UPDATED_AT = "updated_at"
    }
}

/**
 * The five columns of a suggestion, and nothing else. `fuel_type` arrives as the Postgres
 * enum label, which is lowercase, unlike the Kotlin constant.
 */
@Serializable
internal data class VehicleAttributesRow(
    @SerialName("make") val make: String,
    @SerialName("model") val model: String,
    @SerialName("variant") val variant: String? = null,
    @SerialName("year") val year: Int,
    @SerialName("fuel_type") val fuelType: String,
) {
    /**
     * Null when the row carries a year or a fuel label this build's domain refuses.
     *
     * [source] is the caller's to state: the same five columns come back whether they were
     * read from this owner's rows or resolved across owners, and only the caller knows which
     * question it asked.
     */
    fun toRegisteredVehicle(source: VehicleSource): RegisteredVehicle? {
        val modelYear = ModelYear.of(year).getOrNull() ?: return null
        val fuel = FuelType.entries.firstOrNull { it.name.equals(fuelType, ignoreCase = true) }
            ?: return null
        return RegisteredVehicle(
            make = make,
            model = model,
            variant = variant,
            year = modelYear,
            fuelType = fuel,
            source = source,
        )
    }
}

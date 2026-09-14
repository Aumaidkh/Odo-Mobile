package com.hopcape.odo.infrastructure.supabase.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.car.lookup.RegisteredVehicle
import com.hopcape.odo.core.domain.car.lookup.VehicleRegistryLookup
import com.hopcape.odo.core.domain.car.lookup.VehicleSource
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.infrastructure.supabase.http.SupabaseRequestFailed
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

/**
 * A plate answered from **any** owner's car, through the `resolve_plate` RPC.
 *
 * The last tier, and the only one that can answer somebody setting up their first car. It
 * is also the only one that reaches across owners, so it is not a table read and cannot be:
 * `cars` is owner-scoped by RLS, and the function is `security definer` with a fixed
 * five-column result (migration `20260902180000_resolve_plate.sql`).
 *
 * The server refuses a partial plate with a 400 and refuses a caller past the daily ceiling
 * with a 429-shaped error. Both come back here as [DomainError.LookupUnavailable] rather
 * than "no record": neither is a statement about the plate, and telling an owner we have
 * never seen their car because they were rate-limited would be a lie.
 */
internal class SupabasePlateRegistryLookup(
    private val postgrest: PostgrestClient,
) : VehicleRegistryLookup {

    override suspend fun lookup(
        registrationNumber: RegistrationNumber,
    ): Either<DomainError, RegisteredVehicle> {
        val rows = try {
            postgrest.rpc(
                function = FUNCTION,
                params = JsonObject(mapOf(PARAM_PLATE to JsonPrimitive(registrationNumber.value))),
                serializer = ListSerializer(VehicleAttributesRow.serializer()),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (refused: SupabaseRequestFailed) {
            return DomainError.LookupUnavailable.left()
        } catch (_: Throwable) {
            return DomainError.LookupOffline.left()
        }

        return rows.firstOrNull()?.toRegisteredVehicle(VehicleSource.ANOTHER_RECORD)?.right()
            ?: DomainError.RegistrationNotFound.left()
    }

    private companion object {
        const val FUNCTION = "resolve_plate"
        const val PARAM_PLATE = "p_plate"
    }
}

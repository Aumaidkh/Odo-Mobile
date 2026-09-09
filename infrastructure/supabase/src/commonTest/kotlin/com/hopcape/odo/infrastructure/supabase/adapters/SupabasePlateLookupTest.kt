package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.domain.car.lookup.VehicleSource
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.infrastructure.supabase.MockResponse
import com.hopcape.odo.infrastructure.supabase.SupabaseTestHarness
import com.hopcape.odo.infrastructure.supabase.bodyText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * The two remote tiers of the plate lookup (issue #392), against a scripted PostgREST.
 *
 * The assertions that matter are about what leaves the device and what is asked for. The
 * column list is the privacy boundary on the owner-scoped tier, and the three failure
 * mappings are what decide whether the owner is offered a retry or sent to manual entry.
 */
class SupabasePlateLookupTest {

    // ─── the owner's own cars ───────────────────────────────────────────────────────

    @Test
    fun `the owner-scoped read asks for the five attributes and nothing else`() = runTest {
        // Widening this select is a privacy change, not a formatting one: owner_id,
        // nickname and the odometer are none of a suggestion's business.
        val harness = SupabaseTestHarness { MockResponse("[]") }

        ownLookup(harness).lookup(PLATE)

        val url = harness.onlyRequest().url.toString()
        assertContains(url, "select=make%2Cmodel%2Cvariant%2Cyear%2Cfuel_type")
        listOf("owner_id%2C", "nickname", "current_odometer_km", "%2Aid").forEach {
            assertFalse(url.contains(it), "$it must not be selected: $url")
        }
    }

    @Test
    fun `the owner-scoped read is filtered to the caller and to live rows`() = runTest {
        val harness = SupabaseTestHarness { MockResponse("[]") }

        ownLookup(harness).lookup(PLATE)

        val url = harness.onlyRequest().url.toString()
        assertContains(url, "owner_id=eq.owner-1")
        assertContains(url, "registration_number=eq.MH12AB1234")
        assertContains(url, "deleted_at=is.null")
        assertContains(url, "order=updated_at.desc")
    }

    @Test
    fun `a signed-out caller is answered without a request`() = runTest {
        // Pre-sign-in rows carry a placeholder that is not a uuid, and the server's
        // owner_id is. Asking would spend a round trip to be told nothing.
        val harness = SupabaseTestHarness { MockResponse("[]") }

        val result = ownLookup(harness, owner = OwnerId.LOCAL_PLACEHOLDER).lookup(PLATE)

        assertIs<DomainError.RegistrationNotFound>(result.leftOrNull())
        assertEquals(0, harness.requests.size)
    }

    @Test
    fun `a matching row becomes the owner's own record`() = runTest {
        val harness = SupabaseTestHarness { MockResponse(SWIFT_ROW) }

        val vehicle = ownLookup(harness).lookup(PLATE).getOrNull()

        assertEquals("Maruti Suzuki", vehicle?.make)
        assertEquals("Swift", vehicle?.model)
        assertEquals("VXI", vehicle?.variant)
        assertEquals(2020, vehicle?.year?.value)
        // The Postgres enum label is lowercase; the Kotlin constant is not.
        assertEquals(FuelType.PETROL, vehicle?.fuelType)
        assertEquals(VehicleSource.OWN_RECORD, vehicle?.source)
    }

    @Test
    fun `no rows is no record`() = runTest {
        val harness = SupabaseTestHarness { MockResponse("[]") }

        assertIs<DomainError.RegistrationNotFound>(ownLookup(harness).lookup(PLATE).leftOrNull())
    }

    @Test
    fun `a refused read is unavailable rather than no record`() = runTest {
        // The server answered, and it answered no. Telling the owner we have never seen
        // their car would be a lie that sends them to manual entry for good.
        val harness = SupabaseTestHarness { MockResponse("{}", HttpStatusCode.InternalServerError) }

        assertIs<DomainError.LookupUnavailable>(ownLookup(harness).lookup(PLATE).leftOrNull())
    }

    @Test
    fun `an unreadable body is offline rather than no record`() = runTest {
        // Nothing usable came back. Retrying is the right move, so it must not be the
        // permanent error.
        val harness = SupabaseTestHarness { MockResponse("not json") }

        assertIs<DomainError.LookupOffline>(ownLookup(harness).lookup(PLATE).leftOrNull())
    }

    // ─── another owner's car ────────────────────────────────────────────────────────

    @Test
    fun `the cross-owner tier calls the RPC with the normalized plate`() = runTest {
        // An RPC and not a table read, and it cannot be one: cars is owner-scoped by RLS,
        // so reaching across owners needs the security-definer function.
        val harness = SupabaseTestHarness { MockResponse("[]") }

        SupabasePlateRegistryLookup(harness.postgrest).lookup(PLATE)

        val request = harness.onlyRequest()
        assertContains(request.url.toString(), "/rest/v1/rpc/resolve_plate")
        assertContains(request.bodyText(), """"p_plate":"MH12AB1234"""")
    }

    @Test
    fun `a cross-owner match is labelled as somebody else's record`() = runTest {
        // The owner is told, because it is a guess about a car that may have changed
        // hands rather than something they wrote down themselves.
        val harness = SupabaseTestHarness { MockResponse(SWIFT_ROW) }

        val vehicle = SupabasePlateRegistryLookup(harness.postgrest).lookup(PLATE).getOrNull()

        assertEquals(VehicleSource.ANOTHER_RECORD, vehicle?.source)
    }

    @Test
    fun `a refused plate or a spent rate limit is unavailable rather than no record`() = runTest {
        // The server raises on a partial plate and on a caller past the daily ceiling.
        // Neither is a statement about the plate.
        listOf(HttpStatusCode.BadRequest, HttpStatusCode.TooManyRequests).forEach { status ->
            val harness = SupabaseTestHarness { MockResponse("{}", status) }

            val result = SupabasePlateRegistryLookup(harness.postgrest).lookup(PLATE)

            assertIs<DomainError.LookupUnavailable>(result.leftOrNull(), "$status")
        }
    }

    private fun ownLookup(
        harness: SupabaseTestHarness,
        owner: OwnerId = OwnerId("owner-1"),
    ) = SupabaseVehicleRegistryLookup(harness.postgrest, CurrentOwnerProvider { owner })

    private companion object {
        val PLATE = RegistrationNumber.of("mh 12 ab 1234")!!

        const val SWIFT_ROW =
            """[{"make":"Maruti Suzuki","model":"Swift","variant":"VXI","year":2020,"fuel_type":"petrol"}]"""
    }
}

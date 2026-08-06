package com.hopcape.odo.core.data.sync

import com.hopcape.odo.core.data.car.CarDto
import com.hopcape.odo.core.data.car.CarRemoteDataSource
import com.hopcape.odo.core.data.car.CarSyncTable
import com.hopcape.odo.core.data.owner.ProfileDto
import com.hopcape.odo.core.data.owner.ProfileRemoteDataSource
import com.hopcape.odo.core.data.owner.ProfileSyncTable
import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The offline placeholder owner must never reach the server.
 *
 * This is a regression test for a live failure. `owner_id` is a `uuid` column and the
 * placeholder is the string `local-owner`, so a pull filtered on it came back
 * `22P02 invalid input syntax for type uuid` — a 400 that stopped `profiles`, and with it
 * the whole run, because the engine halts at the first refusal. Nothing reached Supabase at
 * all, and the only symptom was one failed entity in the log.
 */
class PlaceholderOwnerTest {

    @Test
    fun theConstantIsNotAUuid_whichIsWhatMakesItSafe() {
        // If this ever became uuid-shaped, a pre-sign-in row could be pushed and land
        // against a real account. Failing the request is the safer half of the trade.
        assertEquals("local-owner", OwnerId.LOCAL_PLACEHOLDER.value)
        // 8-4-4-4-12 hex is what Postgres will accept as a uuid. This must not match it.
        val uuid = Regex("^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$")
        assertTrue(!uuid.matches(OwnerId.LOCAL_PLACEHOLDER.value))
    }

    @Test
    fun aPlaceholderOwnerIsTreatedAsNoOwner() {
        assertNull(OwnerId.LOCAL_PLACEHOLDER.value.orNullIfPlaceholder())
        assertNull(null.orNullIfPlaceholder())
        assertNull("".orNullIfPlaceholder())
        assertEquals("5b28c012-545f", "5b28c012-545f".orNullIfPlaceholder())
    }

    @Test
    fun theCarPullIsSkippedWhileSignedOut() = runTest {
        val (db, _) = inMemoryDatabase()
        val remote = RecordingCars()
        val table = CarSyncTable(db, remote, silentSyncTelemetry(), ownerId = { OwnerId.LOCAL_PLACEHOLDER.value })

        assertTrue(table.fetch(since = null).isEmpty())
        assertNull(remote.askedFor, "the placeholder must never be sent as a filter")
    }

    @Test
    fun theProfilePullIsSkippedWhileSignedOut() = runTest {
        val (db, _) = inMemoryDatabase()
        val remote = RecordingProfiles()
        val table = ProfileSyncTable(db, remote, ownerId = { OwnerId.LOCAL_PLACEHOLDER.value })

        assertTrue(table.fetch(since = null).isEmpty())
        assertNull(remote.askedFor)
    }

    @Test
    fun aRealOwnerIsPassedThrough() = runTest {
        val (db, _) = inMemoryDatabase()
        val remote = RecordingCars()
        val table = CarSyncTable(db, remote, silentSyncTelemetry(), ownerId = { "5b28c012-545f-447d-9a85-920084f68246" })

        table.fetch(since = null)

        assertEquals("5b28c012-545f-447d-9a85-920084f68246", remote.askedFor)
    }

    private class RecordingCars : CarRemoteDataSource {
        var askedFor: String? = null
        override suspend fun fetchSince(ownerId: String, since: Instant?): List<CarDto> {
            askedFor = ownerId
            return emptyList()
        }

        override suspend fun push(cars: List<CarDto>) = cars

        override suspend fun demoteOtherPrimaries(ownerId: String, keepCarId: String) = Unit
    }

    private class RecordingProfiles : ProfileRemoteDataSource {
        var askedFor: String? = null
        override suspend fun fetchSince(ownerId: String, since: Instant?): List<ProfileDto> {
            askedFor = ownerId
            return emptyList()
        }

        override suspend fun push(profiles: List<ProfileDto>) = profiles
    }
}

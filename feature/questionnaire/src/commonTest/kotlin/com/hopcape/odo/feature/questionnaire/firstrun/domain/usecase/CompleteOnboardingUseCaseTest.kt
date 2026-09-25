package com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Instant

class CompleteOnboardingUseCaseTest {

    private class FakeProfileRepository(
        stored: OwnerProfile? = null,
        private val saveResult: (OwnerProfile) -> Either<DomainError, OwnerProfile> = { it.right() },
    ) : OwnerProfileRepository {
        var saveCount = 0
        var current: OwnerProfile? = stored

        override suspend fun save(profile: OwnerProfile): Either<DomainError, OwnerProfile> {
            saveCount++
            return saveResult(profile).onRight { current = it }
        }

        override fun observe(): Flow<OwnerProfile?> = flowOf(current)
        override suspend fun recordPhone(ownerId: OwnerId, phone: PhoneNumber): Either<DomainError, Unit> =
            Unit.right()

        override suspend fun delete(): Either<DomainError, Unit> = Unit.right()
    }

    private val ownerId = OwnerId("owner-1")
    private val now = Instant.parse("2026-07-30T10:15:00Z")
    private val fixedClock = object : Clock {
        override fun now(): Instant = now
    }

    private fun useCase(profiles: OwnerProfileRepository) =
        CompleteOnboardingUseCase(profiles, CurrentOwnerProvider { ownerId }, fixedClock)

    private fun stored(completedAt: Instant? = null, id: OwnerId = ownerId) = OwnerProfile.reconstitute(
        id = id,
        name = "Rahul",
        onboardingCompletedAt = completedAt,
        city = "Pune",
        email = "rahul@example.com",
        avatarPath = "avatars/owner-1.jpg",
        sharesPricesAnonymously = false,
    )

    @Test
    fun aStoredProfile_isStampedAndKeepsEverythingElse() = runTest {
        val profiles = FakeProfileRepository(stored())

        val result = useCase(profiles)()

        assertEquals(true, result.getOrNull())
        val saved = profiles.current!!
        assertEquals(now, saved.onboardingCompletedAt)
        assertEquals("Rahul", saved.name?.value)
        assertEquals("Pune", saved.city)
        assertEquals("rahul@example.com", saved.email?.value)
        assertEquals("avatars/owner-1.jpg", saved.avatarPath)
        assertEquals(false, saved.sharesPricesAnonymously)
    }

    /** A bare row would be pushed over the account's real one at sign-in. */
    @Test
    fun noStoredProfile_createsNothing() = runTest {
        val profiles = FakeProfileRepository()

        val result = useCase(profiles)()

        assertEquals(false, result.getOrNull())
        assertEquals(0, profiles.saveCount)
    }

    @Test
    fun anotherOwnersProfile_isLeftAlone() = runTest {
        val profiles = FakeProfileRepository(stored(id = OwnerId("someone-else")))

        useCase(profiles)()

        assertEquals(0, profiles.saveCount)
    }

    @Test
    fun anAlreadyStampedProfile_keepsItsFirstStamp() = runTest {
        val first = Instant.parse("2026-01-01T00:00:00Z")
        val profiles = FakeProfileRepository(stored(completedAt = first))

        val result = useCase(profiles)()

        assertEquals(false, result.getOrNull())
        assertEquals(0, profiles.saveCount)
    }

    @Test
    fun aFailedSave_isReportedAndNotSwallowed() = runTest {
        val profiles = FakeProfileRepository(stored()) { DomainError.PersistenceFailure("disk full").left() }

        val result = useCase(profiles)()

        assertIs<DomainError.PersistenceFailure>(result.leftOrNull())
    }
}

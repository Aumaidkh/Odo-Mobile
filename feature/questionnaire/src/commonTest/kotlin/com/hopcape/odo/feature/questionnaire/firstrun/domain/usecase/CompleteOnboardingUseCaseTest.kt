package com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.model.OnboardingGoal
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class CompleteOnboardingUseCaseTest {

    private class FakeProfileRepository(
        private val result: (OwnerProfile) -> Either<DomainError, OwnerProfile> = { it.right() },
    ) : OwnerProfileRepository {
        var saveCount = 0
        var lastSaved: OwnerProfile? = null
        override suspend fun save(profile: OwnerProfile): Either<DomainError, OwnerProfile> {
            saveCount++
            lastSaved = profile
            return result(profile)
        }

        override fun observe(): Flow<OwnerProfile?> = flowOf(lastSaved)
        override suspend fun recordPhone(ownerId: OwnerId, phone: PhoneNumber): Either<DomainError, Unit> =
            Unit.right()

        override suspend fun delete(): Either<DomainError, Unit> = Unit.right()
    }

    private val ownerId = OwnerId("owner-1")
    private val now = Instant.parse("2026-07-30T10:15:00Z")
    private val owner = CurrentOwnerProvider { ownerId }
    private val fixedClock = object : Clock {
        override fun now(): Instant = now
    }

    private fun useCase(profiles: OwnerProfileRepository) =
        CompleteOnboardingUseCase(profiles = profiles, currentOwner = owner, clock = fixedClock)

    @Test
    fun validAnswers_persistACompletedProfile() = runTest {
        val profiles = FakeProfileRepository()

        val result = useCase(profiles)(
            CompleteOnboardingCommand(name = "  Rahul  ", goal = OnboardingGoal.TRACK_COSTS),
        )

        assertTrue(result.isRight(), "expected Right but was $result")
        val saved = profiles.lastSaved
        assertEquals(1, profiles.saveCount)
        assertEquals(ownerId, saved?.id)
        assertEquals("Rahul", saved?.name?.value)
        assertEquals(OnboardingGoal.TRACK_COSTS, saved?.goal)
        // Stamped before the save, so a stored profile never claims setup is unfinished.
        assertEquals(now, saved?.onboardingCompletedAt)
        assertTrue(saved?.hasCompletedOnboarding ?: false)
    }

    @Test
    fun missingAnswers_accumulateAndPersistNothing() = runTest {
        val profiles = FakeProfileRepository()

        val errors = useCase(profiles)(CompleteOnboardingCommand(name = " ", goal = null))
            .leftOrNull()
            ?.toList()
            .orEmpty()

        // Both failures at once — the step shows them together instead of one per submit.
        assertEquals(2, errors.size, "expected both failures but got $errors")
        assertTrue(DomainError.BlankOwnerName in errors)
        assertTrue(DomainError.MissingOnboardingGoal in errors)
        assertEquals(0, profiles.saveCount)
    }

    @Test
    fun missingGoalAlone_blocksCompletion() = runTest {
        // This rule is onboarding's, not the domain's: OwnerProfile itself accepts a
        // goal-less profile, because the server creates one at signup.
        val profiles = FakeProfileRepository()

        val errors = useCase(profiles)(CompleteOnboardingCommand("Rahul", null))
            .leftOrNull()
            ?.toList()
            .orEmpty()

        assertEquals(listOf(DomainError.MissingOnboardingGoal), errors)
        assertEquals(0, profiles.saveCount)
    }

    @Test
    fun invalidName_blocksCompletion() = runTest {
        val profiles = FakeProfileRepository()

        val errors = useCase(profiles)(CompleteOnboardingCommand("R", OnboardingGoal.SELL_SOON))
            .leftOrNull()
            ?.toList()
            .orEmpty()

        assertIs<DomainError.OwnerNameTooShort>(errors.single())
        assertEquals(0, profiles.saveCount)
    }

    @Test
    fun persistenceFailure_isReportedAndNotSwallowed() = runTest {
        val profiles = FakeProfileRepository { DomainError.PersistenceFailure("disk full").left() }

        val result = useCase(profiles)(
            CompleteOnboardingCommand("Rahul", OnboardingGoal.NEVER_MISS_RENEWAL),
        )

        assertIs<DomainError.PersistenceFailure>(result.leftOrNull()?.single())
        assertNull(result.getOrNull())
    }
}

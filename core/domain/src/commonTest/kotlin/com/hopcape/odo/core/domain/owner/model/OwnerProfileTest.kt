package com.hopcape.odo.core.domain.owner.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

class OwnerProfileTest {

    private val ownerId = OwnerId("owner-1")
    private val completedAt = Instant.parse("2026-07-30T10:15:00Z")

    private fun name(raw: String) = OwnerName.of(raw).getOrNull()!!

    @Test
    fun new_holdsBothAnswersAndIsNotYetComplete() {
        val profile = OwnerProfile.new(
            id = ownerId,
            name = name("Rahul"),
            goal = OnboardingGoal.TRACK_COSTS,
        )

        assertEquals("Rahul", profile.name?.value)
        assertEquals(OnboardingGoal.TRACK_COSTS, profile.goal)
        assertFalse(profile.hasCompletedOnboarding)
    }

    @Test
    fun completeOnboarding_stampsTheTimestamp() {
        val profile = OwnerProfile.new(ownerId, name("Rahul"), OnboardingGoal.SELL_SOON)

        val completed = profile.completeOnboarding(completedAt)

        assertTrue(completed.hasCompletedOnboarding)
        assertEquals(completedAt, completed.onboardingCompletedAt)
        // The original is untouched — the aggregate returns a new value, never mutates.
        assertFalse(profile.hasCompletedOnboarding)
    }

    @Test
    fun completeOnboarding_twice_keepsTheFirstTimestamp() {
        // Re-running the last step must not rewrite when the owner actually onboarded.
        val completed = OwnerProfile.new(ownerId, name("Rahul"), OnboardingGoal.SELL_SOON)
            .completeOnboarding(completedAt)

        val again = completed.completeOnboarding(Instant.parse("2026-08-01T09:00:00Z"))

        assertSame(completed, again)
        assertEquals(completedAt, again.onboardingCompletedAt)
    }

    @Test
    fun completeOnboarding_keepsTheAnswers() {
        val completed = OwnerProfile.new(ownerId, name("Rahul"), OnboardingGoal.SELL_SOON)
            .completeOnboarding(completedAt)

        assertEquals("Rahul", completed.name?.value)
        assertEquals(OnboardingGoal.SELL_SOON, completed.goal)
        assertEquals(ownerId, completed.id)
    }

    @Test
    fun reconstitute_acceptsTheNullsASignupCreatedRowHas() {
        // The server creates the row by trigger at signup, before onboarding asks
        // anything. Refusing to load it would make the app unable to read its own DB.
        val profile = OwnerProfile.reconstitute(
            id = ownerId,
            name = null,
            goal = null,
            onboardingCompletedAt = null,
        )

        assertNull(profile.name)
        assertNull(profile.goal)
        assertFalse(profile.hasCompletedOnboarding)
    }

    @Test
    fun reconstitute_trustsStoredValues() {
        val profile = OwnerProfile.reconstitute(
            id = ownerId,
            name = "Rahul",
            goal = OnboardingGoal.NEVER_MISS_RENEWAL,
            onboardingCompletedAt = completedAt,
        )

        assertEquals("Rahul", profile.name?.value)
        assertEquals(OnboardingGoal.NEVER_MISS_RENEWAL, profile.goal)
        assertTrue(profile.hasCompletedOnboarding)
    }
}

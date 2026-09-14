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
        )

        assertEquals("Rahul", profile.name?.value)
        assertFalse(profile.hasCompletedOnboarding)
    }

    @Test
    fun completeOnboarding_stampsTheTimestamp() {
        val profile = OwnerProfile.new(ownerId, name("Rahul"))

        val completed = profile.completeOnboarding(completedAt)

        assertTrue(completed.hasCompletedOnboarding)
        assertEquals(completedAt, completed.onboardingCompletedAt)
        // The original is untouched — the aggregate returns a new value, never mutates.
        assertFalse(profile.hasCompletedOnboarding)
    }

    @Test
    fun completeOnboarding_twice_keepsTheFirstTimestamp() {
        // Re-running the last step must not rewrite when the owner actually onboarded.
        val completed = OwnerProfile.new(ownerId, name("Rahul"))
            .completeOnboarding(completedAt)

        val again = completed.completeOnboarding(Instant.parse("2026-08-01T09:00:00Z"))

        assertSame(completed, again)
        assertEquals(completedAt, again.onboardingCompletedAt)
    }

    @Test
    fun completeOnboarding_keepsTheAnswers() {
        val completed = OwnerProfile.new(ownerId, name("Rahul"))
            .completeOnboarding(completedAt)

        assertEquals("Rahul", completed.name?.value)
        assertEquals(ownerId, completed.id)
    }

    @Test
    fun reconstitute_acceptsTheNullsASignupCreatedRowHas() {
        // The server creates the row by trigger at signup, before onboarding asks
        // anything. Refusing to load it would make the app unable to read its own DB.
        val profile = OwnerProfile.reconstitute(
            id = ownerId,
            name = null,
            onboardingCompletedAt = null,
        )

        assertNull(profile.name)
        assertFalse(profile.hasCompletedOnboarding)
    }

    @Test
    fun reconstitute_trustsStoredValues() {
        val profile = OwnerProfile.reconstitute(
            id = ownerId,
            name = "Rahul",
            onboardingCompletedAt = completedAt,
        )

        assertEquals("Rahul", profile.name?.value)
        assertTrue(profile.hasCompletedOnboarding)
    }

    @Test
    fun priceSharing_startsOnForANewProfile() {
        val profile = OwnerProfile.new(ownerId, name("Rahul"))

        assertTrue(profile.sharesPricesAnonymously)
    }

    @Test
    fun withPriceSharing_turnsItOffAndLeavesEverythingElseAlone() {
        val profile = OwnerProfile.new(ownerId, name("Rahul"))
            .withCity("Pune")
            .completeOnboarding(completedAt)

        val optedOut = profile.withPriceSharing(false)

        assertFalse(optedOut.sharesPricesAnonymously)
        assertEquals("Rahul", optedOut.name?.value)
        assertEquals("Pune", optedOut.city)
        assertTrue(optedOut.hasCompletedOnboarding)
    }

    @Test
    fun priceSharing_survivesEveryOtherEdit() {
        // Each `with*` rebuilds the whole aggregate positionally, so a new field is easy to
        // drop from one of them. An opted-out owner must not be opted back in by renaming.
        val optedOut = OwnerProfile.new(ownerId, name("Rahul"))
            .withPriceSharing(false)

        assertFalse(optedOut.withName(name("Rahul Sharma")).sharesPricesAnonymously)
        assertFalse(optedOut.withCity("Pune").sharesPricesAnonymously)
        assertFalse(optedOut.withAvatar("avatar.jpg").sharesPricesAnonymously)
        assertFalse(optedOut.withEmail(null).sharesPricesAnonymously)
        assertFalse(optedOut.completeOnboarding(completedAt).sharesPricesAnonymously)
    }

    @Test
    fun reconstitute_defaultsPriceSharingOnForRowsWrittenBeforeTheColumnExisted() {
        val profile = OwnerProfile.reconstitute(
            id = ownerId,
            name = "Rahul",
            onboardingCompletedAt = null,
        )

        assertTrue(profile.sharesPricesAnonymously)
    }
}

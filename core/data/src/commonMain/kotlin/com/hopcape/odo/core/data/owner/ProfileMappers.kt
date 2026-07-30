package com.hopcape.odo.core.data.owner

import com.hopcape.odo.core.data.db.Profiles
import com.hopcape.odo.core.domain.owner.model.OnboardingGoal
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import kotlin.time.Instant

/**
 * DB row → domain. The boundary where a generated [Profiles] row becomes an
 * [OwnerProfile]; domain never sees the row type. Rehydrates via
 * [OwnerProfile.reconstitute], which tolerates the nulls a signup-created row has.
 */
internal fun Profiles.toDomain(): OwnerProfile = OwnerProfile.reconstitute(
    id = OwnerId(id),
    name = full_name,
    // An unrecognized goal means the column was written by a newer build (or is
    // corrupt); read as "not answered" rather than crashing, since the goal only
    // chooses a landing surface and the profile is perfectly usable without it.
    goal = onboarding_goal?.let { stored -> OnboardingGoal.entries.firstOrNull { it.name == stored } },
    onboardingCompletedAt = onboarding_completed_at?.let(Instant::parse),
)

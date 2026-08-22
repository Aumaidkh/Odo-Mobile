package com.hopcape.odo.infrastructure.database.owner

import com.hopcape.odo.infrastructure.database.db.Profiles
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
    city = city,
    email = email,
    avatarPath = avatar_path,
    sharesPricesAnonymously = shares_prices != 0L,
    phone = phone,
)

/** SQLite has no boolean type; the column is a 0/1 integer. */
internal fun Boolean.toDbLong(): Long = if (this) 1L else 0L

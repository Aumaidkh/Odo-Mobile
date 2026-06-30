package com.hopcape.odo.core.domain.owner.model

/**
 * The user's primary reason for using Odo, captured during onboarding and used
 * to route them to the right starting surface (PRD §5.1).
 *
 * Belongs to the Owner context — the DB stores it on `profiles`, not `cars`.
 * Mirrors the DB `onboarding_goal` enum.
 */
enum class OnboardingGoal { SELL_SOON, TRACK_COSTS, NEVER_MISS_RENEWAL }

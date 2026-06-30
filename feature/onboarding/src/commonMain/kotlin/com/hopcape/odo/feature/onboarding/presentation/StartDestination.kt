package com.hopcape.odo.feature.onboarding.presentation

import com.hopcape.odo.core.domain.owner.model.OnboardingGoal

/**
 * Where onboarding should drop the user once their car is created — the routing
 * decision expressed as **data**, not a navigation action. The ViewModel emits
 * this (via [OnboardingEffect.NavigateToStart]); the nav host in `:app` maps it to
 * an actual destination, keeping presentation free of nav/Compose types.
 */
enum class StartDestination {
    DASHBOARD,
    RESALE_PASSPORT,
    DOCUMENT_VAULT,
}

/** Goal → starting surface mapping (PRD §5.1). */
fun OnboardingGoal.toStartDestination(): StartDestination = when (this) {
    OnboardingGoal.SELL_SOON -> StartDestination.RESALE_PASSPORT
    OnboardingGoal.TRACK_COSTS -> StartDestination.DASHBOARD
    OnboardingGoal.NEVER_MISS_RENEWAL -> StartDestination.DOCUMENT_VAULT
}

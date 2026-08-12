package com.hopcape.odo.feature.onboarding.presentation.state

import com.hopcape.odo.core.domain.owner.model.OnboardingGoal

/**
 * Where onboarding should drop the owner once setup is done — the routing decision
 * expressed as **data**, not a navigation action.
 *
 * It lives in presentation because deciding *which surface answers this owner's goal* is a
 * presentation decision; turning it into an actual `NavKey` is not, and stays behind
 * `StartDestination.toOdoDestination()` in the navigation package. That split is what lets
 * the ViewModel emit this without ever importing a nav or Compose type.
 */
internal enum class StartDestination {
    DASHBOARD,
    RESALE_PASSPORT,
    DOCUMENT_VAULT,
}

/** Goal → starting surface mapping (PRD §5.1). */
internal fun OnboardingGoal.toStartDestination(): StartDestination = when (this) {
    OnboardingGoal.SELL_SOON -> StartDestination.RESALE_PASSPORT
    OnboardingGoal.TRACK_COSTS -> StartDestination.DASHBOARD
    OnboardingGoal.NEVER_MISS_RENEWAL -> StartDestination.DOCUMENT_VAULT
}

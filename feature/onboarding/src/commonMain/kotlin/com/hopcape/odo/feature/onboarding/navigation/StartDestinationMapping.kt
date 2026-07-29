package com.hopcape.odo.feature.onboarding.navigation

import com.hopcape.odo.core.domain.owner.model.OnboardingGoal
import com.hopcape.odo.core.navigation.OdoDestination

/**
 * Where onboarding should drop the user once their car is created — the routing
 * decision expressed as **data**, not a navigation action. Presentation emits this
 * (it stays free of nav/Compose types); [toOdoDestination] is the single seam that
 * turns it into an actual navigation key.
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

/**
 * The Dashboard / Resale-Passport / Document-Vault surfaces don't exist yet
 * (later features / Phase 2), so all three currently land on [OdoDestination.Home].
 * Point each at its real destination here as those features ship — callers don't change.
 */
internal fun StartDestination.toOdoDestination(): OdoDestination = when (this) {
    StartDestination.DASHBOARD -> OdoDestination.Home
    StartDestination.RESALE_PASSPORT -> OdoDestination.Home // TODO: ResalePassport destination (Phase 2)
    StartDestination.DOCUMENT_VAULT -> OdoDestination.Home // TODO: DocumentVault destination
}

package com.hopcape.odo.feature.onboarding.presentation.welcome

/**
 * The three intro/marketing slides shown before car setup (the "why Odo" hook).
 * A plain enum so the carousel is a pure function of state and stays unit-testable
 * without Compose — the UI maps each page to its own layout/copy, exactly as
 * [com.hopcape.odo.feature.onboarding.presentation.state.OnboardingStep] does for
 * the data-gathering steps. Order is display order; [FAIRNESS] is the last slide
 * (its CTA becomes "Get Started" — see [WelcomeUiState.isLastPage]).
 */
internal enum class WelcomePage {
    /** "You never know if you're overpaying." — brand + value promise. */
    OVERPAYING,

    /** "Hidden charges. Every single time." — social-proof / overcharge testimonial. */
    HIDDEN_CHARGES,

    /** "How do you know the price is fair?" — the 3-step how-it-works + Get Started. */
    FAIRNESS,
}

/**
 * Immutable render state for the intro carousel. The UI is a pure function of this:
 * it reads [currentIndex] to position the pager and derives its footer CTA from
 * [isLastPage]; it never computes navigation itself. [currentIndex] is always a
 * valid index into [pages] (the ViewModel clamps every transition).
 */
internal data class WelcomeUiState(
    val currentIndex: Int = 0,
) {
    /** Ordered slides driving both the pager and the page-indicator dots. */
    val pages: List<WelcomePage> = WelcomePage.entries

    /** Total slide count — the page-indicator dot count. */
    val pageCount: Int get() = pages.size

    /** The slide currently on screen. */
    val currentPage: WelcomePage get() = pages[currentIndex]

    /** First slide — no "back" affordance. */
    val isFirstPage: Boolean get() = currentIndex == 0

    /** Last slide — the primary CTA reads "Get Started" and finishes the carousel. */
    val isLastPage: Boolean get() = currentIndex == pages.lastIndex
}

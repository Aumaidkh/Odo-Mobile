package com.hopcape.odo.feature.paywall.presentation

/**
 * Why the paywall was shown — frames the same offer with a context-specific badge,
 * headline, and subtitle. [SAVINGS] uses [PaywallUiState.amountPaise]; [SCANS_EXHAUSTED]
 * uses [PaywallUiState.freeScans].
 */
internal enum class PaywallTrigger { GENERIC, SCANS_EXHAUSTED, SAVINGS }

/** The two Pro billing periods. */
internal enum class PaywallPlan { MONTHLY, ANNUAL }

/**
 * Display state for the Pro paywall. One screen, three framings ([trigger]); the plan
 * selection and CTA follow [selectedPlan]. Prices are fixed offer copy; only the
 * context figures ([amountPaise] saved, [freeScans] quota) are dynamic.
 */
internal data class PaywallUiState(
    val trigger: PaywallTrigger,
    val selectedPlan: PaywallPlan = PaywallPlan.MONTHLY,
    val amountPaise: Long = 0L,
    val freeScans: Int = 3,
)

/** Generic "everything unlocked" framing. */
internal fun samplePaywallGeneric() = PaywallUiState(trigger = PaywallTrigger.GENERIC)

/** "0 free scans left" framing after the monthly free quota is used up. */
internal fun samplePaywallScans() = PaywallUiState(trigger = PaywallTrigger.SCANS_EXHAUSTED, freeScans = 3)

/** "You just saved Rs. 700" framing right after a fairness win. */
internal fun samplePaywallSavings() = PaywallUiState(trigger = PaywallTrigger.SAVINGS, amountPaise = 70000L)

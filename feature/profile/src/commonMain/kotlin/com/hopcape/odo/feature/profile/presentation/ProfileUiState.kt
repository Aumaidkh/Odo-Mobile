package com.hopcape.odo.feature.profile.presentation

/**
 * Display state for the profile home. [isPro] switches between the Pro-plan card and
 * the Go-Pro upsell; the preference rows show a compact value summary.
 */
internal data class ProfileUiState(
    val name: String,
    val phoneLine: String,
    val isPro: Boolean,
    val proRenews: String,
    val passportOwned: Boolean,
    val notificationsSummary: String,
    val unitsSummary: String,
    val appearanceSummary: String,
)

/** Sample: a Pro subscriber. */
internal fun sampleProProfile(): ProfileUiState = ProfileUiState(
    name = "Rahul Deshmukh",
    phoneLine = "+91 98765 43210 · Pune",
    isPro = true,
    proRenews = "Renews 12 Aug 2026 · ₹149/mo",
    passportOwned = true,
    notificationsSummary = "4 on",
    unitsSummary = "km · ₹",
    appearanceSummary = "Dark",
)

/** Sample: a free-plan user. */
internal fun sampleFreeProfile(): ProfileUiState = ProfileUiState(
    name = "Rahul Deshmukh",
    phoneLine = "+91 98765 43210",
    isPro = false,
    proRenews = "",
    passportOwned = false,
    notificationsSummary = "4 on",
    unitsSummary = "km · ₹",
    appearanceSummary = "Dark",
)

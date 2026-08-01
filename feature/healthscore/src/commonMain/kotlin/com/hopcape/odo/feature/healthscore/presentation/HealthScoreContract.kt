package com.hopcape.odo.feature.healthscore.presentation

/** What the owner did on the health-score screen, as data. */
internal sealed interface HealthScoreEvent {

    /** The back button. */
    data object BackTapped : HealthScoreEvent

    /** The (i) button — "how your score works". */
    data object InfoTapped : HealthScoreEvent

    /** "Unlock with Pro" on the locked breakdown. */
    data object UnlockTapped : HealthScoreEvent
}

/** One-shot handoffs the route host performs. */
internal sealed interface HealthScoreEffect {

    data object GoBack : HealthScoreEffect

    /** Open the explainer sheet. */
    data object OpenInfo : HealthScoreEffect

    /** Open the paywall, tagged with where it was opened from. */
    data object OpenPaywall : HealthScoreEffect
}

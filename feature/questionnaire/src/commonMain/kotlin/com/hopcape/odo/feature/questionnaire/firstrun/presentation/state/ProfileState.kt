package com.hopcape.odo.feature.questionnaire.firstrun.presentation.state

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.domain.owner.model.OnboardingGoal

/**
 * The owner's reason for being here, as the three choices the screen offers.
 *
 * A presentation type, not the domain enum: the copy is goal-shaped ("Stop overpaying")
 * while [OnboardingGoal] is storage-shaped (it mirrors the DB `onboarding_goal` enum).
 * [toDomain] is the one seam between them, so re-wording a card never touches the DB.
 */
internal enum class OnboardingGoalOption {
    STOP_OVERPAYING,
    STAY_HEALTHY,
    SELL_FOR_MORE,
}

/** Presentation choice → the stored [OnboardingGoal] that routes the owner after setup. */
internal fun OnboardingGoalOption.toDomain(): OnboardingGoal = when (this) {
    OnboardingGoalOption.STOP_OVERPAYING -> OnboardingGoal.TRACK_COSTS
    OnboardingGoalOption.STAY_HEALTHY -> OnboardingGoal.NEVER_MISS_RENEWAL
    OnboardingGoalOption.SELL_FOR_MORE -> OnboardingGoal.SELL_SOON
}

/** The last thing asked of the owner: a name to greet them by, and why they came. */
@Immutable
internal data class ProfileState(
    val name: FormField<String> = FormField(""),
    val goal: FormField<OnboardingGoalOption> = FormField(),
) {
    /** A name that is actually a name — a lone space shouldn't unlock Continue. */
    val isNameValid: Boolean get() = name.text.trim().length >= MIN_NAME_LENGTH

    val isAnswered: Boolean get() = isNameValid && goal.value != null

    private companion object {
        const val MIN_NAME_LENGTH = 2
    }
}

package com.hopcape.odo.feature.onboarding.domain.usecase

import com.hopcape.odo.core.domain.owner.model.OnboardingGoal

/**
 * Raw, unvalidated answers from the profile step. Fields are nullable because the screen
 * can submit before both are given; validation happens in [CompleteOnboardingUseCase].
 */
internal data class CompleteOnboardingCommand(
    val name: String?,
    val goal: OnboardingGoal?,
)

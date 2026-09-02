package com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase


/**
 * Raw, unvalidated answers from the profile step. Fields are nullable because the screen
 * can submit before both are given; validation happens in [CompleteOnboardingUseCase].
 */
internal data class CompleteOnboardingCommand(
    val name: String?,
)

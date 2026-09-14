package com.hopcape.odo.feature.questionnaire.presentation

/** Stable handles for the instrumented suite. Never change one without changing the test. */
object QuestionnaireTestTags {
    const val SCREEN = "questionnaire_screen"
    const val CONTINUE = "questionnaire_continue"

    /** One card. Suffixed with the option's stored value, which is a declared constant. */
    fun option(value: String) = "questionnaire_option_$value"
}

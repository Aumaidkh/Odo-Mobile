package com.hopcape.odo.core.domain.owner.model

/**
 * The question keys other modules name.
 *
 * A key is an identifier, so it lives in the shared kernel; the question itself — its copy,
 * icons and options — is declared by the registry in `:feature:questionnaire`. That split is
 * what lets the profile screen ask for the goal question without depending on the feature
 * that draws it.
 *
 * Only keys with a caller outside the questionnaire belong here.
 */
object QuestionKeys {
    val Goal = QuestionKey("goal.v1")

    /** Where the car is serviced — the labour rate every price comparison is quoted at. */
    val Workshop = QuestionKey("workshop.v1")
}

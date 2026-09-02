package com.hopcape.odo.feature.questionnaire

import com.hopcape.odo.core.designsystem.icons.IcCurrencyDollar
import com.hopcape.odo.core.designsystem.icons.IcSpeedometer
import com.hopcape.odo.core.designsystem.icons.IcTagFilled
import com.hopcape.odo.core.domain.owner.model.OnboardingGoal
import com.hopcape.odo.core.domain.owner.model.QuestionKeys
import com.hopcape.odo.feature.questionnaire.resources.Res
import com.hopcape.odo.feature.questionnaire.resources.qn_goal_healthy
import com.hopcape.odo.feature.questionnaire.resources.qn_goal_overpay
import com.hopcape.odo.feature.questionnaire.resources.qn_goal_resale
import com.hopcape.odo.feature.questionnaire.resources.qn_goal_subtitle
import com.hopcape.odo.feature.questionnaire.resources.qn_goal_title

/*
 * Keys carry a version. If a question changes enough that an old answer no longer means the
 * same thing, add `.v2` rather than editing `.v1` — the old answers then stop being read
 * instead of being counted as answers to a question nobody was asked.
 *
 * The keys themselves live in :core:domain's QuestionKeys, because callers outside this module
 * name them. What is declared here is the question: its copy, icons and options.
 */

/**
 * MULTI, because tracking costs *and* not wanting to miss a renewal is the normal case.
 *
 * Nothing reads the set yet. Goal-based routing used to be the one reader and it is gone —
 * it sent every goal to the same screen anyway — so ordering the dashboard by these answers
 * is its own decision, taken once there are real sets to order by.
 */
private val GoalQuestion = Question(
    key = QuestionKeys.Goal,
    title = Res.string.qn_goal_title,
    subtitle = Res.string.qn_goal_subtitle,
    selection = SelectionMode.MULTI,
    options = listOf(
        // The card and the stored value differ on purpose: the copy is goal-shaped while
        // OnboardingGoal is storage-shaped. Re-wording a card must not touch stored data.
        option(OnboardingGoal.TRACK_COSTS, Res.string.qn_goal_overpay, IcCurrencyDollar),
        option(OnboardingGoal.NEVER_MISS_RENEWAL, Res.string.qn_goal_healthy, IcSpeedometer),
        option(OnboardingGoal.SELL_SOON, Res.string.qn_goal_resale, IcTagFilled),
    ),
)

/** The registry the app runs on. Onboarding asks these in order; Profile edits one. */
fun odoQuestions() = QuestionRegistry(listOf(GoalQuestion))

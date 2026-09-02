package com.hopcape.odo.feature.questionnaire

import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.domain.owner.model.OnboardingGoal
import com.hopcape.odo.core.domain.owner.model.QuestionKey
import com.hopcape.odo.feature.questionnaire.resources.Res
import com.hopcape.odo.feature.questionnaire.resources.qn_goal_title
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The guards on a declaration, which are the only thing standing in for a build-time check. */
class QuestionTest {

    @Test
    fun `option stores the enum constant name`() {
        val built = option(OnboardingGoal.TRACK_COSTS, Res.string.qn_goal_title, IcCheck)

        assertEquals("TRACK_COSTS", built.value)
    }

    @Test
    fun `a question with no options is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Question(
                key = QuestionKey("empty.v1"),
                title = Res.string.qn_goal_title,
                selection = SelectionMode.SINGLE,
                options = emptyList(),
            )
        }
    }

    /** A repeated value would be two rows the storage layer cannot tell apart. */
    @Test
    fun `a question repeating an option value is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Question(
                key = QuestionKey("dupe.v1"),
                title = Res.string.qn_goal_title,
                selection = SelectionMode.SINGLE,
                options = listOf(
                    option(OnboardingGoal.TRACK_COSTS, Res.string.qn_goal_title, IcCheck),
                    option(OnboardingGoal.TRACK_COSTS, Res.string.qn_goal_title, IcCheck),
                ),
            )
        }
    }
}

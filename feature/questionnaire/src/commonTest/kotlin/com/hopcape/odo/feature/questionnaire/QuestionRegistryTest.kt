package com.hopcape.odo.feature.questionnaire

import com.hopcape.odo.core.domain.owner.model.OnboardingGoal
import com.hopcape.odo.core.domain.owner.model.QuestionKey
import com.hopcape.odo.core.domain.owner.model.QuestionKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The declared registry, plus the lookups the asking screen depends on. */
class QuestionRegistryTest {

    private val registry = odoQuestions()

    @Test
    fun `no key is declared twice`() {
        assertTrue(registry.duplicateKeys.isEmpty(), "duplicates: ${registry.duplicateKeys}")
    }

    /** Every key is versioned, so a changed question can stop old answers counting. */
    @Test
    fun `every key carries a version suffix`() {
        val unversioned = registry.questions.map { it.key.value }.filterNot { it.matches(VERSIONED) }
        assertTrue(unversioned.isEmpty(), "keys without a .vN suffix: $unversioned")
    }

    /**
     * The stored value must name a real domain constant. Storage is stringly, so nothing else
     * catches a typo here — it would surface as an answer no reader recognises.
     */
    @Test
    fun `every goal option maps back to an OnboardingGoal`() {
        val goal = registry.require(QuestionKeys.Goal)

        val mapped = goal.options.map { OnboardingGoal.valueOf(it.value) }

        assertEquals(OnboardingGoal.entries.toSet(), mapped.toSet(), "every goal must be offered")
    }

    @Test
    fun `find returns null for an undeclared key`() {
        assertNull(registry.find(QuestionKey("nope.v1")))
    }

    @Test
    fun `require fails loudly for an undeclared key`() {
        assertFailsWith<IllegalStateException> { registry.require(QuestionKey("nope.v1")) }
    }

    @Test
    fun `forKeys returns only what was asked for`() {
        val asked = registry.forKeys(listOf(QuestionKeys.Goal))

        assertEquals(listOf(QuestionKeys.Goal), asked.map { it.key })
    }

    @Test
    fun `forKeys skips undeclared keys`() {
        val asked = registry.forKeys(listOf(QuestionKeys.Goal, QuestionKey("nope.v1")))

        assertEquals(listOf(QuestionKeys.Goal), asked.map { it.key })
    }

    /** Registry order, not the caller's, so a caller cannot reorder the flow by accident. */
    @Test
    fun `forKeys answers in registry order`() {
        val reversed = registry.questions.map { it.key }.reversed()

        val asked = registry.forKeys(reversed)

        assertEquals(registry.questions.map { it.key }, asked.map { it.key })
    }

    @Test
    fun `forKeys is empty when nothing matches`() {
        assertTrue(registry.forKeys(listOf(QuestionKey("nope.v1"))).isEmpty())
    }

    private companion object {
        val VERSIONED = Regex(""".+\.v\d+$""")
    }
}

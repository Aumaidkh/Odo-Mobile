package com.hopcape.odo.feature.questionnaire.presentation

import com.hopcape.odo.feature.questionnaire.SelectionMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The selection rule on its own.
 *
 * SINGLE has no declared question since goals went MULTI, so without this the branch would
 * ship untested — and the next SINGLE question would be the one to find out.
 */
class SelectionToggleTest {

    @Test
    fun singleReplacesWhateverWasPicked() {
        assertEquals(setOf("B"), setOf("A").toggle("B", SelectionMode.SINGLE))
    }

    @Test
    fun singleTappedAgainKeepsTheAnswer() {
        // Not a toggle-off: a radio group with nothing selected is not a state the owner
        // can reach by tapping the only thing they picked.
        assertEquals(setOf("A"), setOf("A").toggle("A", SelectionMode.SINGLE))
    }

    @Test
    fun multiAddsToWhatIsPicked() {
        assertEquals(setOf("A", "B"), setOf("A").toggle("B", SelectionMode.MULTI))
    }

    @Test
    fun multiTappedAgainRemoves() {
        assertEquals(setOf("B"), setOf("A", "B").toggle("A", SelectionMode.MULTI))
    }

    @Test
    fun multiCanBeEmptied() {
        assertEquals(emptySet(), setOf("A").toggle("A", SelectionMode.MULTI))
    }
}

package com.hopcape.odo.feature.advisory.presentation.checklist

/** What the owner did on the checklist screen. */
internal sealed interface ChecklistEvent {

    /**
     * "Save to phone", with the card already drawn to PNG bytes.
     *
     * The pixels come with the event because only the screen knows what was laid out —
     * the same hand-off the bill check's share card uses.
     */
    data class SaveClicked(val png: ByteArray?) : ChecklistEvent {

        // A ByteArray in a data class compares by identity, and two captures of the same card
        // are different arrays. Nothing compares these, so the generated equals is removed
        // rather than left as a trap.
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = png?.contentHashCode() ?: 0
    }

    data object BackClicked : ChecklistEvent
}

/** One-shot things the route host performs. */
internal sealed interface ChecklistEffect {

    data object NavigateBack : ChecklistEffect

    /** The card is in the owner's downloads. */
    data object Saved : ChecklistEffect

    /** The card could not be drawn or written. Said in a snackbar, never silently. */
    data object SaveFailed : ChecklistEffect
}

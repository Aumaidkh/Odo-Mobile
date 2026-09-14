package com.hopcape.odo.core.domain.owner.model

import kotlin.time.Instant

/**
 * One option the owner picked for one question.
 *
 * A multi-select answer is several of these sharing a [key].
 */
data class QuestionAnswer(
    val key: QuestionKey,
    /**
     * The name of a domain constant, such as `TRACK_COSTS` — never the label on the card, so
     * re-wording the card does not touch stored data. Turning it back into a typed option is
     * the reading feature's job.
     */
    val value: String,
    /** When the owner answered. Safe to show them, unlike the row's sync timestamps. */
    val answeredAt: Instant,
)

/** The values picked for [key], in the order they were answered. Empty when unanswered. */
fun List<QuestionAnswer>.valuesFor(key: QuestionKey): List<String> =
    filter { it.key == key }.map { it.value }

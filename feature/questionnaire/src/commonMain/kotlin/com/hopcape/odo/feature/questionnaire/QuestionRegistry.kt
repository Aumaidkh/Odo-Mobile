package com.hopcape.odo.feature.questionnaire

import com.hopcape.odo.core.domain.owner.model.QuestionKey

/**
 * Every question the app knows how to ask, in the order onboarding asks them.
 *
 * [forKeys] is what makes asking a subset possible. Without it, editing one answer from the
 * profile screen would have to reopen the whole flow.
 */
class QuestionRegistry(val questions: List<Question>) {

    private val byKey: Map<QuestionKey, Question> = questions.associateBy { it.key }

    /** Keys declared more than once. A test asserts this is empty; nothing checks it at runtime. */
    val duplicateKeys: List<QuestionKey> =
        questions.groupBy { it.key }.filterValues { it.size > 1 }.keys.toList()

    fun find(key: QuestionKey): Question? = byKey[key]

    /**
     * The question for [key].
     *
     * Throws when it is not declared, which is a wiring mistake rather than a runtime
     * condition — a caller asking for a key nobody declared cannot render anything.
     */
    fun require(key: QuestionKey): Question = byKey[key]
        ?: error("Question '${key.value}' is not declared in the registry.")

    /**
     * The questions for [keys], in registry order rather than the order asked for, so a
     * caller cannot accidentally reorder the flow. Unknown keys are skipped.
     */
    fun forKeys(keys: Collection<QuestionKey>): List<Question> =
        questions.filter { it.key in keys.toSet() }
}

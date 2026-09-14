package com.hopcape.odo.core.domain.owner.model

import kotlin.jvm.JvmInline

/**
 * Identifies one question, e.g. `goal.v1`.
 *
 * Versioned: when a question changes enough that an old answer no longer means the same
 * thing, the version goes up and the old answers stop being read.
 *
 * The domain does not know which keys exist. Questions are declared in
 * `:feature:questionnaire`, because they carry labels, icons and option lists.
 */
@JvmInline
value class QuestionKey(val value: String) {
    init {
        require(value.isNotBlank()) { "A question key cannot be blank." }
    }

    override fun toString(): String = value
}

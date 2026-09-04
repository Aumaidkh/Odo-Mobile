package com.hopcape.odo.feature.questionnaire

import androidx.compose.ui.graphics.vector.ImageVector
import com.hopcape.odo.core.domain.owner.model.QuestionKey
import org.jetbrains.compose.resources.StringResource

/** Whether a question takes one answer or several. */
enum class SelectionMode { SINGLE, MULTI }

/**
 * One option the owner can pick.
 *
 * [value] is what gets stored — the name of a domain constant, never the label. Build one
 * with [option] rather than by hand, so the value comes from a real enum.
 *
 * [icon] and [description] are alternatives, not a pair. An outcome ("stop overpaying")
 * reads as an icon and a label; a choice the owner has to place themselves in ("company
 * service centre") needs an example instead, and inventing a glyph for each would only add
 * three shapes nobody can tell apart.
 */
data class QuestionOption internal constructor(
    val value: String,
    val label: StringResource,
    val icon: ImageVector? = null,
    val description: StringResource? = null,
)

/**
 * An option whose stored value is [answer]'s constant name.
 *
 * Typed at the declaration and stringly in storage, which is the trade D1 accepted. The
 * declaration is the only place that has to know the type, so it is the only place worth
 * enforcing it.
 */
fun <T : Enum<T>> option(
    answer: T,
    label: StringResource,
    icon: ImageVector? = null,
    description: StringResource? = null,
) = QuestionOption(value = answer.name, label = label, icon = icon, description = description)

/**
 * One question, as declared in [QuestionRegistry].
 *
 * Hand-written rather than generated: a question carries a label, an icon and an ordered
 * option list, none of which fit in a KSP annotation argument.
 */
data class Question(
    val key: QuestionKey,
    val title: StringResource,
    val subtitle: StringResource? = null,
    val selection: SelectionMode,
    val options: List<QuestionOption>,
) {
    init {
        require(options.isNotEmpty()) { "Question ${key.value} has no options." }
        val duplicates = options.groupBy { it.value }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Question ${key.value} repeats options: $duplicates" }
    }
}

package com.hopcape.odo.core.domain.reminder.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.jvm.JvmInline

/**
 * What a custom reminder is about ("Air pressure check") — a trimmed, non-blank string
 * capped at [MAX_LENGTH].
 *
 * Mandatory, unlike [DocumentTitle][com.hopcape.odo.core.domain.document.model.DocumentTitle]:
 * a document falls back to its type for a name, but a custom reminder has no type to fall
 * back to — its title is the only thing the list row and the notification can say.
 * Construct only via [of].
 */
@JvmInline
value class ReminderTitle private constructor(val value: String) {
    companion object {
        /** Shorter than a document title: this is a notification headline, not a label. */
        const val MAX_LENGTH = 60

        fun of(raw: String?): Either<DomainError, ReminderTitle> {
            val trimmed = raw?.trim()
            return when {
                trimmed.isNullOrEmpty() -> DomainError.BlankReminderTitle.left()
                trimmed.length > MAX_LENGTH -> DomainError.ReminderTitleTooLong(MAX_LENGTH).left()
                else -> ReminderTitle(trimmed).right()
            }
        }
    }
}

package com.hopcape.odo.core.domain.document.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.jvm.JvmInline

/**
 * The owner's own label for a document ("SafeDrive comprehensive") — a trimmed,
 * non-blank string capped at [MAX_LENGTH].
 *
 * Optional (DB `documents.title` is nullable) because [DocumentType] already names the
 * paper well enough to render a row; the title only adds detail when the owner has more
 * than one of a type. So [of] maps null/blank input to a `null` result (absent) rather
 * than an error — only an over-long title fails. Construct only via [of].
 */
@JvmInline
value class DocumentTitle private constructor(val value: String) {
    companion object {
        /**
         * Shorter than a workshop name: this is a label the owner reads in a list row,
         * not free text. Long enough for "Comprehensive — SafeDrive General Insurance".
         */
        const val MAX_LENGTH = 80

        fun of(raw: String?): Either<DomainError, DocumentTitle?> {
            val trimmed = raw?.trim()
            return when {
                trimmed.isNullOrEmpty() -> null.right()
                trimmed.length > MAX_LENGTH -> DomainError.DocumentTitleTooLong(MAX_LENGTH).left()
                else -> DocumentTitle(trimmed).right()
            }
        }
    }
}

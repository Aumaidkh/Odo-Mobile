package com.hopcape.odo.core.domain.document.model

/**
 * Reads over a car's vault that more than one surface needs.
 *
 * The vault holds every document ever filed, including the ones a renewal replaced, so
 * "does this car have insurance?" is never a plain `firstOrNull { it.type == INSURANCE }`.
 * The rule for picking the right one lives here rather than in each caller.
 */

/**
 * The document of [type] that runs longest — the one that speaks for the car today.
 *
 * A renewal is filed as a new document, so last year's lapsed policy sits in the vault
 * next to this year's live one. Ordering by expiry picks the live one; a document with no
 * expiry sorts lowest, because a paper that never lapses is only the answer when nothing
 * dated exists. `null` when the car has no document of this type at all.
 */
fun List<Document>.latestOfType(type: DocumentType): Document? =
    filter { it.type == type }.maxWithOrNull(compareBy(nullsFirst()) { it.expiresOn })

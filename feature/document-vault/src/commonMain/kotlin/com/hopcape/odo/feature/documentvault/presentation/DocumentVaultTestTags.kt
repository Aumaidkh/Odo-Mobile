package com.hopcape.odo.feature.documentvault.presentation

import com.hopcape.odo.core.domain.document.model.DocumentType

/**
 * Test tags for the vault controls an end-to-end test cannot reach by the words on them.
 *
 * Deliberately few. Copy is what an owner sees, so a test that finds a card by its name is
 * testing the product; a tag is only added where the words repeat. Every row's action says
 * "Add" or "Renew", so only the tag says *which* document it belongs to.
 *
 * Public because `:androidApp`'s instrumented tests reference these, which is the only reason
 * anything in this module is public besides the Koin module and the analytics schema.
 */
object DocumentVaultTestTags {

    /** One document's row on the vault overview, on file or missing. */
    fun row(type: DocumentType): String = "documentvault_row_${type.name}"

    /** The "Add" / "Renew" pill inside that row. */
    fun rowAction(type: DocumentType): String = "documentvault_row_action_${type.name}"
}

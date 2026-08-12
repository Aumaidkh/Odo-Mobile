package com.hopcape.odo.core.data.document

import com.hopcape.odo.core.domain.document.entitlement.DocumentAllowance
import com.hopcape.odo.core.domain.document.entitlement.DocumentLimit

/**
 * The [DocumentAllowance] in force while nothing sells a subscription: everyone is on the
 * free tier, so everyone gets [FREE_TIER_LIMIT] documents.
 *
 * Honest rather than temporary-looking. There is no subscription state in the app yet, and
 * "free tier" is the true answer for every owner today — this is not a stub pretending to
 * read something it can't. When payments land, a real adapter reads the owner's plan and
 * replaces this in one line of `coreDataModule`; the cap check in the add-document use case
 * does not change, which is the point of asking through a port.
 */
internal class FreeTierDocumentAllowance : DocumentAllowance {

    override suspend fun current(): DocumentLimit = FREE_TIER

    private companion object {
        /** PRD §pricing — free tier keeps 3 documents; Pro is unlimited. */
        const val FREE_TIER_LIMIT = 3
        val FREE_TIER = DocumentLimit.UpTo(FREE_TIER_LIMIT)
    }
}

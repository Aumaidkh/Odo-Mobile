package com.hopcape.odo.feature.onboarding

import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.model.OwnerId

/**
 * M1 stub for [CurrentOwnerProvider]: there is no auth yet, so every car is owned
 * by a single stable local owner. The id is an offline placeholder — the server
 * stamps the real `owner_id` on sync (DB_SCHEMA). When auth lands, a real provider
 * replaces this in the Koin graph; nothing else changes.
 */
internal class LocalOwnerProvider : CurrentOwnerProvider {
    override fun currentOwnerId(): OwnerId = LOCAL_OWNER_ID

    private companion object {
        val LOCAL_OWNER_ID = OwnerId("local-owner")
    }
}

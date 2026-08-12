package com.hopcape.odo.core.domain.owner.model

import kotlin.jvm.JvmInline

/**
 * Typed identity for the car's owner (the profile/auth user). The Car aggregate
 * references the Owner aggregate by this id only — never by embedding the object.
 */
@JvmInline
value class OwnerId(val value: String) {

    companion object {
        /**
         * The owner of everything created before anyone signed in.
         *
         * Odo works fully offline, so a car can exist before an account does. These rows are
         * stamped with this id and moved onto the real account by the adoption step on the
         * first successful sign-in (SYNC_DESIGN §9).
         *
         * **A constant, deliberately not "whatever `CurrentOwnerProvider` returns".** Once
         * that provider starts answering with the signed-in user's id, reading the
         * placeholder from it would make adoption compare the real id against itself, match
         * nothing, and silently strand every pre-sign-in row on this device forever.
         *
         * It is not a UUID, which is what makes it safe: the server's `owner_id` columns are
         * `uuid`, so a row still carrying this can never be mistaken for a synced one — it
         * is rejected outright rather than written somewhere wrong.
         */
        val LOCAL_PLACEHOLDER = OwnerId("local-owner")
    }
}

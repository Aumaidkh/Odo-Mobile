package com.hopcape.odo.core.domain.subscription

import com.hopcape.odo.core.domain.owner.model.OwnerId

/**
 * Port telling the store who is using the app, so a subscription follows the account rather
 * than the phone.
 *
 * Odo lets someone buy Pro without signing in — asking for a phone number before taking their
 * money is a step that costs more than it protects — so a purchase starts out attached to an
 * anonymous identity the store minted. [identify] is what moves it onto the owner's account
 * the moment they verify a number, and it is the only reason Pro survives a new device.
 *
 * **Neither call blocks and neither reports failure.** Both are best effort by contract: they
 * talk to a store over the network, and nothing about signing in or out may wait on that, let
 * alone fail because of it. An implementation that could not reach the store leaves the link
 * for the next sign-in to make.
 *
 * `:core:data` binds a no-op. Nothing about auth should require a billing module to exist.
 */
interface SubscriptionIdentity {

    /** This device is now acting as [ownerId]. Move any anonymous purchase onto them. */
    fun identify(ownerId: OwnerId)

    /** This device is signed out. Go back to an anonymous identity. */
    fun forget()
}

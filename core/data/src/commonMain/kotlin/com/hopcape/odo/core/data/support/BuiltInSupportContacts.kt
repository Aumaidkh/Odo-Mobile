package com.hopcape.odo.core.data.support

import com.hopcape.odo.core.domain.support.SupportContacts

/**
 * The support address compiled into the build.
 *
 * What the app answers before Remote Config has said anything — a fresh install whose first
 * fetch has not landed, and any device that never reaches Firebase. `RemoteConfigSupport
 * Contacts` decorates this rather than replacing it, so the fallback is always a working
 * address and never a blank one.
 *
 * Unlike the legal URLs, this is not derived from anything: there is no project address to
 * build it out of, so it is written here. Changing it means a release, which is exactly why
 * the remote override exists.
 */
internal class BuiltInSupportContacts : SupportContacts {
    override val email: String = "support@odoapp.in"
}

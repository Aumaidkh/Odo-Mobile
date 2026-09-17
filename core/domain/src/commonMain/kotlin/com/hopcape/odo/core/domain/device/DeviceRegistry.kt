package com.hopcape.odo.core.domain.device

import arrow.core.Either
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Tell the server that this installation is in use by the signed-in owner.
 *
 * Support can find an account but not the install behind it, so nobody can answer "did this
 * person actually open the app". This is what joins the two.
 */
interface DeviceRegistry {

    /** Records the device, or says why it could not. Safe to call repeatedly. */
    suspend fun recordSeen(): Either<DomainError, Unit>
}

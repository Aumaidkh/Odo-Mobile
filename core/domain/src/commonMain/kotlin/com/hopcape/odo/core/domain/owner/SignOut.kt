package com.hopcape.odo.core.domain.owner

/**
 * Signs the owner out.
 *
 * A port because the control that offers it lives in Profile and the work belongs to auth,
 * and a `:feature:*` never imports another. Same shape as [SessionStatusProvider], which
 * Profile already asks whether to show the row at all.
 *
 * Returns nothing and cannot fail: the session is gone locally whatever the server said, and
 * a device that cannot sign out is worse than a refresh token left live.
 */
fun interface SignOut {
    suspend operator fun invoke()
}

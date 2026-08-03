package com.hopcape.odo.feature.auth.domain

import com.hopcape.odo.core.domain.owner.LocalUserDataWipe
import com.hopcape.odo.core.domain.owner.SignOut

/**
 * Signs the owner out and leaves the device holding nothing of theirs.
 *
 * Order matters. The session goes first, so nothing can start a sync pass while the rows are
 * being removed — a run that read a half-wiped database would push whatever survived. Only
 * then is the local copy forgotten.
 *
 * Both steps always run. Neither can fail in a way worth reporting to the owner: the server
 * call is best effort, and a wipe that partly failed is followed by another sign-out or a
 * reinstall. What must not happen is stopping halfway and leaving a device with no session
 * but somebody's data still on it.
 */
internal class SignOutUseCase(
    private val sessions: OdoSessionManager,
    private val wipe: LocalUserDataWipe,
) : SignOut {
    override suspend fun invoke() {
        sessions.signOut()
        wipe.wipe()
    }
}

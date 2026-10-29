package com.hopcape.odo.core.common.session

import kotlin.concurrent.Volatile

/** Whether the app is on screen, and an id that changes each time it comes back. */
interface AppSession {
    val isForeground: Boolean
    val sessionId: String
}

/** For platforms and tests with no lifecycle to follow: always on screen, one session. */
internal object FixedAppSession : AppSession {
    override val isForeground: Boolean = true
    override val sessionId: String = "fixed"
}

/**
 * A session that follows the platform's foreground signal. A new id is made at process
 * start and on every [onForeground], so a background wake has its own session.
 */
class LifecycleAppSession(private val newId: () -> String) : AppSession {

    @Volatile
    override var isForeground: Boolean = false
        private set

    @Volatile
    override var sessionId: String = newId()
        private set

    fun onForeground() {
        sessionId = newId()
        isForeground = true
    }

    fun onBackground() {
        isForeground = false
    }
}

package com.hopcape.odo.core.platform.sync

import com.hopcape.odo.core.sync.SyncReason
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * How long one install waits before a sync, so that not every install goes at once.
 *
 * The trigger for a sync is often something that happened to everybody at the same instant —
 * a push telling every device the server changed, a cell tower coming back, a retry after an
 * outage. Every install then hits the backend in the same second, which is worst at exactly
 * the moment it is least able to take it.
 *
 * Two things are spread, and they solve different halves of it:
 *
 *  - the **first attempt**, by a delay drawn per request, which spreads a shared trigger;
 *  - the **backoff base**, drawn per request too, which spreads the *retries*. WorkManager's
 *    exponential backoff is a formula over that base, so a fixed one has every install
 *    retrying in lockstep however long the outage lasts. Different bases make the curves
 *    diverge instead of converging on the same second.
 *
 * Nothing is spread when somebody is watching. A pull-to-refresh that sits still for twenty
 * seconds reads as a broken button, and the handful of installs whose owner is looking at the
 * screen right now is not the herd worth worrying about.
 */
internal class SyncJitter(private val random: Random = Random.Default) {

    /**
     * How long to wait before the first attempt for [reason].
     *
     * The band is wider the more likely it is that every install was triggered by the same
     * event. A local write is spread by the person doing it and needs almost none; a remote
     * change was fanned out to everybody in one broadcast and needs the most.
     */
    fun initialDelay(reason: SyncReason): Duration = when (reason) {
        // Someone is waiting. No delay, at any cost to the herd.
        SyncReason.Manual, SyncReason.SignIn -> Duration.ZERO

        // One server-side fan-out reaches every install in the same second.
        SyncReason.RemoteChange -> spread(REMOTE_CHANGE_SPREAD)

        // A tower or a home router coming back reconnects everything behind it at once.
        SyncReason.Reconnected -> spread(RECONNECTED_SPREAD)

        // Spread by when people open the app, but mornings are still a peak.
        SyncReason.AppForeground -> spread(FOREGROUND_SPREAD)

        // Already debounced by five seconds, and spread by the person typing.
        SyncReason.LocalWrite -> LOCAL_WRITE_DEBOUNCE + spread(LOCAL_WRITE_SPREAD)
    }

    /** Startup has no [SyncReason]; it is a foreground run by another name. */
    fun startupDelay(): Duration = spread(FOREGROUND_SPREAD)

    /**
     * The base WorkManager multiplies as attempts stack up.
     *
     * Drawn once per request rather than fixed, so two installs that fail together climb
     * different curves — 20s becomes 20/40/80, 45s becomes 45/90/180 — instead of both
     * knocking on the door at the same three moments.
     */
    fun backoffBase(): Duration =
        BACKOFF_FLOOR + spread(BACKOFF_CEILING - BACKOFF_FLOOR)

    /** A uniform draw in `0..width`, inclusive. A zero width is zero, not a crash. */
    private fun spread(width: Duration): Duration {
        val millis = width.inWholeMilliseconds
        return if (millis <= 0) Duration.ZERO else random.nextLong(millis + 1).milliseconds
    }

    private companion object {
        val REMOTE_CHANGE_SPREAD = 120.seconds
        val RECONNECTED_SPREAD = 60.seconds
        val FOREGROUND_SPREAD = 20.seconds
        val LOCAL_WRITE_SPREAD = 10.seconds

        /** Matches `CoroutineSyncScheduler`'s debounce, which this replaces on Android. */
        val LOCAL_WRITE_DEBOUNCE = 5.seconds

        /**
         * WorkManager clamps a backoff below 10 seconds, so the floor stays well clear of it.
         * The ceiling is what keeps the first retry inside a minute.
         */
        val BACKOFF_FLOOR = 20.seconds
        val BACKOFF_CEILING = 45.seconds
    }
}

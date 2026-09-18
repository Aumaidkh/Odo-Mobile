package com.hopcape.odo.core.platform.notification

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * A one-off nudge back into the app, some days after something happened.
 *
 * Not a reminder: nothing here is a date the owner asked to be told about, and none of it is
 * on file. The two schedulers beside this one rebuild their whole schedule from the database
 * because their reminders live there; these are scheduled once, at the moment that earns
 * them, and are forgotten afterwards.
 *
 * They exist because first run leaves nothing behind that will ever ring. An owner who sets
 * up a car, looks around and closes the app has given Odo no date, no expiry and no reason to
 * open again — and a dashboard nobody returns to is the whole of the retention problem.
 *
 * Best-effort by contract, like the others: an implementation swallows its own failures. A
 * nudge that could not be scheduled must never cost the owner the thing they were doing.
 */
fun interface EngagementNudgeScheduler {

    /**
     * Put [nudge] on the schedule, replacing any earlier one of the same kind.
     *
     * Replacing rather than stacking: scanning three bills in a week is a reason to say one
     * thing later, not three.
     */
    suspend fun schedule(nudge: EngagementNudge)
}

/**
 * The nudges Odo sends, and how long after the moment that earns them.
 *
 * Both delays are deliberately long enough to be useful and short enough to still be about
 * the thing that happened. A day after setting a car up, the score the owner was shown has
 * become theirs to improve; three days after a scan is when the next bill is plausible and
 * the last one is still remembered.
 */
enum class EngagementNudge(val delay: Duration) {

    /** A car was set up. */
    FIRST_SCORE(delay = 1.days),

    /** A bill was scanned. */
    SCAN_FOLLOW_UP(delay = 3.days),

    /**
     * A record exists and no account does.
     *
     * The one nudge that is about losing something rather than doing something. Two days is
     * long enough that the owner has stopped thinking about setup and short enough that the
     * record is still small — the point is to ask before there is a year of history riding
     * on one phone, not after.
     */
    BACK_UP(delay = 2.days),
}

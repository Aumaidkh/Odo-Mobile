package com.hopcape.odo.core.platform.notification

/**
 * Whether Odo is allowed to *read* the notifications other apps post.
 *
 * Distinct from [SystemNotificationSettings], which is about posting. The two are unrelated
 * permissions and are refused for different reasons: one stops Odo's reminders appearing, the
 * other stops payment detection working at all.
 *
 * This is not a runtime permission. There is no prompt to show — the owner has to find Odo in
 * a system settings list and turn it on there — so the whole seam is "is it on" and "take me
 * to the page". Everything else is the opt-in screen's job: explaining what would be read,
 * before sending anyone to a page that says Odo will be able to read all notifications.
 *
 * On a platform with no such capability the implementation reports false forever and [open]
 * does nothing, which leaves detection permanently unavailable and every other capture
 * channel working.
 */
interface NotificationAccess {

    /** Whether the OS currently lets Odo read notifications. */
    fun isGranted(): Boolean

    /**
     * Whether this build declares the notification listener at all.
     *
     * Separate from [isGranted] because they fail in opposite directions. A permission that
     * was never granted is the owner's to change; a service the manifest does not declare is
     * one the OS will never bind no matter what the owner or a remote flag says, and nothing
     * anywhere reports that. Declaring it is what puts
     * `BIND_NOTIFICATION_LISTENER_SERVICE` in front of a Play reviewer, so a build can
     * legitimately ship without it — this is how the code above notices.
     */
    fun isListenerDeclared(): Boolean

    /**
     * Open the system page where the owner grants or revokes it.
     *
     * There is no callback. The owner leaves the app, changes a switch, and comes back;
     * whatever is showing has to re-read [isGranted] when it resumes rather than waiting to
     * be told.
     */
    fun open()

    /**
     * Ask the system to bind the listener again.
     *
     * Granted and bound are two different things. The OS unbinds a notification listener
     * whenever the app is updated, and some builds — MIUI in particular — unbind it again
     * whenever they reclaim the process. The permission still reads as granted, the settings
     * screen still shows the switch on, and nothing is ever delivered.
     *
     * There is no way to observe that state, so this is called on every launch rather than in
     * response to anything. It is cheap and idempotent: a listener that is already bound stays
     * bound.
     */
    fun requestRebind()

    /**
     * Keep asking, on a schedule, for as long as the grant is in place.
     *
     * The one rebind path that does not need the app to already be running. Everything else —
     * the service's own disconnect hook, the rebind on launch — is code inside a process that
     * has to exist first, and the state this recovers from is precisely the one where it does
     * not.
     *
     * Idempotent: calling it on every launch keeps one job, not one per launch.
     */
    fun keepBound()

    /**
     * Force the binding back when the ordinary request is not enough.
     *
     * Separate from [requestRebind] because it is destructive: it tears down any binding that
     * does exist on the way to rebuilding one. Only worth doing when [isBound] says there is
     * nothing to lose.
     */
    fun repairBinding()

    /**
     * Whether the listener is actually connected right now — not merely permitted.
     *
     * False on a process that has just started and has not been bound yet, so a caller has to
     * give the system a moment before treating it as a fault.
     */
    fun isBound(): Boolean

    /**
     * Hand the connection back when the owner turns detection off.
     *
     * The grant survives — revoking it is theirs to do, not Odo's — but a listener that stays
     * bound for a feature nobody asked for is reading notifications it has no reason to see.
     * Releasing it means that between switching detection off and switching it on again, the
     * system is not delivering anything to this app at all.
     *
     * This is the difference between a permission the app *has* and one it is *using*, and it
     * is the one a review is entitled to ask about.
     */
    fun releaseBinding()
}

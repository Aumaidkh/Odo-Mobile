package com.hopcape.odo.core.platform.notification

/**
 * Whether this phone will let Odo be started in the background at all.
 *
 * Stock Android restarts an app whose notification listener is enabled. Several manufacturers
 * do not: they hold a separate "autostart" switch of their own, off by default, and while it
 * is off the system's attempt to start Odo is refused. The permission still reads as granted,
 * the listener never binds, and no code in the app can tell the difference or work around it.
 *
 * So this seam does not try. It reports whether the phone is one of those, and opens the page
 * where the owner can change it. Everything else about detection is Odo's problem; this one is
 * the device's, and the only honest thing to do is say so on the screen that asks for the
 * permission.
 */
interface BackgroundStartAccess {

    /**
     * Whether this build is known to block background starts behind its own setting.
     *
     * Matched on the manufacturer, which is a guess rather than a reading: there is no API that
     * answers "will you let me start". A false positive costs one line of advice the owner can
     * ignore; a false negative costs them a feature that silently never works.
     */
    fun needsAttention(): Boolean

    /**
     * Open the manufacturer's own autostart or battery page.
     *
     * @return whether anything was opened. False on a build whose page could not be found, and
     *   then the screen has to fall back to telling the owner where to look.
     */
    fun open(): Boolean
}

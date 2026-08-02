package com.hopcape.odo.core.platform.notification

/**
 * What the operating system, rather than Odo, has decided about notifications.
 *
 * The owner's topic switches live in the app's own settings; this is the permission above
 * them. A reminder cannot be delivered while the OS has notifications blocked, however many
 * topics are switched on, so the notifications screen reads this and says so.
 */
interface SystemNotificationSettings {

    /** Whether the OS currently lets Odo post notifications. */
    fun areEnabled(): Boolean

    /**
     * Open the OS settings page for Odo's notifications. Only the system can grant the
     * permission back once it has been refused, so the app can do no more than send the
     * owner there.
     */
    fun open()
}

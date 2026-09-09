package com.hopcape.odo.web.admin.ui

/**
 * The two things about the chrome somebody chooses, remembered between visits.
 *
 * Not in Supabase, and deliberately: this is how one person likes one browser to
 * look. Storing it server-side would mean a round trip before the first paint and a
 * shared setting between an admin's laptop and the machine in the office.
 *
 * A seam rather than a direct reach for `localStorage`, for the same reason
 * `TokenStore` is one: the moment a composable touches `window` the module has
 * nothing left to test against off-browser.
 *
 * Reads must never throw. A browser with site data blocked is a browser with no
 * preference, which is the default — the same outcome as a first visit.
 */
interface ChromePreferences {

    /** Null until somebody chooses. The caller decides what an unset preference means. */
    var darkTheme: Boolean?

    var railCollapsed: Boolean
}

/** Remembers nothing. The binding off-browser, and in tests. */
object NoChromePreferences : ChromePreferences {
    override var darkTheme: Boolean? = null
    override var railCollapsed: Boolean = false
}

expect fun chromePreferences(): ChromePreferences

package com.hopcape.odo.web.admin.ui

import kotlinx.browser.localStorage
import org.w3c.dom.get
import org.w3c.dom.set

/**
 * `localStorage`, two keys.
 *
 * Reads and writes go straight through rather than being cached in a field, so a
 * second tab that changes the theme is not fighting a stale copy held here.
 *
 * Every access is wrapped: `localStorage` is not merely empty in a browser with site
 * data blocked, it *throws* on access. An admin who has locked their browser down
 * should get the default theme, not a blank page.
 */
actual fun chromePreferences(): ChromePreferences = LocalStorageChromePreferences

private object LocalStorageChromePreferences : ChromePreferences {

    override var darkTheme: Boolean?
        get() = runCatching { localStorage[THEME] }.getOrNull()?.toBooleanStrictOrNull()
        set(value) {
            runCatching {
                if (value == null) localStorage.removeItem(THEME) else localStorage[THEME] = value.toString()
            }
        }

    override var railCollapsed: Boolean
        get() = runCatching { localStorage[RAIL] }.getOrNull()?.toBooleanStrictOrNull() ?: false
        set(value) {
            runCatching { localStorage[RAIL] = value.toString() }
        }

    private const val THEME = "odo.admin.darkTheme"
    private const val RAIL = "odo.admin.railCollapsed"
}

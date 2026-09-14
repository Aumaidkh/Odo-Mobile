package com.hopcape.odo.web.core.platform

import kotlinx.browser.localStorage
import org.w3c.dom.get
import org.w3c.dom.set

/**
 * `localStorage`, under one key.
 *
 * Reads and writes go straight through rather than being cached in a field: a
 * second tab signing out should not leave this one holding a token it can no
 * longer use.
 */
actual fun tokenStore(): TokenStore = LocalStorageTokenStore

private object LocalStorageTokenStore : TokenStore {

    private const val KEY = "odo.blog.refreshToken"

    override var refreshToken: String?
        get() = localStorage[KEY]?.takeIf { it.isNotBlank() }
        set(value) {
            if (value == null) localStorage.removeItem(KEY) else localStorage[KEY] = value
        }
}

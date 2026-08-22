package com.hopcape.odo.web.blog.platform

/**
 * Where the refresh token lives between page loads.
 *
 * An interface rather than a direct reach for `localStorage`, so the auth
 * adapter can be tested without a browser and so the one place that touches
 * browser storage is nameable when the storage choice is revisited.
 *
 * What is kept here is the refresh token, not the ID token. The ID token is
 * short-lived and is held in memory only: writing it down would mean persisting
 * a credential that is valid right now, for the sake of skipping a request that
 * takes a few hundred milliseconds once per page load.
 */
interface TokenStore {

    /** Null when nobody is signed in on this browser. */
    var refreshToken: String?
}

/**
 * The browser's `localStorage`, or an in-memory stand-in off-browser.
 *
 * localStorage and not a cookie: nothing on the server reads this, and a cookie
 * would be sent on every request to the domain for no reason. It is the same
 * exposure the Firebase JS SDK accepts for the same token — readable by any
 * script that ends up on the page, which is why the page's CSP matters more than
 * the storage choice does.
 */
expect fun tokenStore(): TokenStore

package com.hopcape.odo.web.blog.infrastructure

/**
 * Which Firebase project the CMS signs in against, and who is allowed through.
 *
 * The key below is a public client identifier — the same class of value the app
 * ships inside `google-services.json` and the same one `web/build.ts` bakes into
 * the account-deletion page. It identifies the project; it authorises nothing.
 * What protects an account is its password and the project's own rules.
 */
object FirebaseConfig {

    /** Production. The project the Play listing and the legal pages already use. */
    const val API_KEY: String = "AIzaSyB8A39cTEw-_4mtRntVatyf5ZWYhiwojUc"

    const val PROJECT_ID: String = "odo-mobile-ba9aa"

    /**
     * Who may publish.
     *
     * **This is a gate on the screen, not security.** Anybody who can open the
     * developer console can get past it, and every account in this Firebase
     * project is an app user signed in by phone — so without a list, enabling
     * password sign-in would make the CMS reachable by anyone who registered one.
     *
     * The real check is a custom claim (`author: true`) minted server-side and
     * read off the ID token, plus rules on whatever stores the posts. Neither
     * exists yet: there is no server, and `AdminRepository` is still backed by
     * sample data. When the CMS gets a backend, that backend refuses a token
     * without the claim and this list stops mattering.
     *
     * Add an address here to let it publish. An empty list lets nobody in, which
     * is the safe way to be wrong; the alternative — an empty list meaning
     * "everybody" — is a mistake that looks like it is working.
     */
    val AUTHOR_EMAILS: Set<String> = setOf(
        "zahid@gmail.com",
    )

    /** Where a password is exchanged for tokens. */
    const val SIGN_IN_ENDPOINT: String =
        "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword"

    /** Where an account's email and display name are read. */
    const val LOOKUP_ENDPOINT: String =
        "https://identitytoolkit.googleapis.com/v1/accounts:lookup"

    /**
     * Where a refresh token becomes a new ID token.
     *
     * A different host from the two above, which matters twice: the page's
     * `connect-src` has to allow both, and an API-key referrer restriction has to
     * cover both.
     */
    const val REFRESH_ENDPOINT: String = "https://securetoken.googleapis.com/v1/token"
}

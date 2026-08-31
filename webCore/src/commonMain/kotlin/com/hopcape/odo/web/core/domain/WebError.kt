package com.hopcape.odo.web.core.domain

/**
 * Everything a repository is allowed to fail with.
 *
 * A closed set rather than an exception, for the same reason the app's ports use
 * one: a screen has to decide what to draw, and it can only do that if the
 * failures are countable. Whatever a transport actually throws — a Ktor timeout,
 * a PostgREST error body — is mapped into one of these at the edge, so no screen
 * ever sees an HTTP status.
 *
 * Shared by both web apps, so the members are named for what happened rather than
 * for who it happened to. What each one *says* is the app's own business: the blog
 * reads [NotPermitted] as "you are not an author", the admin panel as "you are not
 * staff", and neither word belongs in here.
 */
sealed interface WebError {

    /** The request never reached anything. Retrying is worth offering. */
    data object Offline : WebError

    /** The thing asked for does not exist. Draws the 404 page. */
    data object NotFound : WebError

    /**
     * The email and password did not match.
     *
     * [triesLeft] is what the design counts down. Null when the backend does not
     * say — the screen then drops the count rather than inventing one.
     */
    data class SignInRejected(val triesLeft: Int?) : WebError

    /** A signed-in call was made without a session, or with one that has expired. */
    data object NotSignedIn : WebError

    /**
     * The credentials were right and the account is still not allowed in.
     *
     * The 403 from a session function — `blog-session` refusing an address that is
     * not an author, `admin-session` refusing one that is not on the staff list.
     *
     * A separate outcome from [SignInRejected] because it is not a typo: telling
     * somebody their password was wrong when it was not sends them round a loop
     * that cannot end.
     */
    data object NotPermitted : WebError

    /**
     * The password was right and nobody is allowed in at all, because the list of
     * who may is empty.
     *
     * Separate from [NotPermitted] because they send you to different places. One
     * is a decision about an account; this one is a setting nobody has filled in,
     * and reporting it as the first sends whoever is holding the correct password
     * looking at their own account.
     */
    data object NotConfigured : WebError

    /**
     * Sign-in cannot be attempted at all — the provider is switched off for this
     * Firebase project.
     *
     * Its own member because it is a configuration mistake rather than a failure:
     * nothing the person at the keyboard does will fix it, so the screen has to
     * say something other than "try again".
     */
    data object SignInUnavailable : WebError

    /** Anything else. [cause] is for the log, never for the reader. */
    data class Unexpected(val cause: String? = null) : WebError
}

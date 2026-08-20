package com.hopcape.odo.web.blog.domain

/**
 * Everything a repository is allowed to fail with.
 *
 * A closed set rather than an exception, for the same reason the app's ports use
 * one: a screen has to decide what to draw, and it can only do that if the
 * failures are countable. Whatever a transport actually throws — a Ktor timeout,
 * a PostgREST error body — is mapped into one of these at the edge, so no screen
 * ever sees an HTTP status.
 */
sealed interface BlogError {

    /** The request never reached anything. Retrying is worth offering. */
    data object Offline : BlogError

    /** There is no such post, category or author. Draws the 404 page. */
    data object NotFound : BlogError

    /**
     * The email and password did not match.
     *
     * [triesLeft] is what the design counts down. Null when the backend does not
     * say — the screen then drops the count rather than inventing one.
     */
    data class SignInRejected(val triesLeft: Int?) : BlogError

    /** An admin call was made without a session, or with one that has expired. */
    data object NotSignedIn : BlogError

    /** Anything else. [cause] is for the log, never for the reader. */
    data class Unexpected(val cause: String? = null) : BlogError
}

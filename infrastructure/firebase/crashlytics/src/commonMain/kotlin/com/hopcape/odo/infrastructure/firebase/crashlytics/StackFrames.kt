package com.hopcape.odo.infrastructure.firebase.crashlytics

/**
 * Put the frames described by [formatted] back onto this throwable.
 *
 * Crashlytics groups by the top frames it is handed, and the throwable the sink builds has
 * none of its own. A trace that will not parse leaves the throwable untouched.
 */
internal expect fun Throwable.restoreFrames(formatted: String)

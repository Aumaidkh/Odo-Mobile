package com.hopcape.odo.core.domain.owner

/**
 * The owner's city — the key every fairness benchmark is looked up by ("Pune average").
 *
 * `null` until they set it on their profile, and that is a normal state rather than an
 * error: onboarding deliberately does not ask, so an owner can use the app for weeks
 * without one. With no city there is simply no verdict — never a guessed one.
 *
 * `suspend` because the answer is stored, not held in memory: the adapter reads the
 * profile row, and a port that hid that behind a plain function would push a blocking
 * read onto whatever thread happened to call it.
 */
fun interface CurrentCityProvider {
    suspend fun currentCity(): String?
}

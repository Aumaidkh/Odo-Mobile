package com.hopcape.odo.feature.profile.presentation

/**
 * Test tags for the profile controls an end-to-end test cannot reach by the words on them.
 *
 * Deliberately few. Copy is what an owner sees, so a test that finds a row by its name is
 * testing the product; a tag is only added where the words move — a preference row's
 * summary changes with the setting, and the last row says "Sign in" or "Sign out"
 * depending on the session.
 *
 * Public because `:androidApp`'s instrumented tests reference these, which is the only
 * reason anything in this module is public besides the Koin module and the analytics schema.
 */
object ProfileTestTags {

    /** The notifications row, whose summary counts the topics that are on. */
    const val NOTIFICATIONS_ROW: String = "profile_notifications_row"

    /** The units row, whose summary names the distance unit. */
    const val UNITS_ROW: String = "profile_units_row"

    /** The appearance row, whose summary names the theme. */
    const val APPEARANCE_ROW: String = "profile_appearance_row"

    /** The last row: sign in, or sign out once there is a session. */
    const val SESSION_ROW: String = "profile_session_row"

    /** The confirm button in the delete-my-data dialog. */
    const val DELETE_CONFIRM: String = "profile_delete_confirm"
}

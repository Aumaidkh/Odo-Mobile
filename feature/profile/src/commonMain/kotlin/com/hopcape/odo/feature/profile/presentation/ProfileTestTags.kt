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

    /**
     * The confirm button in the sign-out sheet.
     *
     * Tagged because it reads the same as the row that opened it, so "Sign out" matches two
     * nodes the moment the sheet is up.
     */
    const val SIGN_OUT_CONFIRM: String = "profile_sign_out_confirm"
    /** The sync diagnostics line under the version. */
    const val SYNC_ROW = "profile_sync_row"

    /** The "Privacy & permissions" row on the profile home. */
    const val PRIVACY_ROW: String = "profile_privacy_row"

    /*
     * The four device-access rows. Tagged rather than found by name because their trailing
     * state is what a test asserts, and "Allowed" appears on three of them at once.
     */
    const val PRIVACY_CAMERA_ROW: String = "privacy_camera_row"
    const val PRIVACY_LOCATION_ROW: String = "privacy_location_row"
    const val PRIVACY_NOTIFICATIONS_ROW: String = "privacy_notifications_row"
    const val PRIVACY_FILES_ROW: String = "privacy_files_row"

    /*
     * The three privacy switches. A switch has no words of its own — the label is a sibling
     * node — so a test toggling one has nothing else to find it by.
     */
    const val PRIVACY_SHARE_PRICES: String = "privacy_share_prices"
    const val PRIVACY_KEEP_ROUTES: String = "privacy_keep_routes"
    const val PRIVACY_USAGE_ANALYTICS: String = "privacy_usage_analytics"

    /** The danger row that starts the account erase. */
    const val PRIVACY_DELETE_ACCOUNT: String = "privacy_delete_account"

    /** The field the confirmation phrase is typed into. */
    const val DELETE_ACCOUNT_PHRASE: String = "delete_account_phrase"

    /** The confirm button in the delete-account sheet. */
    const val DELETE_ACCOUNT_CONFIRM: String = "delete_account_confirm"
}

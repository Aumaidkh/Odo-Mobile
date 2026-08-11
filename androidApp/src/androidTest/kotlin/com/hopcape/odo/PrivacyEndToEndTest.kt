package com.hopcape.odo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hopcape.odo.feature.profile.presentation.ProfileTestTags
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Privacy & permissions, driven through the running app.
 *
 * These cover what a ViewModel test cannot: that a switch moved on this screen actually
 * reaches the table the rest of the app reads, and survives leaving the screen. On a privacy
 * control that gap is the whole failure — a toggle that looks off and stores on is worse than
 * no toggle at all.
 *
 * The device-access rows are asserted for presence and shape rather than for a particular
 * grant. What the emulator has granted varies with the run, and a test that demanded
 * "Allowed" would be testing the test harness.
 */
@RunWith(AndroidJUnit4::class)
class PrivacyEndToEndTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun startFromASetUpDevice() {
        resetProfile()
        resetTrips()
        seedOnboardedOwner()
        rule.activityRule.scenario.recreate()
    }

    /* ------------------------------ Device access ------------------------------ */

    @Test
    fun deviceAccess_listsTheFourThingsOdoCanReach() {
        rule.openProfile()
        rule.openPrivacy()

        rule.onNodeWithText(PrivacyCopy.CAMERA).assertExists()
        rule.onNodeWithText(PrivacyCopy.LOCATION).assertExists()
        rule.onNodeWithText(PrivacyCopy.NOTIFICATIONS).assertExists()
        rule.onNodeWithText(PrivacyCopy.FILES).assertExists()
    }

    @Test
    fun filesRow_alwaysSaysItIsAskedPerDocument() {
        rule.openProfile()
        rule.openPrivacy()

        // Not a permission at all: the picker grants access to the one document chosen. The
        // row earns its place by saying so, and its state never changes.
        rule.assertAccessRow(ProfileTestTags.PRIVACY_FILES_ROW, PrivacyCopy.FILES_STATE)
    }

    @Test
    fun deviceAccess_saysWhoOwnsTheseSwitches() {
        rule.openProfile()
        rule.openPrivacy()

        // Android owns them, and the screen has to say so rather than offering a toggle that
        // could not work.
        rule.onNodeWithText(PrivacyCopy.MANAGED).assertExists()
    }

    /* ------------------------------ The switches ------------------------------ */

    @Test
    fun defaults_areWhatTheAppShipsWith() {
        rule.openProfile()
        rule.openPrivacy()

        // Asserted on screen, not in the database. `app_settings` has no row until the first
        // save — a missing row *is* the default (AppSettingsRepositoryImpl maps null to
        // AppSettings.Default), so querying it here would test nothing and find nothing.
        rule.onNodeWithTag(ProfileTestTags.PRIVACY_SHARE_PRICES).assertIsOn()
        rule.onNodeWithTag(ProfileTestTags.PRIVACY_KEEP_ROUTES).assertIsOff()
        rule.onNodeWithTag(ProfileTestTags.PRIVACY_USAGE_ANALYTICS).assertIsOn()
    }

    @Test
    fun priceSharing_defaultsOnInTheProfileRowItself() {
        // This one *does* have a row from the start — the profile is written at onboarding,
        // and the column's default is what a new owner gets.
        assertEquals(1L, storedSharesPrices())
    }

    @Test
    fun turningAnalyticsOff_reachesTheDatabase() {
        rule.openProfile()
        rule.openPrivacy()

        rule.togglePrivacySwitch(ProfileTestTags.PRIVACY_USAGE_ANALYTICS)

        assertEquals(0L, storedPrivacyFlag("privacy_usage_analytics"))
    }

    @Test
    fun turningPriceSharingOff_reachesTheProfileRowThatSyncs() {
        rule.openProfile()
        rule.openPrivacy()

        rule.togglePrivacySwitch(ProfileTestTags.PRIVACY_SHARE_PRICES)

        // On `profiles`, not `app_settings` — the server is what has to honour it.
        assertEquals(0L, storedSharesPrices())
    }

    @Test
    fun turningRoutesOn_changesTheLineUnderTheSwitch() {
        rule.openProfile()
        rule.openPrivacy()
        rule.onNodeWithText(PrivacyCopy.ROUTES_OFF).assertExists()

        rule.togglePrivacySwitch(ProfileTestTags.PRIVACY_KEEP_ROUTES)

        // The supporting line states what is true now, not what the switch would do — an
        // owner reading "only distance is stored" under a switch that is on has it backwards.
        rule.awaitText(PrivacyCopy.ROUTES_ON)
        assertEquals(1L, storedPrivacyFlag("privacy_keep_trip_routes"))
    }

    @Test
    fun turningRoutesOff_erasesTheCoordinatesAlreadyStored() {
        seedTripWithRoute()
        rule.activityRule.scenario.recreate()
        rule.openProfile()
        rule.openPrivacy()
        // Routes start off, so switch on and back off — the second toggle is the purge.
        rule.togglePrivacySwitch(ProfileTestTags.PRIVACY_KEEP_ROUTES)
        rule.awaitText(PrivacyCopy.ROUTES_ON)

        rule.togglePrivacySwitch(ProfileTestTags.PRIVACY_KEEP_ROUTES)
        rule.awaitText(PrivacyCopy.ROUTES_OFF)

        // "Only distance is stored" has to be true of trips already on the phone, or the
        // switch is a promise about tomorrow rather than a privacy control.
        assertEquals(0L, tripsWithCoordinates())
        assertEquals(0L, parkedLocationCount())
    }

    @Test
    fun turningRoutesOff_keepsTheDistance() {
        seedTripWithRoute()
        rule.activityRule.scenario.recreate()
        rule.openProfile()
        rule.openPrivacy()
        rule.togglePrivacySwitch(ProfileTestTags.PRIVACY_KEEP_ROUTES)
        rule.awaitText(PrivacyCopy.ROUTES_ON)

        rule.togglePrivacySwitch(ProfileTestTags.PRIVACY_KEEP_ROUTES)
        rule.awaitText(PrivacyCopy.ROUTES_OFF)

        // The switch costs the owner their route and nothing else. Distance was integrated
        // during the drive, not derived from the points just erased.
        assertEquals(12_000L, seededTripDistance())
    }

    @Test
    fun aSwitch_survivesLeavingAndReopeningTheScreen() {
        rule.openProfile()
        rule.openPrivacy()
        rule.togglePrivacySwitch(ProfileTestTags.PRIVACY_USAGE_ANALYTICS)

        rule.onNodeWithContentDescription(BACK).performClick()
        rule.awaitText(ProfileCopy.TITLE)
        rule.openPrivacy()

        // Both halves matter: the switch reads back off from storage, and storage says so.
        rule.onNodeWithTag(ProfileTestTags.PRIVACY_USAGE_ANALYTICS).assertIsOff()
        assertEquals(0L, storedPrivacyFlag("privacy_usage_analytics"))
    }

    /* ------------------------------ What the rows open ------------------------------ */

    @Test
    fun privacyPolicy_showsTheSummaryInTheApp() {
        rule.openProfile()
        rule.openPrivacy()

        rule.openPrivacyPolicy()

        // Native, so it renders with no network — someone reading a privacy notice is often
        // deciding whether to stay, and that is the worst moment for a blank screen.
        rule.onNodeWithText(PrivacyCopy.POLICY_SHORT_VERSION).assertExists()
    }

    @Test
    fun deleteAccount_confirmsBeforeAnythingHappens() {
        rule.openProfile()
        rule.openPrivacy()

        rule.openDeleteAccount()

        rule.onNodeWithText(PrivacyCopy.DELETE_HEADING).assertExists()
        rule.onNodeWithText(PrivacyCopy.DELETE_ACTION).assertExists()
    }

    @Test
    fun deleteAccount_staysLockedUntilThePhraseIsTyped() {
        rule.openProfile()
        rule.openPrivacy()
        rule.openDeleteAccount()

        rule.onNodeWithTag(ProfileTestTags.DELETE_ACCOUNT_CONFIRM).assertIsNotEnabled()

        rule.typeDeleteConfirmation(PrivacyCopy.DELETE_PHRASE)

        // Typing the words is the last chance to stop. A button that could be reached by
        // tapping twice in the same place can be reached by accident.
        rule.onNodeWithTag(ProfileTestTags.DELETE_ACCOUNT_CONFIRM).assertIsEnabled()
    }

    @Test
    fun deleteAccount_aWrongPhrase_leavesTheButtonLocked() {
        rule.openProfile()
        rule.openPrivacy()
        rule.openDeleteAccount()

        rule.typeDeleteConfirmation("delete everything")

        rule.onNodeWithTag(ProfileTestTags.DELETE_ACCOUNT_CONFIRM).assertIsNotEnabled()
    }

    @Test
    fun deleteAccount_onADeviceThatNeverSignedIn_wipesEverythingAndReturnsToFirstRun() {
        rule.openProfile()
        rule.openPrivacy()
        rule.openDeleteAccount()

        rule.deleteAccountForReal()

        // No session, so no code step: there is no account to prove anything against, and
        // making someone verify a number to delete a local database would be ceremony.
        rule.awaitText(ProfileCopy.WELCOME_HEADLINE)
        assertEquals(0L, liveCarCount())
        assertEquals(0L, liveProfileCount())
    }

    @Test
    fun deleteAccount_cancelling_leavesEverythingAlone() {
        rule.openProfile()
        rule.openPrivacy()
        rule.openDeleteAccount()

        rule.onNodeWithText(PrivacyCopy.DELETE_CANCEL).performClick()
        rule.awaitText(PrivacyCopy.TITLE)

        // The car and the profile are both still here — backing out of the confirmation is
        // the one exit from this flow that has to cost nothing.
        assertEquals(1L, liveCarCount())
        assertEquals(SEEDED_OWNER_NAME, storedProfileField("full_name"))
    }
}

/** The back arrow every full-screen surface carries. A content description, not text. */
private const val BACK = "Back"

/** The name [seedOnboardedOwner] writes — same value the onboarding robot uses. */
private const val SEEDED_OWNER_NAME = "Rahul"

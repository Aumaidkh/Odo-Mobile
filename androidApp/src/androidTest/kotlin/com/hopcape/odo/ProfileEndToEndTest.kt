package com.hopcape.odo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hopcape.odo.core.domain.settings.model.ThemePreference
import com.hopcape.odo.core.domain.shared.DistanceUnit
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.feature.profile.presentation.ProfileTestTags
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

/**
 * The profile, driven through the running app.
 *
 * What these cover is the part unit tests cannot: that a setting changed on this screen
 * reaches the rest of the app — the greeting on Home, the unit every reading is written in,
 * and the row summaries an owner reads back on their next visit.
 *
 * Every test seeds a finished setup first. Where the app opens is decided once per launch,
 * so a test that started with no profile would be driving an app that had already gone to
 * the welcome carousel.
 */
@RunWith(AndroidJUnit4::class)
class ProfileEndToEndTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /**
     * Start every test from a set-up device.
     *
     * The activity is recreated because the rule launches it before this runs, so it may
     * have already read a previous test's data — and where the app opens is decided once
     * per launch.
     */
    @Before
    fun startFromASetUpDevice() {
        resetProfile()
        seedOnboardedOwner()
        rule.activityRule.scenario.recreate()
    }

    /* ------------------------------ Reading it back ------------------------------ */

    @Test
    fun profile_showsTheOwnerAndWhatTheirSettingsAreOn() {
        rule.openProfile()

        rule.onNodeWithText(OWNER_NAME).assertExists()
        // The seeded owner has a city, so the card names it rather than prompting for one.
        rule.onNodeWithText(SEEDED_CITY).assertExists()
        rule.assertRowSummary(ProfileTestTags.NOTIFICATIONS_ROW, ProfileCopy.topicsOn(DEFAULT_TOPICS_ON))
        rule.assertRowSummary(ProfileTestTags.UNITS_ROW, ProfileCopy.unitsSummary("km"))
    }

    @Test
    fun withNoSession_theLastRowOffersSignIn_ratherThanSignOut() {
        rule.openProfile()

        // There is no auth yet, so the honest row is the one that can do something.
        rule.onNodeWithText(ProfileCopy.SIGN_IN).assertExists()
    }

    /* ------------------------------ Editing ------------------------------ */

    @Test
    fun editingTheName_changesTheGreetingOnHome() {
        rule.openProfile()
        rule.openEditProfile()

        rule.typeName(NEW_NAME)
        rule.saveProfile()

        rule.awaitText(NEW_NAME)
        assertEquals(NEW_NAME, storedProfileField("full_name"))
        rule.leaveProfile()
        // Home greets by name, so the edit has to be visible the moment the owner is back.
        rule.awaitTextStartingWith(NEW_NAME.take(4))
    }

    @Test
    fun settingTheCity_isWhatTurnsPriceChecksOn() {
        clearProfileCity()
        rule.activityRule.scenario.recreate()
        rule.openProfile()
        rule.awaitText(ProfileCopy.CITY_MISSING)

        rule.openEditProfile()
        rule.chooseCity(CHOSEN_CITY)
        rule.saveProfile()

        rule.awaitText(CHOSEN_CITY)
        assertEquals(CHOSEN_CITY, storedProfileField("city"))
    }

    @Test
    fun aRejectedNameOrEmail_isSaidUnderTheFieldItBelongsTo() {
        rule.openProfile()
        rule.openEditProfile()

        rule.typeName("R")
        rule.typeEmail("not-an-address")
        rule.saveProfile()

        rule.awaitText(ProfileCopy.NAME_TOO_SHORT)
        rule.onNodeWithText(ProfileCopy.EMAIL_INVALID).assertExists()
        // Nothing was written, so the name on file is still the one that was valid.
        assertEquals(OWNER_NAME, storedProfileField("full_name"))
    }

    /* ------------------------------ Settings ------------------------------ */

    @Test
    fun theChosenTheme_isStoredAndReadBackOnTheNextVisit() {
        rule.openProfile()
        rule.openAppearanceSheet()

        rule.chooseTheme(ThemePreference.LIGHT)
        rule.closeSheet()

        rule.assertRowSummary(ProfileTestTags.APPEARANCE_ROW, ProfileCopy.THEME_LIGHT)
        assertEquals(ThemePreference.LIGHT.name, storedTheme())
        // Away and back: the summary comes from the stored row, not from the sheet.
        rule.leaveProfile()
        rule.openProfile()
        rule.assertRowSummary(ProfileTestTags.APPEARANCE_ROW, ProfileCopy.THEME_LIGHT)
    }

    @Test
    fun choosingMiles_rewritesEveryReadingInTheApp() {
        rule.openProfile()
        rule.openUnitsSheet()

        rule.chooseDistanceUnit(DistanceUnit.MILE)
        rule.closeSheet()

        assertEquals(DistanceUnit.MILE.name, storedDistanceUnit())
        rule.assertRowSummary(ProfileTestTags.UNITS_ROW, ProfileCopy.unitsSummary("mi"))
        rule.leaveProfile()
        // Home's car line carries the odometer: 40,000 km is 24,855 mi.
        rule.awaitTextContaining(SEEDED_ODOMETER_IN_MILES)
    }

    @Test
    fun aNotificationTopic_staysWhereItWasPut() {
        rule.openProfile()
        rule.openNotifications()

        rule.toggleNotificationTopic(ProfileCopy.NOTIF_HEALTH)

        rule.waitForIdle()
        assertEquals(1L, storedNotificationFlag("notif_health_drop"))
        rule.onNodeWithContentDescription(BACK).performClick()
        // The row's summary counts the topics that are on, so it moves with the switch.
        rule.assertRowSummary(
            ProfileTestTags.NOTIFICATIONS_ROW,
            ProfileCopy.topicsOn(DEFAULT_TOPICS_ON + 1),
        )
    }

    @Test
    fun aLeadTime_isTheOwnersToChange() {
        rule.openProfile()
        rule.openNotifications()

        // The complaint the issue is about: 30 days is too early for some owners and too
        // late for others, and the only lever used to be turning the topic off.
        rule.toggleLeadChip(DocumentType.INSURANCE, days = 60)
        rule.toggleLeadChip(DocumentType.INSURANCE, days = 1)

        // Stored longest-first, and only the type that was touched is written.
        assertEquals("INSURANCE=60,30,7", storedLeadDays())
    }

    @Test
    fun theHourReminders_arriveAtIsTheOwnersToChange() {
        rule.openProfile()
        rule.openNotifications()

        rule.chooseNotifyHour(ProfileCopy.NOTIFY_AT_8_AM)

        assertEquals(8L, storedNotifyHour())
    }

    /* ------------------------------ Deleting ------------------------------ */

    @Test
    fun deletingEverything_asksFirst_thenLeavesTheAppAtFirstRun() {
        rule.openProfile()
        rule.openEditProfile()

        rule.deleteEverything()

        rule.awaitText(ProfileCopy.WELCOME_HEADLINE, timeoutMillis = DELETE_TIMEOUT_MILLIS)
        assertNull(storedProfileField("full_name"))
        assertEquals(0L, liveCarCount())
    }

    private companion object {
        const val OWNER_NAME = "Rahul"
        const val NEW_NAME = "Rahul Deshmukh"
        const val SEEDED_CITY = "Pune"
        const val CHOSEN_CITY = "Mumbai"

        /** Document expiry, service due, overcharge alerts and the monthly summary. */
        const val DEFAULT_TOPICS_ON = 4

        /** The seeded car reads 40,000 km, which is 24,855 miles. */
        const val SEEDED_ODOMETER_IN_MILES = "24,855 mi"

        const val BACK = "Back"

        /** The wipe touches four tables and then re-routes the whole stack. */
        const val DELETE_TIMEOUT_MILLIS = 15_000L
    }

    /* ------------------------------ Help & support ------------------------------ */

    @Test
    fun profile_offersHelpAndSupport() {
        rule.openProfile()

        // The row this whole feature hangs off. It was commented out for as long as the
        // sheet behind it opened "Coming soon" screens.
        rule.onNodeWithTag(ProfileTestTags.HELP_ROW).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(ProfileCopy.HELP).assertExists()
    }

    @Test
    fun helpSheet_showsEveryRowItStillOffers() {
        rule.openProfile()
        rule.openHelpSheet()

        rule.onNodeWithText(SupportCopy.EMAIL).assertExists()
        rule.onNodeWithText(SupportCopy.REPORT).assertExists()
        rule.onNodeWithText(SupportCopy.IDEA).assertExists()
        rule.onNodeWithText(SupportCopy.FLAG).assertExists()
        rule.onNodeWithText(SupportCopy.FAQS).performScrollTo().assertExists()
        rule.onNodeWithText(SupportCopy.LICENCES).performScrollTo().assertExists()
    }

    @Test
    fun helpSheet_noLongerOffersChatOrTickets() {
        rule.openProfile()
        rule.openHelpSheet()

        // Both were sample data — a hardcoded "Online" badge and a hardcoded open-ticket
        // count — with no backend behind either. This is the test that stops them coming
        // back by accident.
        rule.onNodeWithText(SupportCopy.CHAT).assertDoesNotExist()
        rule.onNodeWithText(SupportCopy.TICKETS).assertDoesNotExist()
    }

    @Test
    fun helpSheet_faqsAnswersAQuestion() {
        rule.openProfile()
        rule.openHelpSheet()
        rule.openFromHelpSheet(SupportCopy.FAQS, SupportCopy.FAQ_FIRST_QUESTION)

        // Closed to begin with: the answer is only there once the row is tapped.
        rule.onNodeWithText(SupportCopy.FAQ_FIRST_ANSWER_FRAGMENT, substring = true).assertDoesNotExist()

        rule.onNodeWithText(SupportCopy.FAQ_FIRST_QUESTION).performClick()
        rule.awaitTextContaining(SupportCopy.FAQ_FIRST_ANSWER_FRAGMENT)
    }

    @Test
    fun helpSearch_matchesWordsThatOnlyAppearInAnAnswer() {
        rule.openProfile()
        rule.openHelpSheet()
        rule.openFromHelpSheet(SupportCopy.SEARCH_BOX, SupportCopy.SEARCH_PROMPT)

        rule.typeHelpSearch("phone")

        // "phone" is nowhere in that question — it is in the answer. Matching titles only
        // would find nothing, which is the bug this guards.
        rule.awaitText(SupportCopy.FAQ_FIRST_QUESTION)
    }

    @Test
    fun helpSearch_saysSoWhenNothingMatches() {
        rule.openProfile()
        rule.openHelpSheet()
        rule.openFromHelpSheet(SupportCopy.SEARCH_BOX, SupportCopy.SEARCH_PROMPT)

        rule.typeHelpSearch("carburettor")

        rule.awaitText(SupportCopy.SEARCH_EMPTY)
    }

    @Test
    fun helpSheet_reportAProblemOpensAFormThatWillNotSendEmpty() {
        rule.openProfile()
        rule.openHelpSheet()
        rule.openFromHelpSheet(SupportCopy.REPORT, SupportCopy.FEEDBACK_HINT)

        // Nothing typed yet, so there is nothing worth mailing.
        rule.onNodeWithText(SupportCopy.FEEDBACK_SEND).assertIsNotEnabled()
    }

    @Test
    fun helpSheet_licencesCreditWhatShips() {
        rule.openProfile()
        rule.openHelpSheet()
        rule.openFromHelpSheet(SupportCopy.LICENCES, SupportCopy.LICENCE_APACHE)

        rule.onNodeWithText(SupportCopy.LICENCE_APACHE).assertExists()
    }

}

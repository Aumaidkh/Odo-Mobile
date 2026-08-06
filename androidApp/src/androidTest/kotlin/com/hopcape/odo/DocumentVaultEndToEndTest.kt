package com.hopcape.odo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.feature.documentvault.presentation.DocumentVaultTestTags
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every document-vault flow an owner can reach today, driven against the real app: the real
 * Koin graph, the real SQLite database, the real navigation graph, the real ViewModels and the
 * real file store.
 *
 * The unit-level pieces each pass on their own, which is exactly why this exists — both bugs
 * this feature has actually had lived in the seams. The routes handed their ViewModels
 * arguments in a way Koin could not build, and the local database had no `documents` table at
 * all; every unit test stayed green through both.
 *
 * **What is seeded and why.** Documents are written straight to the database, because the add
 * flow can only produce one shape today: an uploaded file, no expiry, no issue date. Every
 * status the vault exists to show — valid, expiring, lapsed — needs an expiry the UI has no
 * field for, and the Verified badge needs a DigiLocker copy there is no importer for. The
 * add, delete, renew and share flows are all driven by tapping.
 *
 * Expiry dates are seeded relative to today for the same reason the app resolves them that
 * way: a fixed date would eventually mean a different status than the test was written for.
 *
 * **What is deliberately not covered**, because the product has no affordance for it yet:
 * replacing a file (the menu item opens no picker — `TODO(files)` in the route), viewing or
 * downloading the file (both effects are still stubs), and editing a document's details
 * (no screen collects them).
 */
@RunWith(AndroidJUnit4::class)
class DocumentVaultEndToEndTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /**
     * Start every test from a set-up device with an empty vault.
     *
     * The activity is recreated because the rule launches it before this runs, so it may have
     * already read a previous test's data.
     *
     * Intents are captured for the whole class, not only the tests that pick a file: starting
     * the recording once keeps it symmetrical with the release in [tearDown].
     */
    @Before
    fun startFromASetUpDeviceWithAnEmptyVault() {
        Intents.init()
        resetVault()
        seedOnboardedOwner()
        rule.activityRule.scenario.recreate()
    }

    @After
    fun tearDown() = Intents.release()

    /* ------------------------------ The vault overview ------------------------------ */

    @Test
    fun anEmptyVaultAsksForEveryDocumentItTracks() {
        rule.openVault()

        // The four papers a driver is stopped for, each offering the way to add it.
        rule.awaitText(VaultCopy.HEADER_ADD_TITLE)
        listOf(VaultCopy.DOC_INSURANCE, VaultCopy.DOC_PUC, VaultCopy.DOC_RC, VaultCopy.DOC_LICENCE)
            .forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.assertRowShows(DocumentType.RC, VaultCopy.STATUS_NOT_ADDED)
        rule.onNodeWithTag(DocumentVaultTestTags.rowAction(DocumentType.LICENCE)).assertIsDisplayed()
        rule.onNodeWithText(VaultCopy.ADD_DOCUMENT_BAR).assertIsDisplayed()
    }

    @Test
    fun aSeededVaultShowsWhereEachDocumentStands() {
        seedTrackedDocuments()
        rule.openVault()

        // The owner's own label wins over the type's name once there is one.
        rule.awaitText(VaultFixtures.INSURANCE_TITLE)
        rule.onNodeWithText(VaultCopy.validTill(VaultFixtures.INSURANCE_EXPIRY)).assertIsDisplayed()
        rule.onNodeWithText(VaultCopy.expiresIn(VaultFixtures.PUC_DAYS_LEFT, VaultFixtures.PUC_EXPIRY))
            .assertIsDisplayed()
        // An RC never lapses, so it is on file rather than counting down.
        rule.onNodeWithText(VaultCopy.STATUS_LIFETIME).assertIsDisplayed()
        rule.onNodeWithText(VaultCopy.DOC_LICENCE).assertIsDisplayed()

        // One document is inside its renewal window, and the header names it.
        rule.onNodeWithText(VaultCopy.attentionTitle(1)).assertIsDisplayed()
        rule.onNodeWithText(VaultCopy.attentionBody(VaultCopy.DOC_PUC)).assertIsDisplayed()
        rule.onNodeWithText(VaultCopy.PILL_EXPIRES_SOON).assertIsDisplayed()
        // The 15-day nudge has already passed, so the next one is the 3-day one.
        rule.onNodeWithText(VaultCopy.reminder(VaultFixtures.PUC_REMINDER_DAYS)).assertIsDisplayed()
    }

    @Test
    fun aFullyCoveredVaultSaysSoInsteadOfNagging() {
        seedFullyCoveredVault()
        rule.openVault()

        rule.awaitText(VaultCopy.HEADER_COVERED_TITLE)
        rule.onNodeWithText(VaultCopy.coveredBody(4)).assertIsDisplayed()
        // Nothing to act on: no missing row, and no renewal pill anywhere.
        rule.onNodeWithText(VaultCopy.STATUS_NOT_ADDED).assertDoesNotExist()
        rule.onNodeWithText(VaultCopy.PILL_EXPIRES_SOON).assertDoesNotExist()
    }

    @Test
    fun aLapsedDocumentIsShownAsExpiredAndOfferedARenewal() {
        seedDocument(id = VaultFixtures.PUC_ID, type = DocumentType.PUC, expiresOn = VaultFixtures.EXPIRED_ON)
        rule.openVault()

        // Driving on a lapsed PUC is an offence, so it is stated plainly and given an action.
        rule.awaitText(VaultCopy.expiredOn(VaultFixtures.EXPIRED_ON))
        rule.onNodeWithText(VaultCopy.PILL_EXPIRED).assertIsDisplayed()
        rule.onNodeWithTag(DocumentVaultTestTags.rowAction(DocumentType.PUC)).assertIsDisplayed()
        rule.onNodeWithText(VaultCopy.attentionTitle(1)).assertIsDisplayed()
    }

    /* ------------------------------ Adding a document ------------------------------ */

    @Test
    fun addingAFileFromARowStoresItUnderThatRowsType() {
        rule.openVault()
        rule.awaitText(VaultCopy.HEADER_ADD_TITLE)
        rule.addFromRow(DocumentType.RC)

        // The row that was tapped decides the type, so the owner is not asked twice.
        rule.onNodeWithText(VaultCopy.ADD_CHIP_RC).assertIsSelected()
        rule.onNodeWithText(VaultCopy.ADD_CHIP_INSURANCE).assertIsNotSelected()

        rule.uploadAFile()

        // The success screen reads the document back rather than repeating what was sent.
        rule.awaitText(VaultCopy.added(VaultCopy.DOC_RC))
        // An uploaded file carries no expiry, so there is nothing to promise a reminder for.
        rule.onNodeWithText(VaultCopy.SUCCESS_NO_REMINDER).assertIsDisplayed()

        rule.onNodeWithText(VaultCopy.SUCCESS_BACK).performClick()

        // The vault re-reads the repository it was already observing — nothing refreshes it.
        rule.awaitText(VaultCopy.STATUS_LIFETIME)
        rule.assertRowShows(DocumentType.RC, VaultCopy.PILL_VALID)
        rule.onNodeWithText(VaultCopy.DOC_INSURANCE).assertIsDisplayed()
    }

    @Test
    fun addingFromTheBarStartsOnInsuranceAndTheTypeCanBeChanged() {
        rule.openVault()
        rule.awaitText(VaultCopy.HEADER_ADD_TITLE)
        rule.addFromBar()

        // Named by no row, so the flow opens on the document most owners add first.
        rule.onNodeWithText(VaultCopy.ADD_CHIP_INSURANCE).assertIsSelected()
        rule.onNodeWithText(VaultCopy.ADD_CHIP_PUC).performClick()
        rule.onNodeWithText(VaultCopy.ADD_CHIP_PUC).assertIsSelected()
        rule.onNodeWithText(VaultCopy.ADD_CHIP_INSURANCE).assertIsNotSelected()

        rule.uploadAFile()
        rule.awaitText(VaultCopy.added(VaultCopy.DOC_PUC))
    }

    @Test
    fun anAddedDocumentKeepsACopyOfTheFileInsideTheApp() {
        rule.openVault()
        rule.awaitText(VaultCopy.HEADER_ADD_TITLE)
        rule.addFromRow(DocumentType.INSURANCE)
        rule.uploadAFile()
        rule.awaitText(VaultCopy.added(VaultCopy.DOC_INSURANCE))
        rule.onNodeWithText(VaultCopy.SUCCESS_BACK).performClick()

        // The picked URI stops resolving once the picker's permission lapses, so the bytes
        // have to be the app's own copy — the document opens whether or not that URI still
        // works. The id is the app's, so the file is found by the row that points at it.
        rule.awaitText(VaultCopy.PILL_VALID)
        rule.openDocument(DocumentType.INSURANCE)
        rule.onNodeWithText(VaultCopy.DETAIL_VIEW).assertIsDisplayed()
        rule.onNodeWithText(VaultCopy.DETAIL_FILE_MISSING).assertDoesNotExist()
    }

    @Test
    fun addAnotherGoesBackForTheNextDocument() {
        rule.openVault()
        rule.awaitText(VaultCopy.HEADER_ADD_TITLE)
        rule.addFromRow(DocumentType.INSURANCE)
        rule.uploadAFile()
        rule.awaitText(VaultCopy.added(VaultCopy.DOC_INSURANCE))

        rule.onNodeWithText(VaultCopy.SUCCESS_ADD_ANOTHER).performClick()

        // A fresh add, named by nothing, so it starts where the bar starts.
        rule.awaitText(VaultCopy.ADD_TITLE)
        rule.onNodeWithText(VaultCopy.ADD_CHIP_INSURANCE).assertIsSelected()
    }

    @Test
    fun scanningAndDigiLockerSayTheyAreNotReadyAndStoreNothing() {
        rule.openVault()
        rule.awaitText(VaultCopy.HEADER_ADD_TITLE)
        rule.addFromRow(DocumentType.INSURANCE)

        // Neither has a capture behind it yet. Saying so beats walking an owner to a success
        // screen for a document that was never written.
        rule.onNodeWithText(VaultCopy.CAPTURE_SCAN).performClick()
        rule.awaitText(VaultCopy.CAPTURE_UNAVAILABLE)
        rule.onNodeWithText(VaultCopy.CAPTURE_DIGILOCKER).performClick()
        rule.awaitText(VaultCopy.CAPTURE_UNAVAILABLE)

        rule.onNodeWithLabel(VaultCopy.CLOSE_LABEL).performClick()
        rule.awaitText(VaultCopy.HEADER_ADD_TITLE)
        rule.onNodeWithTag(DocumentVaultTestTags.rowAction(DocumentType.INSURANCE)).assertIsDisplayed()
    }

    @Test
    fun theFreeTierCapRefusesAFourthDocumentAndSaysWhy() {
        // Three is the free tier's whole allowance (PRD pricing), counted per owner. The
        // refusal must name that reason: it used to surface as "Something went wrong",
        // which reads as a broken app rather than a full plan.
        seedTrackedDocuments()
        rule.openVault()
        rule.awaitText(VaultFixtures.INSURANCE_TITLE)
        rule.addFromRow(DocumentType.LICENCE)
        rule.uploadAFile()

        // Refused with the reason, and the flow stays put rather than claiming a save.
        rule.awaitText(VaultCopy.LIMIT_REACHED)
        rule.onNodeWithText(VaultCopy.ADD_TITLE).assertIsDisplayed()
    }

    /* ------------------------------ One document ------------------------------ */

    @Test
    fun anOfficialCopyIsVerified() {
        seedTrackedDocuments()
        rule.openVault()
        rule.awaitText(VaultFixtures.INSURANCE_TITLE)

        rule.openDocument(DocumentType.INSURANCE)

        // Only a DigiLocker copy came from the issuer, so only it earns the badge.
        rule.awaitText(VaultCopy.DETAIL_VERIFIED)
        rule.onNodeWithText(VaultCopy.issuedOn(VaultFixtures.ISSUED_ON)).assertIsDisplayed()
    }

    @Test
    fun aFileTheOwnerSuppliedIsNotVerified() {
        seedTrackedDocuments()
        rule.openVault()
        rule.awaitText(VaultCopy.PILL_EXPIRES_SOON)

        rule.openDocument(DocumentType.PUC)

        // A file the owner picked is a claim, not proof — same screen, no badge.
        rule.awaitText(VaultCopy.detailExpiresIn(VaultFixtures.PUC_DAYS_LEFT))
        rule.onNodeWithText(VaultCopy.DETAIL_VERIFIED).assertDoesNotExist()
    }

    @Test
    fun aDocumentThatNeverExpiresCountsDownNothing() {
        seedTrackedDocuments()
        rule.openVault()
        rule.awaitText(VaultCopy.STATUS_LIFETIME)

        rule.openDocument(DocumentType.RC)

        rule.awaitText(VaultCopy.DETAIL_LIFETIME)
        rule.onNodeWithText(VaultCopy.DETAIL_RENEW).assertDoesNotExist()
    }

    @Test
    fun aDocumentWhoseFileIsGoneSaysSoInsteadOfOfferingAViewer() {
        // What a restore from backup leaves: the row survives, the bytes do not.
        seedDocument(
            id = VaultFixtures.INSURANCE_ID,
            type = DocumentType.INSURANCE,
            expiresOn = VaultFixtures.INSURANCE_EXPIRY,
            withFile = false,
        )
        rule.openVault()
        rule.awaitText(VaultCopy.DOC_INSURANCE)

        rule.openDocument(DocumentType.INSURANCE)

        rule.awaitText(VaultCopy.DETAIL_FILE_MISSING)
        rule.onNodeWithText(VaultCopy.DETAIL_VIEW).assertDoesNotExist()
    }

    @Test
    fun renewNowOpensTheAddFlowOnTheSameType() {
        seedTrackedDocuments()
        rule.openVault()
        rule.awaitText(VaultCopy.PILL_EXPIRES_SOON)

        rule.openDocument(DocumentType.PUC)
        rule.onNodeWithText(VaultCopy.DETAIL_RENEW).performClick()

        // A renewal is a new document of the same type, not an edit of the old one — so the
        // add flow opens, pre-selected, and the lapsing copy stays where it is.
        rule.awaitText(VaultCopy.ADD_TITLE)
        rule.onNodeWithText(VaultCopy.ADD_CHIP_PUC).assertIsSelected()
    }

    @Test
    fun deletingADocumentLeavesTheVaultAskingForItAgain() {
        seedTrackedDocuments()
        rule.openVault()
        rule.awaitText(VaultFixtures.INSURANCE_TITLE)
        rule.openDocument(DocumentType.INSURANCE)

        rule.openDocumentMenu()
        rule.onNodeWithText(VaultCopy.MENU_DELETE).performClick()

        // The screen has nothing left to show, so it leaves; the row goes back to asking.
        rule.awaitText(VaultCopy.TITLE)
        rule.awaitGone(VaultFixtures.INSURANCE_TITLE)
        rule.assertRowShows(DocumentType.INSURANCE, VaultCopy.STATUS_NOT_ADDED)
    }

    @Test
    fun aDocumentDeletedElsewhereClosesTheScreenShowingIt() {
        seedTrackedDocuments()
        rule.openVault()
        rule.awaitText(VaultFixtures.INSURANCE_TITLE)
        rule.openDocument(DocumentType.INSURANCE)

        // Deleted from under an open screen — the document keeps flowing while it is on show,
        // so the screen finds out rather than displaying something that is gone.
        softDeleteDocument(VaultFixtures.INSURANCE_ID)

        rule.awaitText(VaultCopy.TITLE)
        rule.awaitGone(VaultFixtures.INSURANCE_TITLE)
    }

    /* ------------------------------ Sharing ------------------------------ */

    @Test
    fun theShareSheetNamesTheDocumentAndItsTargets() {
        seedTrackedDocuments()
        rule.openVault()
        rule.awaitText(VaultFixtures.INSURANCE_TITLE)
        rule.openDocument(DocumentType.INSURANCE)

        rule.openDocumentMenu()
        rule.onNodeWithText(VaultCopy.MENU_SHARE).performClick()

        // The sheet is its own destination, so it names what it is about rather than
        // inheriting it from whatever is behind it.
        rule.awaitText(VaultCopy.shareTitle(VaultFixtures.INSURANCE_TITLE))
        rule.onNodeWithText(VaultCopy.SHARE_WHATSAPP).assertIsDisplayed()
        rule.onNodeWithText(VaultCopy.SHARE_EMAIL).assertIsDisplayed()
        rule.onNodeWithText(VaultCopy.SHARE_COPY).assertIsDisplayed()
    }

    /* ------------------------------ The file on disk ------------------------------ */

    @Test
    fun deletingADocumentTakesItsFileWithIt() {
        rule.openVault()
        rule.awaitText(VaultCopy.HEADER_ADD_TITLE)
        rule.addFromRow(DocumentType.INSURANCE)
        rule.uploadAFile()
        rule.awaitText(VaultCopy.added(VaultCopy.DOC_INSURANCE))
        rule.onNodeWithText(VaultCopy.SUCCESS_BACK).performClick()
        rule.awaitText(VaultCopy.PILL_VALID)

        rule.openDocument(DocumentType.INSURANCE)
        rule.openDocumentMenu()
        rule.onNodeWithText(VaultCopy.MENU_DELETE).performClick()
        rule.awaitText(VaultCopy.TITLE)

        // A byte blob outliving its row is wasted space nothing can ever reach again.
        assertTrue(noVaultFilesRemain())
    }
}

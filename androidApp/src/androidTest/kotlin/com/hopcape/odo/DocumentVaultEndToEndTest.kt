package com.hopcape.odo

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.espresso.intent.Intents
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.feature.documentvault.presentation.DocumentVaultTestTags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
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
 * **What is seeded and why.** Documents are written straight to the database when the test is
 * about a *status* — valid, expiring, lapsed — because producing those through the UI means
 * driving a date picker to a date chosen for the status, and the Verified badge needs a
 * DigiLocker copy there is no importer for. The add, delete, renew and share flows are all
 * driven by tapping.
 *
 * An upload now ends at the confirm step, where the dates are read off the paper before it is
 * filed. The stub file has no readable date on it, so the flows that go all the way through
 * use an RC — the one kind of paper that never renews and so saves without one.
 *
 * Expiry dates are seeded relative to today for the same reason the app resolves them that
 * way: a fixed date would eventually mean a different status than the test was written for.
 *
 * **What is deliberately not covered**, because the product has no affordance for it yet:
 * viewing or downloading the file (both effects are still stubs).
 */
@RunWith(AndroidJUnit4::class)
class DocumentVaultEndToEndTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /** Granted for the whole class so the hand-off to the scanner reaches its viewfinder. */
    @get:Rule
    val cameraPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    companion object {
        /**
         * The owner exists before anything launches an activity.
         *
         * Where the app opens is read once per launch and the rule starts the activity before
         * `@Before` runs, so on the first test of the class an unseeded profile put the app on
         * the welcome carousel — and recreating it did not move it off. Whichever test the
         * runner happened to put first then failed on its way to the vault.
         */
        @JvmStatic
        @BeforeClass
        fun seedTheOwnerOnce() {
            resetVault()
            seedOnboardedOwner()
        }
    }

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

        rule.fileAnUploadedDocument()

        // The success screen reads the document back rather than repeating what was sent.
        rule.awaitDocumentFiled(DocumentType.RC)
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

        // A PUC renews, so the upload stops at the confirm step until it has an expiry.
        rule.uploadAFile()
        rule.awaitText(VaultCopy.REVIEW_TITLE)
        rule.onNodeWithText(VaultCopy.REVIEW_EXPIRY_REQUIRED).assertIsDisplayed()
    }

    /**
     * The gap this feature closed. An uploaded document used to be filed on the spot with no
     * dates on it, which meant it produced no reminder — the one thing the vault is for.
     */
    @Test
    fun anUploadedDocumentIsReadForItsDatesBeforeItIsFiled() {
        rule.openVault()
        rule.awaitText(VaultCopy.HEADER_ADD_TITLE)
        rule.addFromRow(DocumentType.INSURANCE)

        rule.uploadAFile()

        // The confirm step, not a success screen: nothing is in the vault yet.
        rule.awaitText(VaultCopy.REVIEW_TITLE)
        rule.onNodeWithText(VaultCopy.REVIEW_EXPIRY_REQUIRED).assertIsDisplayed()
        rule.onNodeWithText(VaultCopy.REVIEW_SAVE).performClick()
        rule.onNodeWithText(VaultCopy.REVIEW_TITLE).assertIsDisplayed()
    }

    @Test
    fun anAddedDocumentKeepsACopyOfTheFileInsideTheApp() {
        rule.openVault()
        rule.awaitText(VaultCopy.HEADER_ADD_TITLE)
        rule.addFromRow(DocumentType.RC)
        rule.fileAnUploadedDocument()
        rule.awaitDocumentFiled(DocumentType.RC)
        rule.onNodeWithText(VaultCopy.SUCCESS_BACK).performClick()

        // The picked URI stops resolving once the picker's permission lapses, so the bytes
        // have to be the app's own copy — the document opens whether or not that URI still
        // works. The id is the app's, so the file is found by the row that points at it.
        rule.awaitText(VaultCopy.PILL_VALID)
        rule.openDocument(DocumentType.RC)
        rule.onNodeWithText(VaultCopy.DETAIL_VIEW).assertIsDisplayed()
        rule.onNodeWithText(VaultCopy.DETAIL_FILE_MISSING).assertDoesNotExist()
    }

    @Test
    fun addAnotherGoesBackForTheNextDocument() {
        rule.openVault()
        rule.awaitText(VaultCopy.HEADER_ADD_TITLE)
        rule.addFromRow(DocumentType.RC)
        rule.fileAnUploadedDocument()
        rule.awaitDocumentFiled(DocumentType.RC)

        rule.onNodeWithText(VaultCopy.SUCCESS_ADD_ANOTHER).performClick()

        // A fresh add, named by nothing, so it starts where the bar starts.
        rule.awaitText(VaultCopy.ADD_TITLE)
        rule.onNodeWithText(VaultCopy.ADD_CHIP_INSURANCE).assertIsSelected()
    }

    /**
     * A finished add leaves nothing behind it.
     *
     * The add walks through three screens — the add screen, the confirm step, the success
     * screen — and each used to stay on the stack. Back from the success screen stepped into
     * a confirm step for a document already filed, and back from the vault returned to a
     * success screen for the same one. Both are gone once the document is saved, so back does
     * what back from the vault normally does: it leaves.
     */
    @Test
    fun backFromTheSuccessScreenLeavesTheFinishedAddBehind() {
        rule.openVault()
        rule.awaitText(VaultCopy.HEADER_ADD_TITLE)
        rule.addFromRow(DocumentType.RC)
        rule.fileAnUploadedDocument()
        rule.awaitDocumentFiled(DocumentType.RC)

        Espresso.pressBack()

        rule.awaitText(VaultCopy.TITLE)
        rule.onNodeWithText(VaultCopy.REVIEW_TITLE).assertDoesNotExist()

        // And out of the vault, to the garage that opened it.
        Espresso.pressBack()
        rule.awaitText(GarageCopy.TITLE)
    }

    /** The same, for the inline button — the two ways out end in the same place. */
    @Test
    fun backToDocumentsLeavesTheFinishedAddBehind() {
        rule.openVault()
        rule.awaitText(VaultCopy.HEADER_ADD_TITLE)
        rule.addFromRow(DocumentType.RC)
        rule.fileAnUploadedDocument()
        rule.awaitDocumentFiled(DocumentType.RC)

        rule.onNodeWithText(VaultCopy.SUCCESS_BACK).performClick()
        rule.awaitText(VaultCopy.PILL_VALID)

        Espresso.pressBack()
        rule.awaitText(GarageCopy.TITLE)
    }

    /** "Add another" replaces the finished add rather than stacking on top of it. */
    @Test
    fun addAnotherDoesNotLeaveTheFinishedAddUnderTheNewOne() {
        rule.openVault()
        rule.awaitText(VaultCopy.HEADER_ADD_TITLE)
        rule.addFromRow(DocumentType.RC)
        rule.fileAnUploadedDocument()
        rule.awaitDocumentFiled(DocumentType.RC)
        rule.onNodeWithText(VaultCopy.SUCCESS_ADD_ANOTHER).performClick()
        rule.awaitText(VaultCopy.ADD_TITLE)

        // Backing out of the second add lands on the vault, not on the first add's success
        // screen with its own add screen under that.
        Espresso.pressBack()

        rule.awaitText(VaultCopy.TITLE)
        rule.onNodeWithText(VaultCopy.added(VaultCopy.DOC_RC)).assertDoesNotExist()
    }

    @Test
    fun digiLockerSaysItIsNotReadyAndStoresNothing() {
        rule.openVault()
        rule.awaitText(VaultCopy.HEADER_ADD_TITLE)
        rule.addFromRow(DocumentType.INSURANCE)

        // No importer behind it yet. Saying so beats walking an owner to a success screen
        // for a document that was never written.
        rule.onNodeWithText(VaultCopy.CAPTURE_DIGILOCKER).performClick()
        rule.awaitText(VaultCopy.CAPTURE_UNAVAILABLE)

        rule.onNodeWithLabel(VaultCopy.CLOSE_LABEL).performClick()
        rule.awaitText(VaultCopy.HEADER_ADD_TITLE)
        rule.onNodeWithTag(DocumentVaultTestTags.rowAction(DocumentType.INSURANCE)).assertIsDisplayed()
    }

    /**
     * The vault's "Scan with camera" used to dead-end on "coming soon" while the scanner's
     * document path was already built. It hands over now, and it takes the type the owner
     * chose with it — an RC scanned from the RC row must not come back as an insurance.
     */
    @Test
    fun scanningFromTheVaultOpensTheScannerOnTheChosenPaper() {
        rule.openVault()
        rule.awaitText(VaultCopy.HEADER_ADD_TITLE)
        rule.addFromRow(DocumentType.RC)

        rule.onNodeWithText(VaultCopy.CAPTURE_SCAN).performClick()

        // The viewfinder opens pointed at a paper, not at a bill.
        rule.awaitText(ScanCopy.SCAN_TITLE_DOCUMENT)
        rule.onNodeWithText(ScanCopy.ALIGN_DOCUMENT).assertIsDisplayed()
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

    /**
     * Replacing swaps the file behind a document. It used to do nothing at all — the menu
     * item was wired to an empty lambda — which is what left the old file in place.
     *
     * The badge is the proof that the swap happened: an official copy that has been replaced
     * by a file the owner picked is no longer an official copy.
     */
    @Test
    fun replacingAFileSwapsItInsteadOfAddingAnother() {
        seedDocument(
            id = VaultFixtures.INSURANCE_ID,
            type = DocumentType.INSURANCE,
            expiresOn = VaultFixtures.INSURANCE_EXPIRY,
            source = "DIGILOCKER",
            title = VaultFixtures.INSURANCE_TITLE,
            issuedOn = VaultFixtures.ISSUED_ON,
        )
        rule.openVault()
        rule.awaitText(VaultFixtures.INSURANCE_TITLE)
        rule.openDocument(DocumentType.INSURANCE)
        rule.awaitText(VaultCopy.DETAIL_VERIFIED)

        stubPickedFile()
        rule.openDocumentMenu()
        rule.onNodeWithText(VaultCopy.MENU_REPLACE).performClick()

        rule.awaitGone(VaultCopy.DETAIL_VERIFIED)
        // The same document, not a second one, and the same dates on it. The title is
        // asserted through awaitText because the screen shows it twice — on the card and in
        // the collapsing title.
        rule.awaitText(VaultFixtures.INSURANCE_TITLE)
        rule.onNodeWithText(VaultCopy.issuedOn(VaultFixtures.ISSUED_ON)).assertIsDisplayed()
        assertEquals(1, storedDocumentCount())
        // And one file behind it: the replacement took the old one's place.
        assertEquals(1, storedVaultFiles().size)
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

    /**
     * A document filed before the app read dates has no expiry, so it produces no reminder.
     * The sheet is how it gets one without deleting and re-adding the paper.
     */
    @Test
    fun aDocumentWithNoExpiryCanBeGivenOneFromItsDetail() {
        seedDocument(id = VaultFixtures.INSURANCE_ID, type = DocumentType.INSURANCE, expiresOn = null)
        rule.openVault()
        rule.awaitText(VaultCopy.DOC_INSURANCE)
        rule.openDocument(DocumentType.INSURANCE)

        rule.openDocumentMenu()
        rule.onNodeWithText(VaultCopy.MENU_EDIT_DATES).performClick()

        // Its own destination, opened on what the document already has — which is nothing.
        rule.awaitText(VaultCopy.DATES_TITLE)
        rule.onNodeWithText(VaultCopy.DATES_REQUIRED).assertIsDisplayed()
        rule.onNodeWithText(VaultCopy.DATES_SAVE).assertIsNotEnabled()
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
        rule.addFromRow(DocumentType.RC)
        rule.fileAnUploadedDocument()
        rule.awaitDocumentFiled(DocumentType.RC)
        rule.onNodeWithText(VaultCopy.SUCCESS_BACK).performClick()
        rule.awaitText(VaultCopy.PILL_VALID)

        rule.openDocument(DocumentType.RC)
        rule.openDocumentMenu()
        rule.onNodeWithText(VaultCopy.MENU_DELETE).performClick()
        rule.awaitText(VaultCopy.TITLE)

        // A byte blob outliving its row is wasted space nothing can ever reach again.
        assertTrue(noVaultFilesRemain())
    }
}

package com.hopcape.odo

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import arrow.core.left
import com.hopcape.odo.core.domain.scan.entitlement.ScanLimit
import com.hopcape.odo.core.domain.scan.model.BillType
import com.hopcape.odo.core.domain.shared.DomainError
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every bill-scanner flow an owner can reach, driven against the running app: the real Koin
 * graph, the real SQLite database, the real navigation graph, the real ViewModels and the real
 * repositories that store what comes out.
 *
 * **What is faked, and only this.** The extractor. The shipped binding refuses every scan
 * because the Edge Function that reads a bill does not exist yet, so without a fake there is
 * no readable-bill path at all — and refusing is itself one of the cases here. The port exists
 * for exactly this; everything above it is the shipped code.
 *
 * **Why the shutter is not driven.** CameraX needs a camera, an emulator's is a synthetic
 * scene, and one capture costs seconds on every test. The tests enter at the key a capture
 * navigates to, carrying the photo key it would have carried. What the camera itself does —
 * binding, previewing, writing the file — is not something an assertion on a synthetic scene
 * would prove anyway.
 *
 * **Camera permission** is granted for this class. The rationale, which only shows when it is
 * not, lives in [BillScannerPermissionEndToEndTest].
 *
 * **Before running:** clear the app's data or uninstall. The local database still has no
 * migrations, so an install carrying an older one has no `fuel_fills` table at all.
 */
@RunWith(AndroidJUnit4::class)
class BillScannerEndToEndTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    /**
     * Put the device in the state each test needs **before** the activity launches.
     *
     * Where the app opens is decided once per launch and then held in saved state, so a
     * `@Before` is too late — the rule has already drawn a first frame against the previous
     * test's data. [DeviceState] runs outside the compose rule, so the seed lands first.
     */
    @get:Rule
    val chain: RuleChain = RuleChain
        // The runner pins every key to its compiled default, and the bill check ships
        // closed. This suite drives it, so it says so in its own file.
        .outerRule(PinnedConfig("bill_check_enabled", value = "true", compiledDefault = "false"))
        .around(DeviceState { startOnASetUpDeviceWithNothingScanned() })
        .around(rule)

    @get:Rule
    val cameraPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    /**
     * Start every test on a set-up device with nothing scanned yet, and with the shipped
     * scanning behaviour in place.
     *
     * The defaults are reinstalled per test because Koin's overrides are process-scoped: an
     * extractor one test put in front of the port would otherwise still be there for the next.
     */
    private fun startOnASetUpDeviceWithNothingScanned() {
        Intents.init()
        resetScanner()
        seedOnboardedOwner()
        seedCapturedPhoto()
        installDefaultScanning()
    }

    @After
    fun tearDown() = Intents.release()

    /* ------------------------------ The viewfinder ------------------------------ */

    @Test
    fun theScanButtonOpensTheViewfinderOnBills() {
        rule.openScanner()

        rule.awaitText(ScanCopy.SCAN_TITLE_BILL)
        // Same race as the document mode: the edge detector may already have replaced the
        // align copy by the time this runs, and that is the app working, not failing.
        rule.awaitGuidance(ScanCopy.ALIGN_BILL, ScanCopy.EDGES_DETECTED, ScanCopy.EDGES_PINNED)
        // The two paper modes are always offered, and the fallback to typing it in is
        // always there.
        rule.onNodeWithText(ScanCopy.MODE_BILL).assertIsDisplayed()
        rule.onNodeWithText(ScanCopy.MODE_DOCUMENT).assertIsDisplayed()
        rule.onNodeWithText(ScanCopy.MANUAL).assertIsDisplayed()
    }

    @Test
    fun theScannerOffersThePumpDisplay() {
        // The one capture channel that works wherever the owner is: a pump shows what it
        // dispensed whether they paid by card, by phone or in cash.
        rule.openScanner()
        rule.awaitText(ScanCopy.SCAN_TITLE_BILL)

        rule.onNodeWithText(ScanCopy.MODE_PUMP).assertIsDisplayed()
    }

    @Test
    fun theQuotaPillShowsWhatIsLeftOnTheFreePlan() {
        installScanAllowance(ScanLimit.UpTo(max = 3, used = 1))
        rule.openScanner()

        rule.awaitText(ScanCopy.quota(remaining = 2, total = 3))
    }

    @Test
    fun switchingTargetRetitlesTheScreenAndChangesTheGuidance() {
        rule.openScanner()
        rule.awaitText(ScanCopy.SCAN_TITLE_BILL)

        rule.selectScanMode(ScanCopy.MODE_DOCUMENT)
        rule.awaitText(ScanCopy.SCAN_TITLE_DOCUMENT)
        // Either the align copy or what the edge detector replaced it with — both are this
        // mode's guidance, and which one is up races a live camera. See awaitGuidance.
        rule.awaitGuidance(ScanCopy.ALIGN_DOCUMENT, ScanCopy.EDGES_DETECTED, ScanCopy.EDGES_PINNED)

        rule.selectScanMode(ScanCopy.MODE_PUMP)
        rule.awaitText(ScanCopy.SCAN_TITLE_PUMP)
        // No edge detection on a pump display, so this one has a single right answer.
        rule.awaitGuidance(ScanCopy.ALIGN_PUMP)
        // Reading a pump is on-device, so it spends no scan and the quota pill has nothing
        // true to say.
        rule.awaitGone(ScanCopy.quota(remaining = 3, total = 3))
    }

    @Test
    fun theTorchIsOfferedOnceTheCameraIsAllowed() {
        rule.openScanner()
        rule.awaitText(ScanCopy.SCAN_TITLE_BILL)

        // A workshop counter is badly lit and a thermal bill is low-contrast to begin with.
        rule.awaitLabel(ScanCopy.TORCH_ON)
    }

    /* ------------------------------ Reading a bill ------------------------------ */

    @Test
    fun aReadableBillFillsTheReviewFieldsFromWhatWasRead() {
        installBillExtractor(readableBill())
        rule.openScanner()
        rule.openBillReview()

        rule.awaitText(ScanCopy.REVIEW_TITLE)
        rule.awaitGone(ScanCopy.READING)
        rule.onNodeWithText(ScanFixtures.WORKSHOP).assertIsDisplayed()
        rule.onNodeWithText(ScanFixtures.OIL_CHANGE).assertIsDisplayed()
        rule.onNodeWithText(ScanCopy.REVIEW_SAVE).assertIsEnabled()
    }

    @Test
    fun aHandwrittenBillIsFlaggedForCheckingHoweverSureTheModelIs() {
        // The PRD's honesty rule: handwriting is never auto-populated, whatever score comes
        // back with it.
        installBillExtractor(readableBill(confidence = 62, billType = BillType.HANDWRITTEN))
        rule.openScanner()
        rule.openBillReview()

        rule.awaitText(ScanCopy.REVIEW_LOW_CONFIDENCE)
    }

    @Test
    fun aBillWithNoReadableDateCannotBeSavedUntilOneIsGiven() {
        // Nothing is invented on the owner's behalf: the field reads "Not set" and the save
        // waits, rather than defaulting to today and quietly filing a wrong date.
        installBillExtractor(billWithoutADate())
        rule.openScanner()
        rule.openBillReview()

        rule.awaitText(ScanCopy.REVIEW_TITLE)
        rule.awaitText(ScanCopy.REVIEW_NOT_SET)
        rule.onNodeWithText(ScanCopy.REVIEW_SAVE).assertIsNotEnabled()
    }

    @Test
    fun anUnavailableScannerSaysSoInsteadOfShowingABlankForm() {
        // This is what every scan does today, because no extractor is configured. Before the
        // review screen was wired to its state it showed an empty form and no explanation.
        rule.openScanner()
        rule.openBillReview()

        rule.awaitText(ScanCopy.REVIEW_TITLE)
        rule.awaitText(ScanCopy.ERROR_SCAN_UNAVAILABLE)
    }

    @Test
    fun aBlurryPhotoInvitesAnotherTry() {
        installBillExtractor(DomainError.ScanUnreadable.left())
        rule.openScanner()
        rule.openBillReview()

        rule.awaitText(ScanCopy.ERROR_UNREADABLE)
    }

    @Test
    fun aSpentQuotaOffersProRatherThanARetake() {
        installScanAllowance(ScanLimit.UpTo(max = 3, used = 3))
        installBillExtractor(readableBill())
        rule.openScanner()
        rule.openBillReview()

        rule.awaitText(ScanCopy.quotaSpent(limit = 3))
    }

    @Test
    fun confirmingABillWritesItToTheServiceLogAndLandsOnTheBillCheck() {
        installBillExtractor(readableBill())
        rule.openScanner()
        rule.openBillReview()
        rule.awaitText(ScanCopy.REVIEW_TITLE)
        rule.awaitGone(ScanCopy.READING)

        rule.onNodeWithText(ScanCopy.REVIEW_SAVE).performClick()

        // The entry is written first; the bill check is what the owner lands on, and it can
        // only speak about an entry that exists. Asserted by name, not by the review screen
        // going away — with `bill_check_enabled` closed the same tap lands on the
        // saved-success screen, and this test used to pass either way.
        rule.awaitGone(ScanCopy.REVIEW_TITLE, timeoutMillis = 10_000L)
        assertEquals(1, scannedLogCount())
        assertEquals(ScanFixtures.WORKSHOP, scannedLogWorkshop())
        rule.awaitTextContaining(BILL_CHECK_LANDING, timeoutMillis = 10_000L)
    }

    /* ------------------------------ Picking from the gallery ------------------------------ */

    /**
     * The gallery button used to navigate to the bill review with no photo at all, in every
     * mode. These two prove it now copies the picked picture into app storage and sends it
     * where the chosen mode says.
     */
    @Test
    fun aPickedBillPictureIsReadAsABill() {
        installBillExtractor(readableBill())
        stubPickedImage()
        rule.openScanner()
        rule.awaitText(ScanCopy.SCAN_TITLE_BILL)

        rule.pickFromGallery()

        rule.awaitText(ScanCopy.REVIEW_TITLE)
        rule.awaitGone(ScanCopy.READING)
        rule.onNodeWithText(ScanFixtures.WORKSHOP).assertIsDisplayed()
    }

    @Test
    fun aPickedPaperIsReadAsADocument() {
        installDocumentExtractor(readableDocument())
        stubPickedImage()
        rule.openScanner()
        rule.selectScanMode(ScanCopy.MODE_DOCUMENT)
        rule.awaitText(ScanCopy.SCAN_TITLE_DOCUMENT)

        rule.pickFromGallery()

        rule.awaitText(ScanCopy.DOC_TITLE)
        rule.awaitGone(ScanCopy.READING)
        rule.onNodeWithText(ScanFixtures.INSURER).assertIsDisplayed()
    }

    /* ------------------------------ Reading a document ------------------------------ */

    @Test
    fun aReadablePolicyIsConfirmedAndFiledInTheVault() {
        installDocumentExtractor(readableDocument())
        rule.openScanner()
        rule.openDocumentReview()

        rule.awaitText(ScanCopy.DOC_TITLE)
        rule.awaitGone(ScanCopy.READING)
        rule.onNodeWithText(ScanFixtures.INSURER).assertIsDisplayed()

        rule.onNodeWithText(ScanCopy.DOC_SAVE).performClick()

        rule.waitUntil(10_000L) { documentCount() == 1 }
        assertEquals(1, documentCount())
    }

    @Test
    fun aPaperWithNoExpiryCannotBeFiledUntilOneIsGiven() {
        // A document with no expiry produces no reminder, which is most of what the vault is
        // for — so the screen asks rather than filing something that can never help.
        installDocumentExtractor(readableDocument(expiresOn = null))
        rule.openScanner()
        rule.openDocumentReview()

        rule.awaitText(ScanCopy.DOC_TITLE)
        rule.awaitText(ScanCopy.DOC_EXPIRY_REQUIRED)
        rule.onNodeWithText(ScanCopy.DOC_SAVE).assertIsNotEnabled()
        assertEquals(0, documentCount())
    }
}

/** Whatever the check finds — the point is that the scan landed on it rather than elsewhere. */
private const val BILL_CHECK_LANDING = "worth asking about"

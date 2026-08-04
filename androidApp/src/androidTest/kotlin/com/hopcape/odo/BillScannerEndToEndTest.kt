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

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val cameraPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    /**
     * Start every test on a set-up device with nothing scanned yet, and with the shipped
     * scanning behaviour in place.
     *
     * The defaults are reinstalled per test because Koin's overrides are process-scoped: an
     * extractor one test put in front of the port would otherwise still be there for the next.
     *
     * The activity is recreated because the rule launches it before this runs, so it may
     * already have read a previous test's data.
     */
    @Before
    fun startOnASetUpDeviceWithNothingScanned() {
        Intents.init()
        resetScanner()
        seedOnboardedOwner()
        seedCapturedPhoto()
        installDefaultScanning()
        rule.activityRule.scenario.recreate()
    }

    @After
    fun tearDown() = Intents.release()

    /* ------------------------------ The viewfinder ------------------------------ */

    @Test
    fun theScanButtonOpensTheViewfinderOnBills() {
        rule.openScanner()

        rule.awaitText(ScanCopy.SCAN_TITLE_BILL)
        rule.onNodeWithText(ScanCopy.ALIGN_BILL).assertIsDisplayed()
        // All three targets are offered, and the fallback to typing it in is always there.
        rule.onNodeWithText(ScanCopy.MODE_BILL).assertIsDisplayed()
        rule.onNodeWithText(ScanCopy.MODE_DOCUMENT).assertIsDisplayed()
        rule.onNodeWithText(ScanCopy.MODE_QR).assertIsDisplayed()
        rule.onNodeWithText(ScanCopy.MANUAL).assertIsDisplayed()
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
        rule.onNodeWithText(ScanCopy.ALIGN_DOCUMENT).assertIsDisplayed()

        rule.selectScanMode(ScanCopy.MODE_QR)
        rule.awaitText(ScanCopy.SCAN_TITLE_QR)
        rule.onNodeWithText(ScanCopy.ALIGN_QR).assertIsDisplayed()
        // A payment code spends no scan, so the quota pill has nothing true to say.
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
    fun confirmingABillWritesItToTheServiceLogAndRunsTheFairnessCheck() {
        installBillExtractor(readableBill())
        rule.openScanner()
        rule.openBillReview()
        rule.awaitText(ScanCopy.REVIEW_TITLE)
        rule.awaitGone(ScanCopy.READING)

        rule.onNodeWithText(ScanCopy.REVIEW_SAVE).performClick()

        // The entry is written first; the fairness report is what the owner lands on, and it
        // can only offer "report overcharge" against an entry that exists.
        rule.awaitGone(ScanCopy.REVIEW_TITLE, timeoutMillis = 10_000L)
        assertEquals(1, scannedLogCount())
        assertEquals(ScanFixtures.WORKSHOP, scannedLogWorkshop())
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

    /* ------------------------------ Scanning to pay ------------------------------ */

    @Test
    fun aPaymentCodeShowsWhoIsBeingPaidAndTheAddressItGoesTo() {
        rule.openScanner()
        rule.openPayAtPump(ScanFixtures.QR_WITH_AMOUNT)

        rule.awaitText(ScanCopy.PAY_TITLE)
        rule.onNodeWithText(ScanFixtures.PAYEE).assertIsDisplayed()
        // The name in a code is a claim; the address is what decides where money goes, so
        // both are shown.
        rule.onNodeWithText(ScanCopy.PAY_VPA_LABEL).assertIsDisplayed()
        rule.onNodeWithText(ScanFixtures.VPA).assertIsDisplayed()
        rule.onNodeWithText(ScanCopy.PAY_ACTION).assertIsEnabled()
    }

    @Test
    fun aCodeThatNamesNoSumWaitsForOneBeforeItCanPay() {
        // Most fuel pumps leave the amount to the payer.
        rule.openScanner()
        rule.openPayAtPump(ScanFixtures.QR_NO_AMOUNT)

        rule.awaitText(ScanCopy.PAY_TITLE)
        rule.onNodeWithText(ScanCopy.PAY_ACTION).assertIsNotEnabled()

        rule.typeAmount("2000")
        rule.onNodeWithText(ScanCopy.PAY_ACTION).assertIsEnabled()
    }

    @Test
    fun aCodeThatIsNotAUpiLinkIsRefusedRatherThanGuessedAt() {
        // An EMVCo/Bharat QR encodes a payment in a different grammar; misreading one would
        // send money somewhere else.
        rule.openScanner()
        rule.openPayAtPump(ScanFixtures.QR_UNSUPPORTED)

        rule.awaitText(ScanCopy.ERROR_UNSUPPORTED_QR)
        rule.onNodeWithText(ScanCopy.PAY_ACTION).assertIsNotEnabled()
    }

    @Test
    fun aSettledPaymentRecordsTheFillTheOwnerConfirms() {
        stubUpiPayment(ScanFixtures.UPI_SUCCESS)
        rule.openScanner()
        rule.openPayAtPump(ScanFixtures.QR_WITH_AMOUNT)
        rule.awaitText(ScanCopy.PAY_TITLE)

        rule.onNodeWithText(ScanCopy.PAY_ACTION).performClick()

        // Only a confirmed payment opens the logging step.
        rule.awaitText(ScanCopy.FILL_TITLE)
        rule.typeFill(odometer = ScanFixtures.FILL_ODOMETER_KM.toString(), litres = ScanFixtures.FILL_LITRES)
        rule.onNodeWithText(ScanCopy.FILL_SAVE).performClick()

        rule.waitUntil(10_000L) { fuelFillCount() == 1 }
        val fill = singleFuelFill()
        assertEquals(ScanFixtures.FILL_ODOMETER_KM, fill.odometerKm)
        assertEquals(ScanFixtures.FILL_QUANTITY_MILLI, fill.quantityMilli)
        assertEquals(200_000L, fill.amountPaise)
        assertEquals("UPI", fill.paidVia)
        // The bank's reference is what makes a disputed fill checkable.
        assertEquals(ScanFixtures.TXN_REF, fill.transactionRef)
    }

    @Test
    fun aPendingPaymentRecordsNothingAndSaysWhy() {
        // The money may still move. A fill written here would be a fabricated entry in a
        // history the app promises is trustworthy.
        stubUpiPayment(ScanFixtures.UPI_PENDING)
        rule.openScanner()
        rule.openPayAtPump(ScanFixtures.QR_WITH_AMOUNT)
        rule.awaitText(ScanCopy.PAY_TITLE)

        rule.onNodeWithText(ScanCopy.PAY_ACTION).performClick()

        rule.awaitText(ScanCopy.ERROR_PAYMENT_PENDING)
        rule.onNodeWithText(ScanCopy.FILL_TITLE).assertDoesNotExist()
        assertEquals(0, fuelFillCount())
    }

    @Test
    fun aCancelledPaymentRecordsNothingAndSaysNothingWasCharged() {
        stubCancelledUpiPayment()
        rule.openScanner()
        rule.openPayAtPump(ScanFixtures.QR_WITH_AMOUNT)
        rule.awaitText(ScanCopy.PAY_TITLE)

        rule.onNodeWithText(ScanCopy.PAY_ACTION).performClick()

        rule.awaitText(ScanCopy.ERROR_PAYMENT_CANCELLED)
        assertEquals(0, fuelFillCount())
    }

    @Test
    fun aFillWithNoOdometerCannotBeSaved() {
        // Odometer is mandatory on every entry Odo keeps: two readings are what turn fills
        // into a measured mileage, and one without is only a receipt.
        stubUpiPayment(ScanFixtures.UPI_SUCCESS)
        rule.openScanner()
        rule.openPayAtPump(ScanFixtures.QR_WITH_AMOUNT)
        rule.awaitText(ScanCopy.PAY_TITLE)
        rule.onNodeWithText(ScanCopy.PAY_ACTION).performClick()
        rule.awaitText(ScanCopy.FILL_TITLE)

        rule.typeFill(odometer = "", litres = ScanFixtures.FILL_LITRES)

        rule.onNodeWithText(ScanCopy.FILL_SAVE).assertIsNotEnabled()
        assertEquals(0, fuelFillCount())
    }
}

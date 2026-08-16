package com.hopcape.odo

import android.Manifest
import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.ContextCompat
import android.net.Uri
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.db.SqlDriver
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.scan.BillExtractor
import com.hopcape.odo.core.domain.scan.DocumentExtractor
import com.hopcape.odo.core.domain.scan.entitlement.ScanAllowance
import com.hopcape.odo.core.domain.scan.entitlement.ScanLimit
import com.hopcape.odo.core.domain.scan.model.BillType
import com.hopcape.odo.core.domain.scan.model.ExtractedBill
import com.hopcape.odo.core.domain.scan.model.ExtractedDocument
import com.hopcape.odo.core.domain.scan.model.ExtractedLineItem
import com.hopcape.odo.core.domain.scan.model.ExtractionConfidence
import com.hopcape.odo.core.domain.scan.model.ScanId
import com.hopcape.odo.core.domain.scan.model.ScannedImage
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.ScanTarget
import com.hopcape.odo.core.navigation.navigateTo
import kotlinx.datetime.LocalDate
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import java.io.File

/**
 * The words the bill scanner puts on screen, mirrored from its `strings.xml`.
 *
 * Copied rather than read, for the same reason as [VaultCopy]: Compose Resources keeps a
 * feature's generated `Res` internal to its own module. Asserting on the copy an owner
 * actually reads is the point.
 *
 * Escaped apostrophes in the resource (`isn\'t`) are ASCII, not `’`. Both appear in this
 * feature's strings, so each constant is taken from the resource as written.
 */
internal object ScanCopy {

    /* Camera permission rationale. */
    const val CAMERA_TITLE = "Odo needs your camera to read things"
    const val CAMERA_SUBTITLE = "Everything Odo checks starts as a photo. Grant once and both work."
    const val CAMERA_BILLS = "Service bills"
    const val CAMERA_PAPERS = "Insurance, PUC & RC"
    const val CAMERA_NO_BACKGROUND = "No photos or video in the background"
    const val CAMERA_ONLY_ON_SCAN = "Only when you tap Scan"
    const val CAMERA_ALLOW = "Allow camera"
    const val CAMERA_NOT_NOW = "Not now"
    const val CLOSE_LABEL = "Close"
    const val CAMERA_BLOCKED_SUBTITLE =
        "The camera is switched off for Odo. Turn it back on in settings and scanning works again."

    /* Viewfinder. */
    const val SCAN_TITLE_BILL = "Scan bill"
    const val SCAN_TITLE_DOCUMENT = "Scan document"
    const val SCAN_TITLE_PUMP = "Scan the pump display"
    const val ALIGN_BILL = "Align the bill inside the frame"
    const val ALIGN_DOCUMENT = "Fit the whole paper inside the frame"
    const val ALIGN_PUMP = "Hold steady on the pump’s display"

    /*
     * What the same line says once the live edge detector has found a paper. The align copy
     * above is only what is shown *until* then, which is why an assertion on a paper mode has
     * to accept either — see [awaitGuidance].
     */
    const val EDGES_DETECTED = "Bill detected — tap to lock edges"
    const val EDGES_PINNED = "Edges locked — capture, or tap to unlock"
    const val MODE_BILL = "Bill"
    const val MODE_DOCUMENT = "Document"
    const val MODE_PUMP = "Pump"
    const val MANUAL = "Manual"
    const val TORCH_ON = "Turn the light on"
    const val GALLERY = "Choose from gallery"

    /* Review. */
    const val REVIEW_TITLE = "Review details"
    const val REVIEW_SAVE = "Save & check fairness"
    const val REVIEW_LOW_CONFIDENCE = "≈ LOW CONFIDENCE — PLEASE CHECK"
    const val REVIEW_NOT_SET = "Not set"
    const val READING = "Reading…"

    /* Document confirm. */
    const val DOC_TITLE = "Check the document"
    const val DOC_SAVE = "Save to vault"
    const val DOC_EXPIRY_REQUIRED = "Add the expiry date so Odo can remind you."

    /** The raised Scan action in the bottom bar — the app's primary call to action. */
    const val SCAN_ACTION = "Scan"

    /* Failures. */
    const val ERROR_SCAN_UNAVAILABLE =
        "Scanning isn’t available right now. Enter the details yourself instead."
    const val ERROR_UNREADABLE = "That photo was too blurry to read. Try again in better light."
    const val PUMP_UNREADABLE = "Those numbers didn’t read. Try again, or enter them yourself."

    /** `"%1$d of %2$d free"` — the quota pill. */
    fun quota(remaining: Int, total: Int) = "$remaining of $total free"

    fun quotaSpent(limit: Int) = "You’ve used all $limit free scans this month. Go Pro for unlimited."
}

/** What the fakes return, and the rows the assertions look for. */
internal object ScanFixtures {

    /** The photo key every navigation into a review step carries. */
    const val PHOTO_KEY = "scans/e2e-photo.jpg"

    const val WORKSHOP = "Sharma Motors"
    const val OIL_CHANGE = "Oil change"
    const val LABOUR = "Labour"
    const val ODOMETER_KM = 41_500
    const val TOTAL_PAISE = 355_000L

    /** Dated in the past, so the entry's future-date guard cannot reject it. */
    val SERVICE_DATE: LocalDate = LocalDate(2026, 6, 12)

    const val INSURER = "SafeDrive comprehensive"
    val DOC_EXPIRY: LocalDate = LocalDate(2027, 3, 14)

}

private typealias ScanTestRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/* ------------------------------ Faking the extraction ------------------------------ */

/**
 * Put an extractor in front of the scan use cases.
 *
 * The shipped binding refuses every scan (`UnconfiguredBillExtractor`) because the Edge
 * Function that reads a bill does not exist yet, so without this there is no readable-bill
 * path to drive at all. The port exists for exactly this — the TDD's rule is that the AI is
 * always behind a seam with a fake, so the app is testable without spending a token.
 *
 * Everything above the port is the real thing: the use cases, the confidence gate, the
 * ViewModels, the navigation and the repositories that store what comes out.
 *
 * Safe to call from `@Before` because both scan use cases are Koin `factory` definitions —
 * they resolve the extractor when a ViewModel is built, which happens when the destination is
 * composed, after this has run.
 */
internal fun installBillExtractor(result: Either<DomainError, ExtractedBill>) {
    GlobalContext.get().loadModules(
        listOf(module { single<BillExtractor> { BillExtractor { result } } }),
        allowOverride = true,
    )
}

internal fun installDocumentExtractor(result: Either<DomainError, ExtractedDocument>) {
    GlobalContext.get().loadModules(
        listOf(module { single<DocumentExtractor> { DocumentExtractor { result } } }),
        allowOverride = true,
    )
}

/** Decide how many scans the owner's plan has left. */
internal fun installScanAllowance(limit: ScanLimit) {
    GlobalContext.get().loadModules(
        listOf(module { single<ScanAllowance> { ScanAllowance { limit } } }),
        allowOverride = true,
    )
}

/** Restore the shipped behaviour: three free scans, and an extractor that refuses. */
internal fun installDefaultScanning() {
    installScanAllowance(ScanLimit.UpTo(max = 3, used = 0))
    installBillExtractor(DomainError.ScanUnavailable.left())
    installDocumentExtractor(DomainError.ScanUnavailable.left())
}

/** A printed bill that read cleanly — three lines, a date, an odometer and a workshop. */
internal fun readableBill(
    confidence: Int = 92,
    billType: BillType = BillType.PRINTED_THERMAL,
): Either<DomainError, ExtractedBill> = ExtractedBill(
    scanId = ScanId("e2e-scan"),
    billType = billType,
    confidence = ExtractionConfidence.of(confidence).getOrNull()!!,
    serviceDate = ScanFixtures.SERVICE_DATE,
    odometerKm = ScanFixtures.ODOMETER_KM,
    workshopName = ScanFixtures.WORKSHOP,
    lineItems = listOf(
        ExtractedLineItem(ScanFixtures.OIL_CHANGE, paise(280_000)),
        ExtractedLineItem(ScanFixtures.LABOUR, paise(75_000), needsCheck = confidence < 85),
    ),
    total = paise(ScanFixtures.TOTAL_PAISE),
).right()

/** A bill whose date could not be read — the case the review step must not invent a value for. */
internal fun billWithoutADate(): Either<DomainError, ExtractedBill> =
    (readableBill().getOrNull()!!.copy(serviceDate = null)).right()

/** A policy that read cleanly, expiry included. */
internal fun readableDocument(
    expiresOn: LocalDate? = ScanFixtures.DOC_EXPIRY,
): Either<DomainError, ExtractedDocument> = ExtractedDocument(
    scanId = ScanId("e2e-scan"),
    confidence = ExtractionConfidence.of(91).getOrNull()!!,
    documentType = DocumentType.INSURANCE,
    suggestedTitle = ScanFixtures.INSURER,
    expiresOn = expiresOn,
).right()

private fun paise(value: Long): Amount = Amount.of(value).getOrNull()!!

/* ------------------------------ Navigation ------------------------------ */

private fun navigator(): NavigationManager = GlobalContext.get().get()

/** The first frame waits on the start-destination read, and on a cold start on the seed. */
private const val SCAN_START_UP_TIMEOUT_MILLIS = 20_000L

/**
 * Open the scanner the way an owner does — the raised Scan button in the bottom bar.
 *
 * Driving that path rather than jumping to the key is part of what these prove: the button is
 * the app's primary call to action, and it hands the scanner no car, so the scanner has to
 * name one itself through the active-car port.
 */
internal fun ScanTestRule.openScanner() {
    awaitLabel(ScanCopy.SCAN_ACTION, SCAN_START_UP_TIMEOUT_MILLIS)
    onNodeWithLabel(ScanCopy.SCAN_ACTION).performClick()
}

/**
 * Jump straight to a step that normally follows a capture.
 *
 * The shutter is the one part of this flow an instrumented test cannot drive: CameraX needs a
 * camera, an emulator's is a synthetic scene, and a capture takes seconds it would spend on
 * every test. Everything after the photo — the read, the review, the writes — is what these
 * tests are for, so they enter at the key the capture would have navigated to, with the photo
 * key it would have carried.
 */
internal fun ScanTestRule.openBillReview(photoKey: String? = ScanFixtures.PHOTO_KEY) {
    navigator().navigateTo(OdoDestination.BillScanner.Review(photoKey))
}

internal fun ScanTestRule.openDocumentReview(photoKey: String = ScanFixtures.PHOTO_KEY) {
    navigator().navigateTo(OdoDestination.BillScanner.DocumentReview(photoKey))
}

/** Switch what the viewfinder is pointed at. */
internal fun ScanTestRule.selectScanMode(label: String) {
    onNodeWithText(label).performClick()
}

/**
 * Wait until the line under the frame says one of [acceptable].
 *
 * Polling rather than a bare `assertIsDisplayed`, and a set rather than one string, because
 * that line has two reasons to disagree with a test that names a single value:
 *
 * - It is the *live* guidance. In a paper mode it reads "align…" only until the edge detector
 *   finds a quad, and then becomes "detected"/"pinned". On a device with a real camera
 *   pointed at a real scene, which of those is on screen is a race the test cannot win, and
 *   both are correct app behaviour.
 * - It sits in the viewfinder, not the top bar, so it settles a frame or two after the title
 *   the caller has already waited for.
 *
 * Naming every acceptable value keeps the assertion meaningful — a mode switch that changed
 * nothing, or landed on another mode's copy, still fails.
 */
internal fun ScanTestRule.awaitGuidance(vararg acceptable: String, timeoutMillis: Long = 5_000L) {
    waitUntil(timeoutMillis) { acceptable.any { textCount(it) > 0 } }
}

/** The target a scanner opened from the bottom bar starts on. */
internal val DEFAULT_SCAN_TARGET = ScanTarget.Bill

/* ------------------------------ Database ------------------------------ */

private fun scanDriver(): SqlDriver = GlobalContext.get().get()

/**
 * Empty everything these tests write, and the captured photos with it.
 *
 * [resetOwnerData] covers the profile, the car and the service log; documents and fuel fills
 * are this flow's own tables, and the scan photos live outside the database.
 */
internal fun resetScanner() {
    resetOwnerData()
    scanDriver().execute(null, "DELETE FROM documents", 0)
    scanDriver().execute(null, "DELETE FROM fuel_fills", 0)
    scanDriver().notifyListeners("documents")
    scanDriver().notifyListeners("fuel_fills")
    File(appFilesDir(), "scans").deleteRecursively()
}

/** How many service entries the scanner has written. */
internal fun scannedLogCount(): Int =
    countRows("SELECT COUNT(*) FROM service_logs WHERE source = 'SCANNED' AND deleted_at IS NULL")

/** How many documents are on file. */
internal fun documentCount(): Int =
    countRows("SELECT COUNT(*) FROM documents WHERE deleted_at IS NULL")

/** The workshop name on the single scanned service entry. */
internal fun scannedLogWorkshop(): String? = scanDriver().executeQuery(
    identifier = null,
    sql = "SELECT workshop_name FROM service_logs WHERE source = 'SCANNED' AND deleted_at IS NULL",
    mapper = { cursor ->
        cursor.next()
        app.cash.sqldelight.db.QueryResult.Value(cursor.getString(0))
    },
    parameters = 0,
).value

private fun countRows(sql: String): Int = scanDriver().executeQuery(
    identifier = null,
    sql = sql,
    mapper = { cursor ->
        cursor.next()
        app.cash.sqldelight.db.QueryResult.Value(cursor.getLong(0)!!.toInt())
    },
    parameters = 0,
).value

/**
 * Write the file a seeded photo key points at.
 *
 * The review screens read it back to show the owner what they photographed; without bytes
 * behind the key the image simply does not draw, which is a different screen from the one
 * under test.
 */
/**
 * Answer the gallery picker with a picture, the way an owner choosing one would.
 *
 * The bytes only have to decode — nothing in these tests reads the pixels. What is being
 * proved is the path: the picker's borrowed handle is copied into app storage and the
 * scanner routes the stored key by what the owner came to scan.
 */
internal fun stubPickedImage(): Uri {
    val file = File(scanAppContext().cacheDir, "picked-scan.jpg")
    file.writeBytes(JPEG_BYTES)
    val uri = Uri.fromFile(file)
    intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
        .respondWith(ActivityResult(Activity.RESULT_OK, Intent().setData(uri)))
    return uri
}

/** Tap the gallery affordance on the viewfinder. */
internal fun ScanTestRule.pickFromGallery() {
    onNodeWithLabel(ScanCopy.GALLERY).performClick()
}

internal fun seedCapturedPhoto(storageKey: String = ScanFixtures.PHOTO_KEY) {
    val file = File(appFilesDir(), storageKey)
    file.parentFile?.mkdirs()
    file.writeBytes(JPEG_BYTES)
}

private fun scanAppContext() = InstrumentationRegistry.getInstrumentation().targetContext

private fun appFilesDir() = scanAppContext().filesDir

/** A 1×1 JPEG. Nothing reads the pixels; it only has to decode. */
private val JPEG_BYTES = byteArrayOf(
    0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xDB.toByte(), 0x00, 0x43, 0x00,
) + ByteArray(64) { 0x08 } + byteArrayOf(0xFF.toByte(), 0xD9.toByte())

/* ------------------------------ Permission state ------------------------------ */

/**
 * Whether this install already holds the camera permission.
 *
 * The rationale only shows when it does not, and a grant survives for the life of the install
 * — including one made by another test class's `GrantPermissionRule`. The tests that need it
 * ungranted check this and skip rather than fail, because a permission cannot be taken back
 * from inside the process it belongs to: `pm revoke` kills the app, and in an instrumented run
 * the app is the test.
 */
internal fun cameraPermissionGranted(): Boolean =
    ContextCompat.checkSelfPermission(
        InstrumentationRegistry.getInstrumentation().targetContext,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED

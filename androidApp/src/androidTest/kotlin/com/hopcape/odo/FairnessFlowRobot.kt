package com.hopcape.odo

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.db.SqlDriver
import com.hopcape.odo.feature.servicelog.presentation.ServiceLogTestTags
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.feature.fairnesscheck.presentation.report.FairnessTestTags
import com.hopcape.odo.core.navigation.FairnessLineInput
import com.hopcape.odo.core.navigation.NavigationManager
import com.hopcape.odo.core.navigation.OdoDestination
import com.hopcape.odo.core.navigation.navigateTo
import org.koin.core.context.GlobalContext
import java.io.File

/**
 * The words the fairness check puts on screen, mirrored from its `strings.xml`.
 *
 * Copied rather than read: Compose Resources keeps a feature's generated `Res` internal to
 * its own module, so `:androidApp` cannot reach it. Asserting on the copy an owner actually
 * reads is the point — these tests exist because the screen used to say "this looks fair"
 * when it knew nothing at all.
 */
internal object FairnessCopy {
    const val TITLE = "Fairness check"

    const val OVER_LABEL = "OVERCHARGE CAUGHT"
    const val FAIR_LABEL = "FAIR PRICE"
    const val THIN_LABEL = "NOT ENOUGH DATA"
    const val NO_DATA_LABEL = "NO CITY DATA"

    const val THIN_HEADLINE = "Can’t call this one yet"
    const val NO_DATA_HEADLINE = "Nothing to compare against"

    const val REPORT = "Report overcharge"
    const val DONE = "Done"

    const val NO_CITY_TITLE = "Which city are you in?"
    const val SET_CITY = "Set your city"

    /* The service-log detail actions this flow starts from. */
    const val CHECK_FAIRNESS = "Check fairness"
    const val ATTACH_BILL = "Add a bill to verify"

    /* The overcharge report screen the amber verdict leads to. */
    const val REPORT_QUESTION = "What went wrong?"

    /* Where the no-city dead end sends the owner. */
    const val PROFILE_TITLE = "Profile"

    /* What attaching a bill changes on the entry itself. */
    const val VERIFIED_BADGE = "Verified"
    const val DETAIL_FAIR_HEADLINE = "Fair price vs the city average"
}

/**
 * Entries chosen to land on each of the four outcomes, against the benchmark table the app
 * actually ships (`FakeFairnessRemoteDataSource`): brakes average Rs. 3,400 on 24 bills, AC
 * average Rs. 2,600 on **3** bills, and nothing at all for electrical work.
 *
 * That table is the fixture. If the canned benchmarks move, these amounts have to move with
 * them — which is the point: the four states are only reachable because the pool has those
 * three shapes in it.
 */
internal object FairnessFixtures {
    /** The city the seeded profile carries, and the one the benchmark table is keyed on. */
    const val CITY = "Pune"

    /** Rs. 5,000 against a Rs. 3,400 average, on a sample big enough to say so. */
    const val OVER_ID = "fair-over"
    const val OVER_WORKSHOP = "AutoCare Pune"
    const val OVER_PAISE = 500_000L

    /** Rs. 3,400 — exactly the average, so inside the fair band. */
    const val FAIR_ID = "fair-fair"
    const val FAIR_WORKSHOP = "Sharma Motors"
    const val FAIR_PAISE = 340_000L

    /** AC work: three data points in the city, which is under the confidence floor. */
    const val THIN_ID = "fair-thin"
    const val THIN_WORKSHOP = "Cool Air Garage"
    const val THIN_PAISE = 340_000L

    /** Electrical work: the pool has never seen it. */
    const val NO_DATA_ID = "fair-nodata"
    const val NO_DATA_WORKSHOP = "Bombay Auto Electric"
    const val NO_DATA_PAISE = 260_000L

    /** No bill attached, so nothing to judge and nothing to share. */
    const val SELF_REPORTED_ID = "fair-self"
    const val SELF_REPORTED_WORKSHOP = "Speed Garage"
    const val SELF_REPORTED_PAISE = 300_000L
}

private typealias FairnessTestRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

private fun fairnessDriver(): SqlDriver = GlobalContext.get().get()

/**
 * Enter the report the way a scan leaves it: the viewfinder and the confirm step behind it.
 *
 * Pushed through the navigator rather than driven, for the same reason the scanner's own
 * tests do it — the shutter needs a camera an emulator does not have. What is under test
 * here is what "Done" does with the steps a scan leaves on the stack, not the capture.
 */
internal fun FairnessTestRule.openReportAsIfScanned() {
    val navigation: NavigationManager = GlobalContext.get().get()
    navigation.navigateTo(OdoDestination.BillScanner.Capture())
    navigation.navigateTo(OdoDestination.BillScanner.Review(ScanFixtures.PHOTO_KEY))
    navigation.navigateTo(
        OdoDestination.Fairness(
            items = listOf(
                FairnessLineInput(
                    label = FairnessFixtures.OVER_WORKSHOP,
                    category = ServiceCategory.BRAKES.name,
                    amountPaise = FairnessFixtures.OVER_PAISE,
                ),
            ),
        ),
    )
    awaitFairnessTag(FairnessTestTags.DONE_BUTTON)
}

/**
 * Seed the four entries, all bill-backed and none carrying a stored verdict.
 *
 * No stored verdict is deliberate: the detail screen offers "Check fairness" precisely when
 * an entry has been verified but never judged, and running the check live is what puts the
 * real benchmark table under test rather than a snapshot written by the seed.
 */
internal fun seedFairnessEntries() {
    insertFairnessLog(
        id = FairnessFixtures.OVER_ID,
        date = "2026-03-02",
        odometerKm = 44_000,
        amountPaise = FairnessFixtures.OVER_PAISE,
        workshop = FairnessFixtures.OVER_WORKSHOP,
        category = "BRAKES",
        billPhotoPath = "bills/test-car/fair-over.jpg",
    )
    insertFairnessLog(
        id = FairnessFixtures.FAIR_ID,
        date = "2026-04-02",
        odometerKm = 46_000,
        amountPaise = FairnessFixtures.FAIR_PAISE,
        workshop = FairnessFixtures.FAIR_WORKSHOP,
        category = "BRAKES",
        billPhotoPath = "bills/test-car/fair-fair.jpg",
    )
    insertFairnessLog(
        id = FairnessFixtures.THIN_ID,
        date = "2026-05-02",
        odometerKm = 48_000,
        amountPaise = FairnessFixtures.THIN_PAISE,
        workshop = FairnessFixtures.THIN_WORKSHOP,
        category = "AC",
        billPhotoPath = "bills/test-car/fair-thin.jpg",
    )
    insertFairnessLog(
        id = FairnessFixtures.NO_DATA_ID,
        date = "2026-06-02",
        odometerKm = 50_000,
        amountPaise = FairnessFixtures.NO_DATA_PAISE,
        workshop = FairnessFixtures.NO_DATA_WORKSHOP,
        category = "ELECTRICAL",
        billPhotoPath = "bills/test-car/fair-nodata.jpg",
    )
    announceFairnessWrites()
}

/** One self-reported entry — no bill, so nothing about its price can be judged. */
internal fun seedSelfReportedEntry() {
    insertFairnessLog(
        id = FairnessFixtures.SELF_REPORTED_ID,
        date = "2026-02-02",
        odometerKm = 42_000,
        amountPaise = FairnessFixtures.SELF_REPORTED_PAISE,
        workshop = FairnessFixtures.SELF_REPORTED_WORKSHOP,
        category = "BRAKES",
        billPhotoPath = null,
    )
    announceFairnessWrites()
}

/** Take the city off the owner's profile — the state a benchmark cannot be looked up in. */
internal fun clearOwnerCity() = with(fairnessDriver()) {
    execute(null, "UPDATE profiles SET city = NULL WHERE id = '${LogFixtures.OWNER}'", 0)
    notifyListeners("profiles")
}

private fun insertFairnessLog(
    id: String,
    date: String,
    odometerKm: Int,
    amountPaise: Long,
    workshop: String,
    category: String,
    billPhotoPath: String?,
) = with(fairnessDriver()) {
    val bill = billPhotoPath?.let { "'$it'" } ?: "NULL"
    execute(
        null,
        "INSERT INTO service_logs (id, car_id, owner_id, service_date, odometer_km, total_amount_paise, " +
            "workshop_name, notes, source, bill_id, bill_photo_path, fairness_snapshot, created_at, " +
            "updated_at, sync_status) VALUES ('$id', '${LogFixtures.CAR}', '${LogFixtures.OWNER}', " +
            "'$date', $odometerKm, $amountPaise, '$workshop', NULL, 'MANUAL', NULL, $bill, NULL, " +
            "'$FAIRNESS_SEEDED_AT', '$FAIRNESS_SEEDED_AT', 'PENDING')",
        0,
    )
    execute(null, "INSERT INTO service_log_categories (service_log_id, category) VALUES ('$id', '$category')", 0)
}

/**
 * Tell SQLDelight these tables changed. Hand-written SQL goes in behind its back, so without
 * this an already-collecting screen keeps serving what it read before the seed.
 */
private fun announceFairnessWrites() = fairnessDriver().notifyListeners(
    "cars",
    "profiles",
    "service_logs",
    "service_log_categories",
)

private const val FAIRNESS_SEEDED_AT = "2026-07-01T00:00:00Z"

/* ------------------------------ The picker ------------------------------ */

/**
 * Answer the system document picker with a bill photo, instead of opening it.
 *
 * The picker is another app's activity: an instrumented test can neither drive it nor rely on
 * what it shows. Stubbing the result is the only way to test what Odo does *with* a picked
 * file — copy it into its own storage, verify the entry, and check the price.
 */
internal fun stubPickedBill(): Uri {
    val file = File(fairnessAppContext().cacheDir, PICKED_BILL_NAME)
    file.writeBytes(JPEG_BYTES)
    val uri = Uri.fromFile(file)
    intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
        .respondWith(ActivityResult(Activity.RESULT_OK, Intent().setData(uri)))
    return uri
}

/** Enough of a JPEG for the content resolver to name its type; nothing reads the contents. */
private val JPEG_BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())

private const val PICKED_BILL_NAME = "picked-bill.jpg"

private fun fairnessAppContext(): Context = InstrumentationRegistry.getInstrumentation().targetContext

/* ------------------------------ Navigation ------------------------------ */

/** Open a seeded entry's detail from the service log. */
internal fun FairnessTestRule.openEntryDetail(logId: String, workshop: String) {
    onNodeWithTag(ServiceLogTestTags.card(logId)).performClick()
    awaitFairnessText(workshop)
}

/**
 * Attach a bill to the open entry, with the picker stubbed.
 *
 * Waits for the button first: the detail screen animates in, and tapping mid-transition
 * lands on whatever the previous screen had there.
 */
internal fun FairnessTestRule.attachABill() {
    stubPickedBill()
    awaitFairnessText(FairnessCopy.ATTACH_BILL)
    onNodeWithText(FairnessCopy.ATTACH_BILL).performClick()
}

/** Run the check on the open entry and land on the report. */
internal fun FairnessTestRule.runFairnessCheck() {
    awaitFairnessText(FairnessCopy.CHECK_FAIRNESS)
    onNodeWithText(FairnessCopy.CHECK_FAIRNESS).performClick()
    awaitFairnessText(FairnessCopy.TITLE)
}

/* ------------------------------ Waiting ------------------------------ */

internal fun FairnessTestRule.awaitFairnessText(text: String, timeoutMillis: Long = FAIRNESS_TIMEOUT_MILLIS) {
    waitUntil(timeoutMillis) { fairnessTextCount(text) > 0 }
    onAllNodesWithText(text).onFirst()
}

internal fun FairnessTestRule.awaitFairnessTag(tag: String, timeoutMillis: Long = FAIRNESS_TIMEOUT_MILLIS) {
    waitUntil(timeoutMillis) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
}

internal fun FairnessTestRule.fairnessTextCount(text: String): Int =
    onAllNodesWithText(text).fetchSemanticsNodes().size

/** Wait until nothing on screen carries [tag] — a state the screen has moved off. */
internal fun FairnessTestRule.awaitFairnessGone(tag: String, timeoutMillis: Long = FAIRNESS_TIMEOUT_MILLIS) {
    waitUntil(timeoutMillis) { fairnessTagCount(tag) == 0 }
}

internal fun FairnessTestRule.fairnessTagCount(tag: String): Int =
    onAllNodesWithTag(tag).fetchSemanticsNodes().size

/** Long enough for the benchmark lookup and a screen transition, short enough to fail fast. */
private const val FAIRNESS_TIMEOUT_MILLIS = 5_000L

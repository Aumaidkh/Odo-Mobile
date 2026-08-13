package com.hopcape.odo

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hopcape.odo.feature.timeline.presentation.TimelineTestTags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Sharing the record, driven end to end: the Timeline tab's share button, the sheet, a real
 * WebView print, a real file on disk and a real `ACTION_SEND`.
 *
 * The unit tests cover what goes into the document and what the sheet does with it. What
 * only a device can answer is whether the whole chain actually produces a file — the print
 * pipeline runs nowhere else, and neither does the `FileProvider` whose configuration is the
 * one thing that turns a working render into a share that fails.
 *
 * The chooser is stubbed so the test does not hand the phone a real share sheet to sit on.
 *
 * **Before running:** clear the app's data, for the same reason as the timeline suite — the
 * local database has no migrations yet.
 */
@RunWith(AndroidJUnit4::class)
class RecordExportEndToEndTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    companion object {
        /** The four bytes every PDF starts with. */
        private const val PDF_MAGIC = "%PDF"

        /** Printing a whole record on an emulator is slower than a frame. */
        private const val EXPORT_TIMEOUT_MILLIS = 30_000L

        @JvmStatic
        @BeforeClass
        fun seedTheCarOnce() {
            resetTimeline()
            seedTimelineOwner()
        }
    }

    private val exported: File
        get() = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "exports/${LogFixtures.CAR}/service-record.pdf",
        )

    @Before
    fun startFromACarWithAHistory() {
        clearTimelineData()
        resetTimelineFilter()
        exported.delete()

        Intents.init()
        // Answer the chooser rather than launching it: an un-stubbed share sheet stays on
        // screen and every test after this one drives into it.
        intending(hasAction(Intent.ACTION_CHOOSER))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

        seedTimelineHistory()
        seedTimelineDocuments()
        seedTimelineScores()
    }

    @After
    fun releaseIntents() = Intents.release()

    @Test
    fun theTimelineSharesTheRecordAsAPdf() {
        rule.openTimeline()
        rule.onNodeWithTag(TimelineTestTags.SHARE_BUTTON).performClick()
        rule.awaitText(ShareCopy.SHEET_TITLE)

        rule.onNodeWithText(ShareCopy.DOWNLOAD_PDF).performClick()

        // The render is a WebView print, so the file appears well after the tap returns.
        rule.waitUntil(EXPORT_TIMEOUT_MILLIS) { exported.exists() && exported.length() > 0 }

        val bytes = exported.readBytes()
        assertEquals("the exported file is not a PDF", PDF_MAGIC, bytes.decodeToString(0, 4))
        assertTrue("a record of eight events is not 1 KB; got ${bytes.size}", bytes.size > 1_000)

        intended(hasAction(Intent.ACTION_CHOOSER))
    }

    @Test
    fun theSheetBecomesUsableAgainAfterSharing() {
        rule.openTimeline()
        rule.onNodeWithTag(TimelineTestTags.SHARE_BUTTON).performClick()
        rule.awaitText(ShareCopy.SHEET_TITLE)

        rule.onNodeWithText(ShareCopy.DOWNLOAD_PDF).performClick()
        rule.waitUntil(EXPORT_TIMEOUT_MILLIS) { exported.exists() && exported.length() > 0 }

        // Back to its resting label, which is what says the sheet is not still rendering.
        rule.awaitText(ShareCopy.DOWNLOAD_PDF)
        val firstWrite = exported.lastModified()

        // A second target sends the document already produced rather than making another.
        rule.onNodeWithText(ShareCopy.DOWNLOAD_PDF).performClick()
        rule.waitForIdle()

        assertEquals(
            "the record has not changed, so nothing should have been rendered again",
            firstWrite,
            exported.lastModified(),
        )
    }
}

/** The share sheet's copy, as the owner reads it. Mirrors `sl_share_*` in servicelog. */
internal object ShareCopy {
    const val SHEET_TITLE = "Share verified record"
    const val DOWNLOAD_PDF = "Download as PDF"
}

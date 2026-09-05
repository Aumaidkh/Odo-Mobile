package com.hopcape.odo

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * The share card, from the finding to the file that leaves the app.
 *
 * The card is a picture rather than a screen, so "it renders" is not the thing to assert.
 * What matters is that a real PNG lands in the export directory and that something is handed
 * it — those are the two steps between an owner tapping share and their family group seeing
 * the number.
 *
 * WhatsApp is not installed on an emulator, which makes this the fallback test as well: the
 * direct intent fails, and the owner gets the chooser rather than nothing happening.
 */
@RunWith(AndroidJUnit4::class)
class ShareCardEndToEndTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain
        .outerRule(
            DeviceState {
                resetOwnerData()
                seedOnboardedOwner()
                installNoStore()
            },
        )
        .around(rule)

    private val card: File
        get() = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "exports/odo-saved.png",
        )

    @Before
    fun startWithNoCardAndNoChooser() {
        card.delete()
        Intents.init()
        // Answered rather than launched: an un-stubbed share sheet stays on screen and every
        // test after this one drives into it.
        intending(hasAction(Intent.ACTION_CHOOSER))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun releaseIntents() = Intents.release()

    @Test
    fun theCardIsDrawnAndHandedOver() {
        seedBillToCheck()
        rule.openServiceLog()
        rule.openEntryDetail(BillCheckFixtures.BILL_ID, BillCheckFixtures.WORKSHOP)
        rule.onNodeWithText(CHECK_ACTION).performClick()
        rule.awaitTextContaining(RESULT_HEADLINE)

        rule.onNodeWithText(SHARE).performClick()
        rule.awaitText(CARD_LABEL)

        rule.onNodeWithText(SEND_ON_WHATSAPP).performClick()

        // The capture and the encode both happen after the tap returns.
        rule.waitUntil(CARD_TIMEOUT_MILLIS) { card.exists() && card.length() > 0 }
        val bytes = card.readBytes()
        assertEquals(
            "the exported card is not a PNG",
            PNG_MAGIC,
            bytes.copyOfRange(1, 4).decodeToString(),
        )
        assertTrue("a card of this size is not a real render; got ${bytes.size}", bytes.size > 5_000)

        // No WhatsApp on an emulator, so the chooser is what the owner is given.
        intended(hasAction(Intent.ACTION_CHOOSER))
    }

    /**
     * The other button. It copies out of app storage into the owner's downloads, and the
     * confirmation is the only proof they get that it worked — so it is the thing to assert.
     */
    @Test
    fun theCardCanBeKept() {
        seedBillToCheck()
        rule.openServiceLog()
        rule.openEntryDetail(BillCheckFixtures.BILL_ID, BillCheckFixtures.WORKSHOP)
        rule.onNodeWithText(CHECK_ACTION).performClick()
        rule.awaitTextContaining(RESULT_HEADLINE)

        rule.onNodeWithText(SHARE).performClick()
        rule.awaitText(CARD_LABEL)

        rule.onNodeWithText(SAVE).performClick()

        rule.awaitText(SAVED)
    }

    private companion object {
        const val CHECK_ACTION = "Check fairness"
        const val RESULT_HEADLINE = "worth asking about"
        const val SHARE = "Share"
        const val CARD_LABEL = "SAVED ON TODAY’S SERVICE"
        const val SEND_ON_WHATSAPP = "Send on WhatsApp"
        const val SAVE = "Save"
        const val SAVED = "Saved to your downloads"

        /** The three readable bytes after PNG's 0x89 signature byte. */
        const val PNG_MAGIC = "PNG"
        const val CARD_TIMEOUT_MILLIS = 10_000L
    }
}

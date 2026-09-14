package com.hopcape.odo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.db.SqlDriver
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * A problem report, from the first chip to a row on the device.
 *
 * The point of the whole stack is that the row exists before anything reaches the network, so
 * that is what this asserts: the confirmation names a reference, and the local table holds a
 * `PENDING` ticket carrying the area that was picked.
 */
@RunWith(AndroidJUnit4::class)
class SupportTicketEndToEndTest {

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

    @Test
    fun aReportBecomesARowBeforeItBecomesAnything() {
        rule.awaitText(HOME_TAB)
        rule.openProfile()
        rule.openHelpSheet()
        rule.openFromHelpSheet(REPORT_ROW, WHERE)

        rule.onNodeWithText(AREA_REMINDERS).performClick()
        rule.onNodeWithText(HINT).performTextInput(MESSAGE)
        // No account email on a phone-signed-in owner, so the form asks for one — which is
        // decision 3, and is what makes the ticket answerable.
        rule.onNodeWithText(EMAIL_LABEL).performTextInput(EMAIL)
        rule.onNodeWithText(SEND).performClick()

        rule.awaitText(SENT_HEADLINE)
        // The reference is derived from the ticket's own id, so its presence is proof a
        // ticket was created — not merely that a screen was shown.
        rule.awaitTextContaining(REFERENCE_PREFIX)

        val row = storedTicket()
        assertEquals("REMINDERS", row.area)
        assertEquals("saved locally, waiting for a connection", "PENDING", row.syncStatus)
        assertEquals(EMAIL, row.replyTo)
        assertTrue(row.body.contains("never arrived"))
    }

    private data class StoredTicket(
        val area: String,
        val syncStatus: String,
        val replyTo: String?,
        val body: String,
    )

    /** Read straight out of SQLDelight — the screen's word for it is not the evidence. */
    private fun storedTicket(): StoredTicket {
        val driver: SqlDriver = GlobalContext.get().get()
        return driver.executeQuery(
            identifier = null,
            sql = "SELECT details, sync_status, reply_to, body FROM support_tickets LIMIT 1",
            mapper = { cursor ->
                cursor.next()
                app.cash.sqldelight.db.QueryResult.Value(
                    StoredTicket(
                        area = cursor.getString(0).orEmpty(),
                        syncStatus = cursor.getString(1).orEmpty(),
                        replyTo = cursor.getString(2),
                        body = cursor.getString(3).orEmpty(),
                    ),
                )
            },
            parameters = 0,
        ).value.let { it.copy(area = AREA_IN_DETAILS.find(it.area)?.groupValues?.get(1).orEmpty()) }
    }

    private companion object {
        const val HOME_TAB = "Home"
        const val REPORT_ROW = "Report a problem"
        const val WHERE = "WHERE DID IT HAPPEN"
        const val AREA_REMINDERS = "Reminders"
        const val HINT = "What were you doing, and what happened instead?"
        const val MESSAGE = "The reminder never arrived on the due date."
        const val EMAIL_LABEL = "Your email"
        const val EMAIL = "owner@example.com"
        const val SEND = "Send report"
        const val SENT_HEADLINE = "Report sent"
        const val REFERENCE_PREFIX = "ODO-"

        /** `details` is JSON; this pulls the one value the test is about. */
        val AREA_IN_DETAILS = Regex("\"area\"\\s*:\\s*\"([A-Z_]+)\"")
    }
}

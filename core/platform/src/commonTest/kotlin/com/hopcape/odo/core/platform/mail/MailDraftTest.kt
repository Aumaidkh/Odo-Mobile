package com.hopcape.odo.core.platform.mail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MailDraftTest {

    private fun draft(
        to: String = "support@odoapp.in",
        subject: String = "Odo support",
        body: String = "Hello",
    ) = MailDraft(to = to, subject = subject, body = body)

    /**
     * The whole point of the change: both fields are in the URI, because the mail apps that
     * ignored the intent extras opened an untitled blank draft.
     */
    @Test
    fun theSubjectAndBodyAreInTheUri() {
        val uri = draft(subject = "Report a problem", body = "What went wrong:").toMailtoUri()

        assertEquals(
            "mailto:support@odoapp.in?subject=Report%20a%20problem&body=What%20went%20wrong%3A",
            uri,
        )
    }

    /** `support%40odoapp.in` is a legal escape and still not what some clients show. */
    @Test
    fun theAddressKeepsItsAtSign() {
        assertTrue(draft().toMailtoUri().startsWith("mailto:support@odoapp.in?"))
    }

    /** Both end the draft early if they reach the URL raw. */
    @Test
    fun newlinesAndAmpersandsInTheBodyAreEscaped() {
        val uri = draft(body = "one\ntwo & three").toMailtoUri()

        assertTrue(uri.endsWith("&body=one%0Atwo%20%26%20three"), uri)
    }

    /** Bodies are typed by people, and people type in more than one script. */
    @Test
    fun nonAsciiSurvivesAsUtf8Bytes() {
        assertTrue(draft(body = "ठीक").toMailtoUri().endsWith("&body=%E0%A4%A0%E0%A5%80%E0%A4%95"))
    }
}

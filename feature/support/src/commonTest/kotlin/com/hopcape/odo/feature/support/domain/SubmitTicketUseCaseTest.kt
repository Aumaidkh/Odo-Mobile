package com.hopcape.odo.feature.support.domain

import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.support.SupportTicket
import com.hopcape.odo.core.domain.support.TicketDetail
import com.hopcape.odo.core.domain.support.TicketKind
import com.hopcape.odo.feature.support.domain.usecase.PickedFile
import com.hopcape.odo.feature.support.domain.usecase.SubmitTicketUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Turning a filled-in form into a saved row.
 *
 * The rule that matters most is the one about files: a picker reference stops resolving once
 * the process dies, so a ticket that stored one would carry a screenshot nobody can open by
 * the time support reads it.
 */
class SubmitTicketUseCaseTest {

    @Test
    fun `a report is saved with what the form collected`() = runTest {
        val tickets = FakeTickets()

        val result = useCase(tickets)(
            kind = TicketKind.PROBLEM,
            body = "  The scan read 450 instead of 4,500.  ",
            details = mapOf(TicketDetail.AREA to "BILL_SCAN"),
            replyTo = "owner@example.com",
        )

        val ticket = assertIs<SupportTicket>(result.getOrNull())
        assertEquals(TicketKind.PROBLEM, ticket.kind)
        assertEquals("The scan read 450 instead of 4,500.", ticket.body, "the body is trimmed")
        assertEquals(mapOf(TicketDetail.AREA to "BILL_SCAN"), ticket.details)
        assertEquals("owner@example.com", ticket.replyTo)
        assertEquals(listOf(ticket), tickets.submitted)
    }

    /** A row saying an area and nothing else is not something support can act on. */
    @Test
    fun `a report with nothing written in it is refused`() = runTest {
        val tickets = FakeTickets()

        val result = useCase(tickets)(kind = TicketKind.PROBLEM, body = "   ")

        assertIs<DomainError.EmptyTicketBody>(result.leftOrNull())
        assertTrue(tickets.submitted.isEmpty(), "nothing reached the repository")
    }

    @Test
    fun `a body longer than the limit is refused`() = runTest {
        val result = useCase()(
            kind = TicketKind.IDEA,
            body = "x".repeat(SupportTicket.MAX_BODY_LENGTH + 1),
        )

        assertIs<DomainError.TicketBodyTooLong>(result.leftOrNull())
    }

    /** A detail column holding "" is a fact nobody can filter on. */
    @Test
    fun `blank details are dropped rather than stored`() = runTest {
        val result = useCase()(
            kind = TicketKind.PRICE_CORRECTION,
            body = "Band looks low.",
            details = mapOf(TicketDetail.JOB to "AC service", TicketDetail.CITY to "  "),
        )

        assertEquals(
            mapOf(TicketDetail.JOB to "AC service"),
            result.getOrNull()?.details,
        )
    }

    /* ------------------------------ Attachments ------------------------------ */

    /**
     * The picker's reference never reaches the ticket.
     *
     * It is a permission-scoped pointer into another app's storage and stops resolving after
     * a restart, so a stored one is a screenshot that is gone by the time anybody opens it.
     */
    @Test
    fun `a picked file is copied into app storage first`() = runTest {
        val files = FakeFiles()

        val result = useCase(files = files)(
            kind = TicketKind.PROBLEM,
            body = "Here is what I saw.",
            picked = listOf(PickedFile(ref = "content://picker/9", name = "bill.jpg")),
        )

        val attachment = result.getOrNull()?.attachments?.single()
        assertEquals("tickets/ticket-0/attachment-0.jpg", attachment?.storageKey)
        assertEquals("bill.jpg", attachment?.name, "the owner's own file name is kept")
        assertEquals(listOf("tickets/ticket-0/attachment-0.jpg"), files.saved)
    }

    /**
     * The words are the report. Losing the screenshot is worse than losing them, and refusing
     * to save either is worst of all.
     */
    @Test
    fun `a file that would not copy does not take the report down with it`() = runTest {
        val tickets = FakeTickets()

        val result = useCase(tickets, files = FakeFiles(failing = true))(
            kind = TicketKind.PROBLEM,
            body = "Here is what I saw.",
            picked = listOf(PickedFile(ref = "content://picker/9", name = "bill.jpg")),
        )

        assertTrue(result.getOrNull()?.attachments.orEmpty().isEmpty())
        assertEquals(1, tickets.submitted.size, "the report was still saved")
    }

    /* ------------------------------ The reference ------------------------------ */

    /** Derived from the id, so it exists with no signal and matches what the panel computes. */
    @Test
    fun `the reference is derived from the ticket's own id`() = runTest {
        val first = useCase()(kind = TicketKind.IDEA, body = "Two cars").getOrNull()

        assertTrue(first?.reference.orEmpty().startsWith("ODO-"))
        assertEquals(first?.reference, first?.reference, "and it is stable")
    }

    @Test
    fun `a repository that refuses reports the failure rather than a ticket`() = runTest {
        val result = useCase(FakeTickets(failing = true))(
            kind = TicketKind.PROBLEM,
            body = "Something broke.",
        )

        assertNull(result.getOrNull())
    }

    private fun useCase(
        tickets: FakeTickets = FakeTickets(),
        files: FakeFiles = FakeFiles(),
    ) = SubmitTicketUseCase(
        tickets = tickets,
        files = files,
        ids = CountingIds(),
        clock = FixedClock,
    )

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochSeconds(1_800_000_000L)
    }
}

package com.hopcape.odo.feature.support.presentation

import arrow.core.Either
import arrow.core.right
import androidx.lifecycle.SavedStateHandle
import com.hopcape.odo.core.domain.owner.model.OwnerEmail
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.OwnerName
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.support.TicketDetail
import com.hopcape.odo.core.domain.support.TicketKind
import com.hopcape.odo.feature.support.domain.CountingIds
import com.hopcape.odo.feature.support.domain.FakeFiles
import com.hopcape.odo.feature.support.domain.FakeTickets
import com.hopcape.odo.feature.support.domain.ReplyAddress
import com.hopcape.odo.feature.support.domain.telemetry
import com.hopcape.odo.feature.support.domain.usecase.SubmitTicketUseCase
import com.hopcape.odo.feature.support.presentation.report.ReportEffect
import com.hopcape.odo.feature.support.presentation.report.ReportEvent
import com.hopcape.odo.feature.support.presentation.report.ReportProblemViewModel
import com.hopcape.odo.feature.support.presentation.report.ReportArea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The form that becomes a ticket.
 *
 * Two rules carry the screen: the account's address decides whether the form states where a
 * reply goes or asks for one, and the address itself never leaves this class unmasked.
 */
class ReportProblemViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `an account with an address states where the reply goes`() = runTest {
        val viewModel = viewModel(email = "rakesh@gmail.com")
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("r•••@gmail.com", state.maskedEmail)
        assertFalse(state.asksForEmail)
    }

    @Test
    fun `an account with no address asks for one, and will not send without it`() = runTest {
        val viewModel = viewModel(email = null)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(ReportEvent.MessageChanged("It crashed."))

        val state = viewModel.state.value
        assertTrue(state.asksForEmail)
        assertFalse(state.canSend, "the message alone is not enough")
    }

    /** Decision 3 asks for the address so support can reply. "asdf" cannot be replied to. */
    @Test
    fun `a typed address that could not reach anybody is refused`() = runTest {
        val tickets = FakeTickets()
        val viewModel = viewModel(email = null, tickets = tickets)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(ReportEvent.MessageChanged("It crashed."))
        viewModel.onEvent(ReportEvent.EmailChanged("asdf"))
        viewModel.onEvent(ReportEvent.SendClicked)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.emailInvalid)
        assertTrue(tickets.submitted.isEmpty())
    }

    /**
     * The account is read after the screen opens, so for a moment "no address yet" and "this
     * account has none" look the same. A Send in that moment must not be refused for an
     * address the owner was never asked for.
     */
    @Test
    fun `the form does not ask for an address before the account has been read`() = runTest {
        val viewModel = viewModel(email = "rakesh@gmail.com")

        val before = viewModel.state.value
        assertFalse(before.asksForEmail, "nothing is known yet")
        assertFalse(before.canSend, "and nothing can be sent on what is not known")
    }

    @Test
    fun `a report is saved with its area, and answers with a reference`() = runTest {
        val tickets = FakeTickets()
        val viewModel = viewModel(email = "rakesh@gmail.com", tickets = tickets)

        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(ReportEvent.AreaPicked(ReportArea.REMINDERS))
        viewModel.onEvent(ReportEvent.MessageChanged("The reminder never arrived."))
        viewModel.onEvent(ReportEvent.SendClicked)
        dispatcher.scheduler.advanceUntilIdle()

        val sent = viewModel.effects.first() as ReportEffect.Sent
        assertEquals("REMINDERS", sent.area)
        assertTrue(sent.reference.startsWith("ODO-"))
        assertEquals("r•••@gmail.com", sent.maskedReplyTo, "never the whole address")

        val ticket = tickets.submitted.single()
        assertEquals(TicketKind.PROBLEM, ticket.kind)
        assertEquals("REMINDERS", ticket.details[TicketDetail.AREA])
        assertEquals("rakesh@gmail.com", ticket.replyTo, "the ticket carries the real one")
    }

    /**
     * The switch says "helps us find it faster". This is what makes that true — without the
     * request there is no reference, and support has to ask for one in a second round trip.
     */
    @Test
    fun `attaching logs opens a diagnostics request and files its reference`() = runTest {
        val tickets = FakeTickets()
        val viewModel = viewModel(email = "r@x.co", tickets = tickets, diagnostics = { "ODO-AAAA-BBBB" })
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(ReportEvent.MessageChanged("It crashed."))
        viewModel.onEvent(ReportEvent.SendClicked)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("ODO-AAAA-BBBB", tickets.submitted.single().diagnosticsReference)
    }

    @Test
    fun `turning the switch off files no reference`() = runTest {
        val tickets = FakeTickets()
        val viewModel = viewModel(email = "r@x.co", tickets = tickets, diagnostics = { "ODO-AAAA-BBBB" })
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(ReportEvent.MessageChanged("It crashed."))
        viewModel.onEvent(ReportEvent.AttachLogsToggled(false))
        viewModel.onEvent(ReportEvent.SendClicked)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, tickets.submitted.single().diagnosticsReference)
    }

    @Test
    fun `a failed save says so rather than claiming the report went`() = runTest {
        val viewModel = viewModel(email = "r@x.co", tickets = FakeTickets(failing = true))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(ReportEvent.MessageChanged("It crashed."))
        viewModel.onEvent(ReportEvent.SendClicked)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.failed)
        assertFalse(viewModel.state.value.sending)
    }

    /**
     * Picking a screenshot starts another app, which is a routine way for a low-memory device
     * to kill this process. Losing three paragraphs of a bug report to that is what stops
     * somebody reporting the bug at all.
     */
    @Test
    fun `what was typed survives the process being killed`() = runTest {
        val saved = SavedStateHandle()
        viewModel(email = "r@x.co", saved = saved).also {
            dispatcher.scheduler.advanceUntilIdle()
            it.onEvent(ReportEvent.AreaPicked(ReportArea.CHALLAN))
            it.onEvent(ReportEvent.MessageChanged("Three paragraphs of detail."))
            it.onEvent(ReportEvent.EmailChanged("owner@example.com"))
        }

        // The same handle, a new view model: what the platform hands back after a restart.
        val restored = viewModel(email = "r@x.co", saved = saved)

        assertEquals("Three paragraphs of detail.", restored.state.value.message)
        assertEquals(ReportArea.CHALLAN, restored.state.value.area)
        assertEquals("owner@example.com", restored.state.value.email)
        assertFalse(restored.state.value.sending, "an interrupted send did not happen")
    }

    /**
     * A file that would not copy is dropped by the use case. Saying it travelled tells the
     * owner their screenshot is with support when it is nowhere.
     */
    @Test
    fun `the confirmation counts what was stored, not what was picked`() = runTest {
        val viewModel = viewModel(email = "r@x.co", files = FakeFiles(failing = true))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(ReportEvent.AttachmentPicked("content://9", "bill.jpg"))
        viewModel.onEvent(ReportEvent.MessageChanged("It crashed."))
        viewModel.onEvent(ReportEvent.SendClicked)
        dispatcher.scheduler.advanceUntilIdle()

        val sent = viewModel.effects.first() as ReportEffect.Sent
        assertEquals(0, sent.photos)
    }

    /** Two taps before the button redraws are two tickets, two references, two log uploads. */
    @Test
    fun `a second tap while the first is in flight files nothing`() = runTest {
        val tickets = FakeTickets()
        val viewModel = viewModel(email = "r@x.co", tickets = tickets)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(ReportEvent.MessageChanged("It crashed."))
        viewModel.onEvent(ReportEvent.SendClicked)
        viewModel.onEvent(ReportEvent.SendClicked)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, tickets.submitted.size)
    }

    /** A save that failed and then succeeded must not leave the screen saying it did not. */
    @Test
    fun `a retry clears the failure it is retrying`() = runTest {
        val viewModel = viewModel(email = "r@x.co", tickets = FakeTickets(failing = true))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(ReportEvent.MessageChanged("It crashed."))
        viewModel.onEvent(ReportEvent.SendClicked)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.failed)

        viewModel.onEvent(ReportEvent.SendClicked)
        assertFalse(viewModel.state.value.failed, "cleared while the retry is in flight")
    }

    /**
     * Opening a fresh request per attempt would file a new outbox row and a new upload nudge
     * each time, for one report.
     */
    @Test
    fun `a retry reuses the diagnostics request it already opened`() = runTest {
        var opened = 0
        val viewModel = viewModel(
            email = "r@x.co",
            tickets = FakeTickets(failing = true),
            diagnostics = { opened++; "ODO-AAAA-BBBB" },
        )
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(ReportEvent.MessageChanged("It crashed."))
        repeat(3) {
            viewModel.onEvent(ReportEvent.SendClicked)
            dispatcher.scheduler.advanceUntilIdle()
        }

        assertEquals(1, opened)
    }

    /** A log file is not worth losing a report over. */
    @Test
    fun `a diagnostics request that throws does not take the report with it`() = runTest {
        val tickets = FakeTickets()
        val viewModel = viewModel(
            email = "r@x.co",
            tickets = tickets,
            diagnostics = { error("no database") },
        )
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(ReportEvent.MessageChanged("It crashed."))
        viewModel.onEvent(ReportEvent.SendClicked)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, tickets.submitted.size)
        assertEquals(null, tickets.submitted.single().diagnosticsReference)
    }

    private fun viewModel(
        email: String?,
        tickets: FakeTickets = FakeTickets(),
        files: FakeFiles = FakeFiles(),
        diagnostics: suspend () -> String = { "ODO-0000-0000" },
        saved: SavedStateHandle = SavedStateHandle(),
    ) = ReportProblemViewModel(
        saved = saved,
        submit = SubmitTicketUseCase(
            tickets = tickets,
            files = files,
            ids = CountingIds(),
            clock = FixedClock,
        ),
        replyAddress = ReplyAddress(FakeProfiles(email)),
        requestDiagnostics = diagnostics,
        telemetry = telemetry(),
    )

    private class FakeProfiles(private val email: String?) : OwnerProfileRepository {
        override fun observe(): Flow<OwnerProfile?> = flowOf(
            OwnerProfile.new(OwnerId("owner-1"), OwnerName.of("Rakesh").getOrNull()!!)
                .withEmail(OwnerEmail.of(email).getOrNull()),
        )

        override suspend fun save(profile: OwnerProfile) = error("not called")
        override suspend fun recordPhone(ownerId: OwnerId, phone: PhoneNumber) = error("not called")
        override suspend fun delete(): Either<DomainError, Unit> = Unit.right()
    }

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochSeconds(1_800_000_000L)
    }
}

package com.hopcape.odo.feature.support.presentation

import com.hopcape.odo.core.domain.support.TicketDetail
import com.hopcape.odo.core.domain.support.TicketKind
import com.hopcape.odo.feature.support.domain.CountingIds
import com.hopcape.odo.feature.support.domain.FakeFiles
import com.hopcape.odo.feature.support.domain.FakeTickets
import com.hopcape.odo.feature.support.domain.telemetry
import com.hopcape.odo.feature.support.domain.usecase.SubmitTicketUseCase
import com.hopcape.odo.feature.support.presentation.flagprice.BandComplaint
import com.hopcape.odo.feature.support.presentation.flagprice.DisputedBand
import com.hopcape.odo.feature.support.presentation.flagprice.FlagPriceEvent
import com.hopcape.odo.feature.support.presentation.flagprice.FlagPriceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A price correction.
 *
 * The figure is the whole point — a paragraph cannot move a band and a rupee amount against a
 * named job can — so what matters here is that the number reaches the ticket as paise, beside
 * the band it disputes.
 */
class FlagPriceViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the correction carries the figure, the band and the complaint`() = runTest {
        val tickets = FakeTickets()
        val viewModel = viewModel(tickets = tickets, band = band())

        viewModel.onEvent(FlagPriceEvent.ComplaintPicked(BandComplaint.TOO_LOW))
        viewModel.onEvent(FlagPriceEvent.PaidChanged("2350"))
        viewModel.onEvent(FlagPriceEvent.SendClicked)
        dispatcher.scheduler.advanceUntilIdle()

        val ticket = tickets.submitted.single()
        assertEquals(TicketKind.PRICE_CORRECTION, ticket.kind)
        assertEquals("AC service", ticket.details[TicketDetail.JOB])
        assertEquals("TOO_LOW", ticket.details[TicketDetail.COMPLAINT])
        // Paise, like every other money column in the schema. The field takes whole rupees.
        assertEquals("235000", ticket.details[TicketDetail.PAID_PAISE])
        assertEquals("140000", ticket.details[TicketDetail.BAND_LOW_PAISE])
        assertEquals("Srinagar", ticket.details[TicketDetail.CITY])
    }

    /**
     * The screen says outright that nobody will be emailed about it. Attaching an address
     * would be collecting something with no use for it.
     */
    @Test
    fun `no reply address is carried`() = runTest {
        val tickets = FakeTickets()
        val viewModel = viewModel(tickets = tickets, band = band())

        viewModel.onEvent(FlagPriceEvent.ComplaintPicked(BandComplaint.TOO_HIGH))
        viewModel.onEvent(FlagPriceEvent.PaidChanged("900"))
        viewModel.onEvent(FlagPriceEvent.SendClicked)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(tickets.submitted.single().replyTo)
    }

    /** Opened from the help sheet: the owner names the job, and there is no band to echo. */
    @Test
    fun `a correction with no band uses the job the owner typed`() = runTest {
        val tickets = FakeTickets()
        val viewModel = viewModel(tickets = tickets, band = null)

        viewModel.onEvent(FlagPriceEvent.JobNameChanged("Wheel alignment"))
        viewModel.onEvent(FlagPriceEvent.ComplaintPicked(BandComplaint.WRONG_ITEM))
        viewModel.onEvent(FlagPriceEvent.PaidChanged("600"))
        viewModel.onEvent(FlagPriceEvent.SendClicked)
        dispatcher.scheduler.advanceUntilIdle()

        val details = tickets.submitted.single().details
        assertEquals("Wheel alignment", details[TicketDetail.JOB])
        assertFalse(details.containsKey(TicketDetail.BAND_LOW_PAISE))
    }

    @Test
    fun `nothing can be sent without a complaint and a figure`() = runTest {
        val viewModel = viewModel(band = band())

        assertFalse(viewModel.state.value.canSend)

        viewModel.onEvent(FlagPriceEvent.ComplaintPicked(BandComplaint.TOO_LOW))
        assertFalse(viewModel.state.value.canSend, "a complaint on its own is an opinion")

        viewModel.onEvent(FlagPriceEvent.PaidChanged("2350"))
        assertTrue(viewModel.state.value.canSend)
    }

    private fun viewModel(
        tickets: FakeTickets = FakeTickets(),
        band: DisputedBand? = null,
    ) = FlagPriceViewModel(
        band = band,
        submit = SubmitTicketUseCase(
            tickets = tickets,
            files = FakeFiles(),
            ids = CountingIds(),
            clock = FixedClock,
        ),
        telemetry = telemetry(),
    )

    private fun band() = DisputedBand(
        lineName = "AC service",
        lowPaise = 140_000L,
        highPaise = 180_000L,
        city = "Srinagar",
        workshop = "company centre",
        segment = "1.2L petrol hatchback",
    )

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochSeconds(1_800_000_000L)
    }
}

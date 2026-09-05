package com.hopcape.odo.feature.support.presentation

import com.hopcape.odo.core.domain.support.FeatureIdea
import com.hopcape.odo.core.domain.support.IdeaStatus
import com.hopcape.odo.core.domain.support.TicketKind
import com.hopcape.odo.feature.support.domain.CountingIds
import com.hopcape.odo.feature.support.domain.FakeFiles
import com.hopcape.odo.feature.support.domain.FakeIdeas
import com.hopcape.odo.feature.support.domain.FakeTickets
import com.hopcape.odo.feature.support.domain.telemetry
import com.hopcape.odo.feature.support.domain.usecase.CastIdeaVoteUseCase
import com.hopcape.odo.feature.support.domain.usecase.SubmitTicketUseCase
import com.hopcape.odo.feature.support.presentation.idea.IdeaEffect
import com.hopcape.odo.feature.support.presentation.idea.IdeaEvent
import com.hopcape.odo.feature.support.presentation.idea.SuggestIdeaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The idea box, and the list beside it.
 *
 * The list is the half worth testing: a vote has to answer the tap that made it, and a
 * refresh that fails must not take away what is already on screen.
 */
class SuggestIdeaViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the curated list is shown as rows`() = runTest {
        val viewModel = viewModel(ideas = FakeIdeas(listOf(idea("1", "Two cars", 412))))
        dispatcher.scheduler.advanceUntilIdle()

        val row = viewModel.state.value.ideas.single()
        assertEquals("Two cars", row.title)
        assertEquals(412, row.votes)
    }

    /** The tap has to be answered by the number, or the pill looks broken. */
    @Test
    fun `a vote moves the count and marks the row`() = runTest {
        val ideas = FakeIdeas(listOf(idea("1", "Two cars", 412)))
        val viewModel = viewModel(ideas = ideas)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(IdeaEvent.VoteToggled("1"))
        dispatcher.scheduler.advanceUntilIdle()

        val row = viewModel.state.value.ideas.single()
        assertTrue(row.voted)
        assertEquals(413, row.votes)
        assertEquals(listOf("1" to true), ideas.votes)
    }

    @Test
    fun `voting again takes it back off`() = runTest {
        val ideas = FakeIdeas(listOf(idea("1", "Two cars", 412, voted = true)))
        val viewModel = viewModel(ideas = ideas)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(IdeaEvent.VoteToggled("1"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("1" to false), ideas.votes)
        assertEquals(411, viewModel.state.value.ideas.single().votes)
    }

    /** What is cached is still worth showing, and an error over other people's ideas helps nobody. */
    @Test
    fun `a refresh that fails leaves the list alone and says nothing`() = runTest {
        val ideas = FakeIdeas(listOf(idea("1", "Two cars", 412))).apply { refreshFails = true }
        val viewModel = viewModel(ideas = ideas)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.ideas.size)
    }

    @Test
    fun `an idea is saved as a ticket and clears the box`() = runTest {
        val tickets = FakeTickets()
        val viewModel = viewModel(tickets = tickets)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(IdeaEvent.TextChanged("Hindi interface"))
        viewModel.onEvent(IdeaEvent.SendClicked)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(IdeaEffect.Sent, viewModel.effects.first())
        assertEquals(TicketKind.IDEA, tickets.submitted.single().kind)
        assertEquals("Hindi interface", tickets.submitted.single().body)
        assertEquals("", viewModel.state.value.text, "the box is emptied, not left to re-send")
    }

    @Test
    fun `a failed save is said rather than swallowed`() = runTest {
        val viewModel = viewModel(tickets = FakeTickets(failing = true))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(IdeaEvent.TextChanged("Hindi interface"))
        viewModel.onEvent(IdeaEvent.SendClicked)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(IdeaEffect.Failed, viewModel.effects.first())
        assertEquals("Hindi interface", viewModel.state.value.text, "and the words are kept")
    }

    /**
     * A vote that was never written must not be counted, and the pill snapping back looks
     * identical to a double tap — so the owner is told.
     */
    @Test
    fun `a vote that could not be written is said rather than counted`() = runTest {
        val ideas = FakeIdeas(listOf(idea("1", "Two cars", 412))).apply { voteFails = true }
        val viewModel = viewModel(ideas = ideas)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(IdeaEvent.VoteToggled("1"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(IdeaEffect.VoteFailed, viewModel.effects.first())
        assertEquals(412, viewModel.state.value.ideas.single().votes, "the count did not move")
    }

    @Test
    fun `a second send while the first is in flight files nothing`() = runTest {
        val tickets = FakeTickets()
        val viewModel = viewModel(tickets = tickets)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(IdeaEvent.TextChanged("Hindi interface"))
        viewModel.onEvent(IdeaEvent.SendClicked)
        viewModel.onEvent(IdeaEvent.SendClicked)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, tickets.submitted.size)
    }

    private fun viewModel(
        ideas: FakeIdeas = FakeIdeas(),
        tickets: FakeTickets = FakeTickets(),
    ) = SuggestIdeaViewModel(
        ideas = ideas,
        castVote = CastIdeaVoteUseCase(ideas),
        submit = SubmitTicketUseCase(
            tickets = tickets,
            files = FakeFiles(),
            ids = CountingIds(),
            clock = FixedClock,
        ),
        telemetry = telemetry(),
    )

    private fun idea(id: String, title: String, votes: Int, voted: Boolean = false) = FeatureIdea(
        id = id,
        title = title,
        status = IdeaStatus.IN_PROGRESS,
        votes = votes,
        voted = voted,
    )

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochSeconds(1_800_000_000L)
    }
}

package com.hopcape.odo.feature.challan.presentation

import com.hopcape.odo.core.domain.challan.model.ChallanStatus
import com.hopcape.odo.feature.challan.FakeChallanRepository
import com.hopcape.odo.feature.challan.FixedClock
import com.hopcape.odo.feature.challan.challan
import com.hopcape.odo.feature.challan.domain.usecase.LookupChallansUseCase
import com.hopcape.odo.feature.challan.presentation.result.ChallanResultViewModel
import com.hopcape.odo.feature.challan.presentation.state.Loadable
import com.hopcape.odo.feature.challan.presentation.state.valueOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class ChallanResultViewModelTest {

    private val now = Instant.parse("2026-08-22T12:00:00Z")

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(repo: FakeChallanRepository, regNo: String = "MH14DK8842") = ChallanResultViewModel(
        regNoRaw = regNo,
        lookup = LookupChallansUseCase(challans = repo),
        clock = FixedClock(now),
    )

    @Test
    fun pendingChallans_carryTheTransferWarning() = runTest {
        val repo = FakeChallanRepository(
            challans = listOf(
                challan(id = "A", amountPaise = 2_000_00),
                challan(id = "B", amountPaise = 1_000_00),
                // Someone else's already-paid history is not the buyer's problem.
                challan(id = "C", status = ChallanStatus.PAID, amountPaise = 9_000_00),
            ),
        )
        val viewModel = viewModel(repo)

        val content = viewModel.state.first { it.content is Loadable.Ready }.content.valueOrNull!!
        assertEquals("MH 14 DK 8842", viewModel.state.value.regNo)
        assertNotNull(content.transfer)
        assertEquals(2, content.rows.size)
    }

    @Test
    fun aCleanVehicle_hasNoWarning() = runTest {
        val viewModel = viewModel(FakeChallanRepository())

        val content = viewModel.state.first { it.content is Loadable.Ready }.content.valueOrNull!!
        assertNull(content.transfer)
        assertEquals(0, content.rows.size)
    }

    @Test
    fun theSourceBeingDownOnArrival_isSaidPlainly() = runTest {
        val repo = FakeChallanRepository()
        repo.sourceDown = true
        val viewModel = viewModel(repo)

        assertIs<Loadable.Failed>(viewModel.state.first { it.content !is Loadable.Loading }.content)
    }
}

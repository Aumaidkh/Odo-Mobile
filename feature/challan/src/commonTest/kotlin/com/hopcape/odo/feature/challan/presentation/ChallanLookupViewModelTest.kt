package com.hopcape.odo.feature.challan.presentation

import com.hopcape.odo.feature.challan.FakeChallanRepository
import com.hopcape.odo.feature.challan.challan
import com.hopcape.odo.feature.challan.domain.usecase.LookupChallansUseCase
import com.hopcape.odo.feature.challan.presentation.lookup.ChallanLookupEffect
import com.hopcape.odo.feature.challan.presentation.lookup.ChallanLookupEvent
import com.hopcape.odo.feature.challan.presentation.lookup.ChallanLookupViewModel
import com.hopcape.odo.feature.challan.testTelemetry
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

class ChallanLookupViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(repo: FakeChallanRepository) =
        ChallanLookupViewModel(lookup = LookupChallansUseCase(challans = repo), telemetry = testTelemetry())

    @Test
    fun aKnownPlate_opensTheResult_normalized() = runTest {
        val viewModel = viewModel(FakeChallanRepository(challans = listOf(challan())))
        viewModel.onEvent(ChallanLookupEvent.PlateChanged("mh 14 dk 8842"))
        viewModel.onEvent(ChallanLookupEvent.CheckTapped)

        val effect = viewModel.effects.first()
        assertIs<ChallanLookupEffect.OpenResult>(effect)
        assertEquals("MH14DK8842", effect.regNo)
    }

    @Test
    fun anUnknownPlate_becomesTheNotFoundAnswer_notAnError() = runTest {
        val repo = FakeChallanRepository()
        repo.vehicleKnown = false
        val viewModel = viewModel(repo)
        viewModel.onEvent(ChallanLookupEvent.PlateChanged("MH 14 DK 884"))
        viewModel.onEvent(ChallanLookupEvent.CheckTapped)

        val notFound = assertNotNull(viewModel.state.value.notFound)
        assertEquals("MH14DK884", notFound.plateDisplay.filterNot { it == ' ' })
        assertNull(viewModel.state.value.error)

        // "Edit the number" returns to the input with the plate intact.
        viewModel.onEvent(ChallanLookupEvent.EditNumberTapped)
        assertNull(viewModel.state.value.notFound)
        assertEquals("MH 14 DK 884", viewModel.state.value.plate)
    }

    @Test
    fun aPlateTooShortToBeReal_isRefusedBeforeTheNetwork() = runTest {
        val repo = FakeChallanRepository()
        val viewModel = viewModel(repo)
        viewModel.onEvent(ChallanLookupEvent.PlateChanged("MH1"))
        viewModel.onEvent(ChallanLookupEvent.CheckTapped)

        assertNotNull(viewModel.state.value.error)
    }

    @Test
    fun theSourceBeingDown_isAnErrorUnderTheField() = runTest {
        val repo = FakeChallanRepository()
        repo.sourceDown = true
        val viewModel = viewModel(repo)
        viewModel.onEvent(ChallanLookupEvent.PlateChanged("MH14DK8842"))
        viewModel.onEvent(ChallanLookupEvent.CheckTapped)

        assertNotNull(viewModel.state.value.error)
        assertNull(viewModel.state.value.notFound)
    }
}

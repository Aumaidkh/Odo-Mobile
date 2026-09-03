package com.hopcape.odo.web.admin.presentation.reference

import arrow.core.Either
import arrow.core.right
import com.hopcape.odo.web.admin.domain.Coverage
import com.hopcape.odo.web.admin.domain.JobPrice
import com.hopcape.odo.web.admin.domain.LabourRate
import com.hopcape.odo.web.admin.domain.PartPrice
import com.hopcape.odo.web.admin.domain.Provenance
import com.hopcape.odo.web.admin.domain.ReferenceDataRepository
import com.hopcape.odo.web.admin.domain.ResolvedBand
import com.hopcape.odo.web.admin.domain.ScheduleItem
import com.hopcape.odo.web.admin.domain.ServiceItem
import com.hopcape.odo.web.admin.domain.VehicleSegment
import com.hopcape.odo.web.admin.domain.WorkshopTier
import com.hopcape.odo.web.core.domain.WebError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReferenceDataViewModelTest {

    /** Records what was written, and answers reads from lists the test controls. */
    private class FakeReference(
        var labour: List<LabourRate> = emptyList(),
        var coverage: List<Coverage> = emptyList(),
        var categories: List<ServiceItem> = emptyList(),
        var band: ResolvedBand? = null,
    ) : ReferenceDataRepository {
        val writes = mutableListOf<String>()

        override suspend fun labourRates() = labour.right()
        override suspend fun jobPrices() = emptyList<JobPrice>().right()
        override suspend fun partPrices() = emptyList<PartPrice>().right()
        override suspend fun schedule() = emptyList<ScheduleItem>().right()
        override suspend fun coverage() = coverage.right()
        override suspend fun categories() = categories.right()

        override suspend fun saveLabourRate(rate: LabourRate): Either<WebError, Unit> {
            writes += "labour:${rate.cityTier}|${rate.workshopTier.id}|${rate.paisePerHour}|${rate.provenance.status}"
            return Unit.right()
        }

        override suspend fun saveJobPrice(price: JobPrice): Either<WebError, Unit> {
            writes += "job:${price.categoryId}|${price.segment.id}|${price.partsPaise}|${price.labourHours}"
            return Unit.right()
        }

        override suspend fun savePartPrice(price: PartPrice): Either<WebError, Unit> {
            writes += "part:${price.partSlug}|${price.mrpPaise}"
            return Unit.right()
        }

        override suspend fun saveScheduleItem(item: ScheduleItem): Either<WebError, Unit> {
            writes += "schedule:${item.brand}|${item.itemSlug}|${item.dueKm}|${item.dueMonths}"
            return Unit.right()
        }

        override suspend fun setStatus(table: String, id: String, approved: Boolean): Either<WebError, Unit> {
            writes += "status:$table|$id|$approved"
            return Unit.right()
        }

        override suspend fun resolve(
            categorySlug: String,
            city: String,
            segment: VehicleSegment,
            workshopTier: WorkshopTier,
        ): Either<WebError, ResolvedBand?> {
            writes += "resolve:$categorySlug|$city|${segment.id}|${workshopTier.id}"
            return band.right()
        }
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun rate(tier: Int = 1, approved: Boolean = false) = LabourRate(
        cityTier = tier,
        workshopTier = WorkshopTier.Local,
        paisePerHour = 45000,
        provenance = Provenance(status = if (approved) Provenance.APPROVED else Provenance.DRAFT),
    )

    @Test
    fun `rupees typed into the form reach the repository as paise`() = runTest {
        val repo = FakeReference()
        val viewModel = ReferenceDataViewModel(repo)

        viewModel.onEvent(ReferenceEvent.LabourEditRequested(null))
        viewModel.onEvent(ReferenceEvent.EditorFieldChanged(EditorField.RatePerHour, "450"))
        viewModel.onEvent(ReferenceEvent.EditorSubmitted)

        // 450 rupees is 45000 paise. Money never becomes a float on the way.
        assertTrue(repo.writes.any { it.startsWith("labour:") && it.contains("|45000|") }, repo.writes.toString())
    }

    @Test
    fun `a new row is saved as a draft rather than served straight away`() = runTest {
        val repo = FakeReference()
        val viewModel = ReferenceDataViewModel(repo)

        viewModel.onEvent(ReferenceEvent.LabourEditRequested(null))
        viewModel.onEvent(ReferenceEvent.EditorFieldChanged(EditorField.RatePerHour, "500"))
        viewModel.onEvent(ReferenceEvent.EditorSubmitted)

        assertTrue(repo.writes.single().endsWith("|${Provenance.DRAFT}"), repo.writes.toString())
    }

    @Test
    fun `a rate with nothing typed cannot be submitted`() = runTest {
        val viewModel = ReferenceDataViewModel(FakeReference())

        viewModel.onEvent(ReferenceEvent.LabourEditRequested(null))
        assertFalse(assertNotNull(viewModel.state.value.editor).canSubmit)
    }

    @Test
    fun `a schedule row needs at least one of the two intervals`() = runTest {
        val viewModel = ReferenceDataViewModel(FakeReference())

        viewModel.onEvent(ReferenceEvent.ScheduleEditRequested(null))
        viewModel.onEvent(ReferenceEvent.EditorFieldChanged(EditorField.ItemSlug, "brake_fluid"))
        viewModel.onEvent(ReferenceEvent.EditorFieldChanged(EditorField.DisplayName, "Brake fluid"))
        assertFalse(assertNotNull(viewModel.state.value.editor).canSubmit)

        viewModel.onEvent(ReferenceEvent.EditorFieldChanged(EditorField.DueMonths, "36"))
        assertTrue(assertNotNull(viewModel.state.value.editor).canSubmit)
    }

    @Test
    fun `the labour key carries both halves of the composite primary key`() = runTest {
        val repo = FakeReference(labour = listOf(rate(approved = true)))
        val viewModel = ReferenceDataViewModel(repo)

        viewModel.onEvent(ReferenceEvent.StatusToggled("labour_rates", "1:local", false))

        // labour_rates has no id column, so the row is addressed by the pair.
        assertTrue(repo.writes.any { it == "status:labour_rates|1:local|false" }, repo.writes.toString())
    }

    @Test
    fun `a resolve that finds no band is reported rather than treated as a failure`() = runTest {
        val repo = FakeReference(band = null)
        val viewModel = ReferenceDataViewModel(repo)

        viewModel.onEvent(ReferenceEvent.PreviewFieldChanged(PreviewField.Category, "clutch"))
        viewModel.onEvent(ReferenceEvent.PreviewFieldChanged(PreviewField.City, "Pune"))
        viewModel.onEvent(ReferenceEvent.PreviewRequested)

        val preview = viewModel.state.value.preview
        assertNull(preview.band)
        assertTrue(preview.answered)
        assertNotNull(viewModel.state.value.message)
    }

    @Test
    fun `a modelled band comes back with the rung that answered it`() = runTest {
        val repo = FakeReference(
            band = ResolvedBand(
                avgPaise = 200000,
                p25 = 170000,
                p75 = 230000,
                sampleSize = 0,
                scope = "MODELLED",
                basis = "modelled",
            ),
        )
        val viewModel = ReferenceDataViewModel(repo)

        viewModel.onEvent(ReferenceEvent.PreviewFieldChanged(PreviewField.Category, "ac_service"))
        viewModel.onEvent(ReferenceEvent.PreviewFieldChanged(PreviewField.City, "Mumbai"))
        viewModel.onEvent(ReferenceEvent.PreviewRequested)

        assertEquals("MODELLED", assertNotNull(viewModel.state.value.preview.band).scope)
        assertTrue(repo.writes.any { it == "resolve:ac_service|Mumbai|hatchback|multi_brand" }, repo.writes.toString())
    }

    @Test
    fun `editing a field clears a band that was answered for the old inputs`() = runTest {
        val repo = FakeReference(
            band = ResolvedBand(200000, 170000, 230000, 0, "MODELLED", "modelled"),
        )
        val viewModel = ReferenceDataViewModel(repo)

        viewModel.onEvent(ReferenceEvent.PreviewFieldChanged(PreviewField.Category, "ac_service"))
        viewModel.onEvent(ReferenceEvent.PreviewFieldChanged(PreviewField.City, "Mumbai"))
        viewModel.onEvent(ReferenceEvent.PreviewRequested)
        assertNotNull(viewModel.state.value.preview.band)

        // A band left on screen under changed inputs is the exact misreading this
        // preview exists to prevent.
        viewModel.onEvent(ReferenceEvent.PreviewFieldChanged(PreviewField.City, "Pune"))
        assertNull(viewModel.state.value.preview.band)
    }

    @Test
    fun `coverage is looked up by table name`() = runTest {
        val repo = FakeReference(
            coverage = listOf(
                Coverage("labour_rates", approvedRows = 9, expectedRows = 9),
                Coverage("job_prices", approvedRows = 4, expectedRows = 30),
            ),
        )
        val viewModel = ReferenceDataViewModel(repo)

        val state = viewModel.state.value
        assertTrue(assertNotNull(state.coverageOf("labour_rates")).isComplete)
        assertFalse(assertNotNull(state.coverageOf("job_prices")).isComplete)
        assertNull(state.coverageOf("part_prices"))
    }
}

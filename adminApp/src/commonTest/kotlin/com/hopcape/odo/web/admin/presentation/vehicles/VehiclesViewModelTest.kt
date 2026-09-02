package com.hopcape.odo.web.admin.presentation.vehicles

import arrow.core.Either
import arrow.core.right
import com.hopcape.odo.web.admin.domain.VehicleMake
import com.hopcape.odo.web.admin.domain.VehicleModel
import com.hopcape.odo.web.admin.domain.VehicleSubmission
import com.hopcape.odo.web.admin.domain.VehiclesRepository
import com.hopcape.odo.web.core.domain.WebError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VehiclesViewModelTest {

    private class FakeVehicles(
        var makes: List<VehicleMake> = emptyList(),
        var models: List<VehicleModel> = emptyList(),
        var submissions: List<VehicleSubmission> = emptyList(),
    ) : VehiclesRepository {
        val calls = mutableListOf<String>()
        var nextResult: Either<WebError, Unit> = Unit.right()

        override suspend fun makes() = makes.right()
        override suspend fun models() = models.right()
        override suspend fun submissions() = submissions.right()
        override suspend fun add(make: String, model: String, variant: String?) = record("add:$make|$model|$variant")
        override suspend fun renameMake(id: String, name: String) = record("renameMake:$id|$name")
        override suspend fun editModel(id: String, name: String, variant: String?) = record("editModel:$id|$name|$variant")
        override suspend fun deleteMake(id: String) = record("deleteMake:$id")
        override suspend fun deleteModel(id: String) = record("deleteModel:$id")
        override suspend fun decideSubmission(id: String, accepted: Boolean) = record("decide:$id|$accepted")
        override suspend fun deleteSubmission(id: String) = record("deleteSub:$id")

        private fun record(call: String): Either<WebError, Unit> {
            calls += call
            return nextResult
        }
    }

    private val tata = VehicleMake("make-tata", "Tata", 0)
    private val maruti = VehicleMake("make-maruti", "Maruti", 1)
    private val nexonBase = VehicleModel("model-tata-nexon", "make-tata", "Nexon", null, 0)
    private val nexonXz = VehicleModel("model-tata-nexon-xz", "make-tata", "Nexon", "XZ", 1)
    private val swift = VehicleModel("model-maruti-swift", "make-maruti", "Swift", null, 0)

    private fun vm(repo: FakeVehicles) = VehiclesViewModel(repo)

    private fun loaded() = FakeVehicles(
        makes = listOf(tata, maruti),
        models = listOf(nexonBase, nexonXz, swift),
    )

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `models are grouped under their make`() = runTest {
        val state = vm(loaded()).state.value
        assertEquals(listOf("Tata", "Maruti"), state.groups.map { it.make.name })
        assertEquals(listOf("Nexon", "Nexon"), state.groups.first().models.map { it.name })
        assertEquals(3, state.modelCount)
    }

    /** Typing a make's name should show everything it makes, not nothing. */
    @Test
    fun `searching a make keeps all of its models`() = runTest {
        val model = vm(loaded())
        model.onEvent(VehiclesEvent.SearchChanged("tata"))

        val groups = model.state.value.groups
        assertEquals(listOf("Tata"), groups.map { it.make.name })
        assertEquals(2, groups.first().models.size)
    }

    @Test
    fun `searching a model narrows to the matching rows across makes`() = runTest {
        val model = vm(loaded())
        model.onEvent(VehiclesEvent.SearchChanged("swift"))

        val groups = model.state.value.groups
        assertEquals(listOf("Maruti"), groups.map { it.make.name })
        assertEquals(listOf("Swift"), groups.first().models.map { it.name })
    }

    @Test
    fun `a trim matches too`() = runTest {
        val model = vm(loaded())
        model.onEvent(VehiclesEvent.SearchChanged("xz"))
        assertEquals(listOf("XZ"), model.state.value.groups.single().models.map { it.variant })
    }

    @Test
    fun `the make filter narrows without a search term`() = runTest {
        val model = vm(loaded())
        model.onEvent(VehiclesEvent.MakeSelected("make-maruti"))
        assertEquals(listOf("Maruti"), model.state.value.groups.map { it.make.name })
    }

    /**
     * A make with no models still has to be listed, or one added by mistake could
     * never be found and so never removed.
     */
    @Test
    fun `a make with no models is still listed`() = runTest {
        val repo = FakeVehicles(makes = listOf(tata), models = emptyList())
        assertEquals(listOf("Tata"), vm(repo).state.value.groups.map { it.make.name })
    }

    /**
     * upsert_vehicle_catalog_entry guards every insert with an existence check, so
     * the database would accept this and change nothing while reporting success.
     */
    @Test
    fun `adding something already listed is refused rather than silently doing nothing`() = runTest {
        val repo = loaded()
        val model = vm(repo)

        model.onEvent(VehiclesEvent.AddRequested)
        model.onEvent(VehiclesEvent.EditorMakeChanged("tata"))
        model.onEvent(VehiclesEvent.EditorModelChanged("nexon"))
        model.onEvent(VehiclesEvent.EditorVariantChanged("xz"))
        model.onEvent(VehiclesEvent.EditorSubmitted)

        assertTrue(repo.calls.isEmpty())
        assertNotNull(model.state.value.editor?.error)
    }

    /** A trim is a different row from the trim-less one, so this is not a clash. */
    @Test
    fun `adding a new trim to an existing model is allowed`() = runTest {
        val repo = loaded()
        val model = vm(repo)

        model.onEvent(VehiclesEvent.AddRequested)
        model.onEvent(VehiclesEvent.EditorMakeChanged("Tata"))
        model.onEvent(VehiclesEvent.EditorModelChanged("Nexon"))
        model.onEvent(VehiclesEvent.EditorVariantChanged("XM"))
        model.onEvent(VehiclesEvent.EditorSubmitted)

        assertEquals(listOf("add:Tata|Nexon|XM"), repo.calls)
    }

    @Test
    fun `a blank trim is sent as null rather than an empty string`() = runTest {
        val repo = FakeVehicles()
        val model = vm(repo)

        model.onEvent(VehiclesEvent.AddRequested)
        model.onEvent(VehiclesEvent.EditorMakeChanged("Kia"))
        model.onEvent(VehiclesEvent.EditorModelChanged("Seltos"))
        model.onEvent(VehiclesEvent.EditorVariantChanged("   "))
        model.onEvent(VehiclesEvent.EditorSubmitted)

        assertEquals(listOf("add:Kia|Seltos|null"), repo.calls)
    }

    /** Nothing removes a row on one click. */
    @Test
    fun `deleting asks first`() = runTest {
        val repo = loaded()
        val model = vm(repo)

        model.onEvent(VehiclesEvent.DeleteRequested(DeleteTarget.Model(swift)))
        assertTrue(repo.calls.isEmpty())
        assertNotNull(model.state.value.pendingDelete)

        model.onEvent(VehiclesEvent.DeleteConfirmed)
        assertEquals(listOf("deleteModel:model-maruti-swift"), repo.calls)
        assertNull(model.state.value.pendingDelete)
    }

    @Test
    fun `dismissing a delete does nothing`() = runTest {
        val repo = loaded()
        val model = vm(repo)

        model.onEvent(VehiclesEvent.DeleteRequested(DeleteTarget.Make(tata, 2)))
        model.onEvent(VehiclesEvent.DeleteDismissed)

        assertTrue(repo.calls.isEmpty())
        assertNull(model.state.value.pendingDelete)
    }

    /** The count is what makes the warning honest about the cascade. */
    @Test
    fun `deleting a make carries its model count`() = runTest {
        val model = vm(loaded())
        model.onEvent(VehiclesEvent.DeleteRequested(DeleteTarget.Make(tata, 2)))

        val target = model.state.value.pendingDelete
        assertTrue(target is DeleteTarget.Make && target.modelCount == 2)
        assertEquals("Tata", target?.label)
    }

    /** Unlike a city, nothing has to be filled in first. */
    @Test
    fun `approving a submission is one call`() = runTest {
        val repo = FakeVehicles(
            submissions = listOf(
                VehicleSubmission("sub-1", "Tata", "Sierra", null, "pending", "2026-08-01"),
            ),
        )
        val model = vm(repo)

        assertEquals(1, model.state.value.pending.size)
        model.onEvent(VehiclesEvent.SubmissionApproved(repo.submissions.first()))
        assertEquals(listOf("decide:sub-1|true"), repo.calls)
    }

    @Test
    fun `the queue ignores anything already decided`() = runTest {
        val repo = FakeVehicles(
            submissions = listOf(
                VehicleSubmission("sub-1", "Tata", "Sierra", null, "pending", "2026-08-01"),
                VehicleSubmission("sub-2", "Junk", "Junk", null, "rejected", "2026-08-01"),
            ),
        )
        assertEquals(listOf("sub-1"), vm(repo).state.value.pending.map { it.id })
    }

    @Test
    fun `a failed write keeps the editor open`() = runTest {
        val repo = FakeVehicles()
        repo.nextResult = Either.Left(WebError.Offline)
        val model = vm(repo)

        model.onEvent(VehiclesEvent.AddRequested)
        model.onEvent(VehiclesEvent.EditorMakeChanged("Kia"))
        model.onEvent(VehiclesEvent.EditorModelChanged("Seltos"))
        model.onEvent(VehiclesEvent.EditorSubmitted)

        assertNotNull(model.state.value.editor)
        assertNotNull(model.state.value.message)
    }
}

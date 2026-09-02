package com.hopcape.odo.web.admin.presentation.cities

import arrow.core.Either
import arrow.core.right
import com.hopcape.odo.web.admin.domain.CitiesRepository
import com.hopcape.odo.web.admin.domain.City
import com.hopcape.odo.web.admin.domain.CitySubmission
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CitiesViewModelTest {

    /** Records what was asked of it, and answers from a list the test controls. */
    private class FakeCities(
        var cities: List<City> = emptyList(),
        var submissions: List<CitySubmission> = emptyList(),
    ) : CitiesRepository {
        val calls = mutableListOf<String>()
        var nextResult: Either<WebError, Unit> = Unit.right()

        override suspend fun cities() = cities.right()
        override suspend fun submissions() = submissions.right()

        override suspend fun add(name: String, state: String, tier: Int) =
            record("add:$name|$state|$tier")

        override suspend fun edit(id: String, name: String, state: String, tier: Int) =
            record("edit:$id|$name|$state|$tier")

        override suspend fun setActive(id: String, active: Boolean) = record("active:$id|$active")

        override suspend fun decideSubmission(id: String, accepted: Boolean, state: String?, tier: Int?) =
            record("decide:$id|$accepted|$state|$tier")

        override suspend fun deleteSubmission(id: String) = record("delete:$id")

        private fun record(call: String): Either<WebError, Unit> {
            calls += call
            return nextResult
        }
    }

    private fun city(name: String, state: String = "Maharashtra", active: Boolean = true) =
        City(id = "id-$name", name = name, state = state, tier = 3, isActive = active)

    private fun submission(name: String, status: String = "pending") =
        CitySubmission(id = "sub-$name", name = name, state = null, tier = null, status = status, createdAt = "2026-08-01")

    @BeforeTest fun setUp() = Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `retired cities are hidden until asked for`() = runTest {
        val repo = FakeCities(cities = listOf(city("Pune"), city("Old Town", active = false)))
        val vm = CitiesViewModel(repo)

        assertEquals(listOf("Pune"), vm.state.value.visible.map { it.name })

        vm.onEvent(CitiesEvent.RetiredVisibilityToggled)
        assertEquals(listOf("Pune", "Old Town"), vm.state.value.visible.map { it.name })
    }

    @Test
    fun `search matches a name or a state, ignoring case`() = runTest {
        val repo = FakeCities(cities = listOf(city("Pune"), city("Kochi", state = "Kerala")))
        val vm = CitiesViewModel(repo)

        vm.onEvent(CitiesEvent.SearchChanged("pun"))
        assertEquals(listOf("Pune"), vm.state.value.visible.map { it.name })

        vm.onEvent(CitiesEvent.SearchChanged("KERALA"))
        assertEquals(listOf("Kochi"), vm.state.value.visible.map { it.name })
    }

    @Test
    fun `the queue shows only what is still pending`() = runTest {
        val repo = FakeCities(submissions = listOf(submission("Srinagar"), submission("Junk", status = "rejected")))
        val vm = CitiesViewModel(repo)

        assertEquals(listOf("Srinagar"), vm.state.value.pending.map { it.name })
    }

    /**
     * The unique index would catch this, but only after a round trip and with a
     * message about the request rather than the field.
     */
    @Test
    fun `a duplicate name is refused before it is sent`() = runTest {
        val repo = FakeCities(cities = listOf(city("Pune")))
        val vm = CitiesViewModel(repo)

        vm.onEvent(CitiesEvent.AddRequested)
        vm.onEvent(CitiesEvent.EditorNameChanged("pune"))
        vm.onEvent(CitiesEvent.EditorStateChanged("Maharashtra"))
        vm.onEvent(CitiesEvent.EditorSubmitted)

        assertTrue(repo.calls.isEmpty(), "the write should not have been attempted")
        assertNotNull(vm.state.value.editor?.nameError)
    }

    /** Renaming a city to its own name is not a clash with itself. */
    @Test
    fun `editing a city does not collide with its own name`() = runTest {
        val repo = FakeCities(cities = listOf(city("Pune")))
        val vm = CitiesViewModel(repo)

        vm.onEvent(CitiesEvent.EditRequested(repo.cities.first()))
        vm.onEvent(CitiesEvent.EditorStateChanged("Maharashtra"))
        vm.onEvent(CitiesEvent.EditorSubmitted)

        assertEquals(listOf("edit:id-Pune|Pune|Maharashtra|3"), repo.calls)
    }

    /**
     * `cities.state` is NOT NULL and the promote trigger silently does nothing
     * without one, so a blank state has to be stopped here rather than becoming a
     * write that reports success and changes nothing.
     */
    @Test
    fun `approving without a state is refused`() = runTest {
        val repo = FakeCities(submissions = listOf(submission("Srinagar")))
        val vm = CitiesViewModel(repo)

        vm.onEvent(CitiesEvent.ApproveRequested(repo.submissions.first()))
        vm.onEvent(CitiesEvent.EditorSubmitted)

        assertTrue(repo.calls.isEmpty())
        assertNotNull(vm.state.value.editor?.stateError)
    }

    @Test
    fun `approving sends the state and tier alongside the status`() = runTest {
        val repo = FakeCities(submissions = listOf(submission("Srinagar")))
        val vm = CitiesViewModel(repo)

        vm.onEvent(CitiesEvent.ApproveRequested(repo.submissions.first()))
        vm.onEvent(CitiesEvent.EditorStateChanged("Jammu and Kashmir"))
        vm.onEvent(CitiesEvent.EditorTierChanged(2))
        vm.onEvent(CitiesEvent.EditorSubmitted)

        assertEquals(listOf("decide:sub-Srinagar|true|Jammu and Kashmir|2"), repo.calls)
        assertNull(vm.state.value.editor, "a successful save closes the editor")
    }

    @Test
    fun `rejecting sends no state`() = runTest {
        val repo = FakeCities(submissions = listOf(submission("Junk")))
        val vm = CitiesViewModel(repo)

        vm.onEvent(CitiesEvent.SubmissionRejected(repo.submissions.first()))
        assertEquals(listOf("decide:sub-Junk|false|null|null"), repo.calls)
    }

    @Test
    fun `retiring flips the flag rather than deleting`() = runTest {
        val repo = FakeCities(cities = listOf(city("Pune")))
        val vm = CitiesViewModel(repo)

        vm.onEvent(CitiesEvent.ActiveToggled(repo.cities.first()))
        assertEquals(listOf("active:id-Pune|false"), repo.calls)
    }

    /** Losing a state somebody just looked up because the save failed is not on. */
    @Test
    fun `a failed save keeps the editor and what was typed in it`() = runTest {
        val repo = FakeCities()
        repo.nextResult = Either.Left(WebError.Offline)
        val vm = CitiesViewModel(repo)

        vm.onEvent(CitiesEvent.AddRequested)
        vm.onEvent(CitiesEvent.EditorNameChanged("Srinagar"))
        vm.onEvent(CitiesEvent.EditorStateChanged("Jammu and Kashmir"))
        vm.onEvent(CitiesEvent.EditorSubmitted)

        val editor = vm.state.value.editor
        assertNotNull(editor)
        assertEquals("Srinagar", editor.name.value)
        assertEquals("Jammu and Kashmir", editor.state.value)
        assertNotNull(vm.state.value.message)
    }

    /** The index got there first — say so on the field, not only in the banner. */
    @Test
    fun `a conflict from the server marks the name field`() = runTest {
        val repo = FakeCities()
        repo.nextResult = Either.Left(WebError.Conflict)
        val vm = CitiesViewModel(repo)

        vm.onEvent(CitiesEvent.AddRequested)
        vm.onEvent(CitiesEvent.EditorNameChanged("Srinagar"))
        vm.onEvent(CitiesEvent.EditorStateChanged("Jammu and Kashmir"))
        vm.onEvent(CitiesEvent.EditorSubmitted)

        assertNotNull(vm.state.value.editor?.nameError)
    }
}

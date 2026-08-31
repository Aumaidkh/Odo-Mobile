package com.hopcape.odo.web.admin.presentation.users

import arrow.core.Either
import arrow.core.right
import com.hopcape.odo.web.admin.domain.ManagedUser
import com.hopcape.odo.web.admin.domain.Restriction
import com.hopcape.odo.web.admin.domain.UsersRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UsersViewModelTest {

    private class FakeUsers(var found: ManagedUser? = null) : UsersRepository {
        val calls = mutableListOf<String>()
        var nextResult: Either<WebError, Unit> = Unit.right()
        override suspend fun find(query: String): Either<WebError, ManagedUser?> {
            calls += "find:$query"
            return found.right()
        }
        override suspend fun setEntitlement(ownerId: String, feature: String, granted: Boolean, reason: String) =
            record("entitlement:$ownerId|$feature|$granted|$reason")
        override suspend fun clearEntitlement(ownerId: String, feature: String) = record("clear:$ownerId|$feature")
        override suspend fun setRestriction(ownerId: String, restriction: Restriction, reason: String?) =
            record("restrict:$ownerId|${restriction.id}|$reason")
        private fun record(call: String): Either<WebError, Unit> { calls += call; return nextResult }
    }

    private fun user(restriction: Restriction = Restriction.None) = ManagedUser(
        id = "owner-1", phone = "+919000000000", email = null,
        restriction = restriction, restrictionReason = null,
        createdAt = "2026-01-01", entitlements = emptyList(),
    )

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the form starts from what is stored, so apply stays off until something changes`() = runTest {
        val repo = FakeUsers(user(Restriction.ReadOnly))
        val vm = UsersViewModel(repo)

        vm.onEvent(UsersEvent.QueryChanged("+919000000000"))
        vm.onEvent(UsersEvent.Search)

        assertEquals(Restriction.ReadOnly, vm.state.value.proposed)
        assertFalse(vm.state.value.restrictionChanged)

        vm.onEvent(UsersEvent.RestrictionPicked(Restriction.Blocked))
        assertTrue(vm.state.value.restrictionChanged)
    }

    /**
     * The reason is what the audit log shows. An entry saying an account was blocked
     * without saying why is the entry somebody reads in six months, trying to work out
     * whether to undo it.
     */
    @Test
    fun `restricting demands a reason`() = runTest {
        val repo = FakeUsers(user())
        val vm = UsersViewModel(repo)
        vm.onEvent(UsersEvent.QueryChanged("x")); vm.onEvent(UsersEvent.Search)
        repo.calls.clear()

        vm.onEvent(UsersEvent.RestrictionPicked(Restriction.Blocked))
        vm.onEvent(UsersEvent.RestrictionApplied)

        assertTrue(repo.calls.isEmpty())
        assertNotNull(vm.state.value.reasonError)
    }

    /** Lifting one needs none — "not restricted" explains itself. */
    @Test
    fun `lifting a restriction needs no reason`() = runTest {
        val repo = FakeUsers(user(Restriction.Blocked))
        val vm = UsersViewModel(repo)
        vm.onEvent(UsersEvent.QueryChanged("x")); vm.onEvent(UsersEvent.Search)
        repo.calls.clear()

        vm.onEvent(UsersEvent.RestrictionPicked(Restriction.None))
        vm.onEvent(UsersEvent.RestrictionApplied)

        assertEquals("restrict:owner-1|none|null", repo.calls.first())
    }

    @Test
    fun `granting sends the feature upper-cased`() = runTest {
        val repo = FakeUsers(user())
        val vm = UsersViewModel(repo)
        vm.onEvent(UsersEvent.QueryChanged("x")); vm.onEvent(UsersEvent.Search)
        repo.calls.clear()

        vm.onEvent(UsersEvent.FeatureChanged("pro"))
        vm.onEvent(UsersEvent.EntitlementSet(granted = true))

        assertTrue(repo.calls.first().startsWith("entitlement:owner-1|PRO|true|"))
    }

    @Test
    fun `nothing is attempted before an account has been found`() = runTest {
        val repo = FakeUsers(null)
        val vm = UsersViewModel(repo)

        vm.onEvent(UsersEvent.RestrictionPicked(Restriction.Blocked))
        vm.onEvent(UsersEvent.RestrictionApplied)
        vm.onEvent(UsersEvent.EntitlementSet(granted = true))

        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun `a search that finds nothing says so`() = runTest {
        val repo = FakeUsers(null)
        val vm = UsersViewModel(repo)
        vm.onEvent(UsersEvent.QueryChanged("nobody")); vm.onEvent(UsersEvent.Search)

        assertTrue(vm.state.value.searched)
        assertNotNull(vm.state.value.message)
    }
}

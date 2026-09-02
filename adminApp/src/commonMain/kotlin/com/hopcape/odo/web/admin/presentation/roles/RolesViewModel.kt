package com.hopcape.odo.web.admin.presentation.roles

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.web.admin.domain.AdminRole
import com.hopcape.odo.web.admin.domain.Permission
import com.hopcape.odo.web.admin.domain.RolesRepository
import com.hopcape.odo.web.admin.domain.StaffMember
import com.hopcape.odo.web.admin.presentation.asUiText
import com.hopcape.odo.web.admin.presentation.readAll
import com.hopcape.odo.web.admin.presentation.readInto
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_staff_invited
import com.hopcape.odo.web.core.presentation.state.Loadable
import com.hopcape.odo.web.core.presentation.state.UiText
import com.hopcape.odo.web.core.presentation.state.valueOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RolesEvent {
    data object Refresh : RolesEvent

    /**
     * Who is looking, so the screen can refuse to lock them out.
     *
     * Pushed in rather than read from a session here: this view model is given a
     * repository and nothing else, and a second route to the session would be a
     * second thing to keep in step with the real one.
     */
    data class SelfIdentified(val adminId: String) : RolesEvent
    /** Click a cell to grant or revoke it. */
    data class GrantToggled(val roleSlug: String, val permission: Permission) : RolesEvent
    data class RoleSelected(val slug: String) : RolesEvent

    /** Adding a role. Slug, name, description — permissions come from the grid. */
    data object NewRoleRequested : RolesEvent
    data object NewRoleDismissed : RolesEvent
    data class NewRoleSlugChanged(val value: String) : RolesEvent
    data class NewRoleNameChanged(val value: String) : RolesEvent
    data class NewRoleDescriptionChanged(val value: String) : RolesEvent
    data object NewRoleSubmitted : RolesEvent

    /** Adding somebody to the allowlist. */
    data object NewStaffRequested : RolesEvent
    data object NewStaffDismissed : RolesEvent
    data class NewStaffEmailChanged(val value: String) : RolesEvent
    data class NewStaffNameChanged(val value: String) : RolesEvent
    data object NewStaffSubmitted : RolesEvent

    data class StaffRoleToggled(val member: StaffMember, val roleSlug: String) : RolesEvent
    data class StaffActiveToggled(val member: StaffMember) : RolesEvent

    /** Creates their Firebase account if needed and emails them a link to set a password. */
    data class InviteSent(val member: StaffMember) : RolesEvent

    data object MessageDismissed : RolesEvent
}

/** The add-a-role form. Null when it is not open. */
@Immutable
data class NewRole(val slug: String = "", val name: String = "", val description: String = "") {
    /**
     * The slug has to match what the database will accept, checked here so the
     * refusal arrives while somebody is typing rather than as a constraint
     * violation after they press the button.
     */
    val slugError: Boolean get() = slug.isNotEmpty() && !SLUG.matches(slug)
    val canSubmit: Boolean get() = slug.isNotBlank() && name.isNotBlank() && !slugError

    private companion object {
        val SLUG = Regex("^[a-z][a-z0-9_]*$")
    }
}

/** The add-a-person form. Null when it is not open. */
@Immutable
data class NewStaff(val email: String = "", val name: String = "") {
    /**
     * Deliberately loose. The address is checked properly by the one thing that
     * can check it — whether a sign-in ever arrives for it — and a regex that
     * rejects a valid address is worse than one that lets a typo through, because
     * the typo is visible in the list and the rejection is not.
     */
    val canSubmit: Boolean get() = email.contains('@') && email.trim().length > 2
}

@Immutable
data class RolesUiState(
    val roles: Loadable<List<AdminRole>> = Loadable.Loading,
    val grants: Set<Pair<String, String>> = emptySet(),
    val staff: List<StaffMember> = emptyList(),
    val selectedRole: String? = null,
    val newRole: NewRole? = null,
    val newStaff: NewStaff? = null,
    /** The `admin_users` id of whoever is looking, so the screen can refuse self-harm. */
    val selfId: String? = null,
    val busy: Boolean = false,
    val message: UiText? = null,
) {
    val allRoles: List<AdminRole> get() = roles.valueOrNull.orEmpty()

    val selected: AdminRole?
        get() = allRoles.firstOrNull { it.slug == selectedRole } ?: allRoles.firstOrNull()

    fun isGranted(roleSlug: String, permission: Permission): Boolean =
        (roleSlug to permission.id) in grants

    /**
     * Whether this row is the person looking at it.
     *
     * Used to refuse two things that lock somebody out of the panel with no way
     * back: deactivating yourself, and dropping your own last role. Both are
     * recoverable only by editing the database by hand, which on production means
     * a support ticket to get back into the tool that answers support tickets.
     */
    fun isSelf(member: StaffMember): Boolean = member.id == selfId

    fun mayRevokeRole(member: StaffMember, roleSlug: String): Boolean =
        !(isSelf(member) && member.roles == listOf(roleSlug))

    fun mayDeactivate(member: StaffMember): Boolean = !isSelf(member)
}

/**
 * The permission grid.
 *
 * Every cell is a row in `admin_role_permissions`, so clicking one is an insert or
 * a delete rather than a staged edit with a save button. That is deliberate: a
 * grid with a save button invites somebody to change six things and lose five of
 * them, and the audit log records each change on its own either way.
 */
class RolesViewModel(
    private val repository: RolesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RolesUiState())
    val state: StateFlow<RolesUiState> = _state.asStateFlow()

    private val roles = MutableStateFlow<Loadable<List<AdminRole>>>(Loadable.Loading)

    init {
        viewModelScope.launch { roles.collect { v -> _state.value = _state.value.copy(roles = v) } }
        load()
    }

    fun onEvent(event: RolesEvent) {
        when (event) {
            RolesEvent.Refresh -> load()
            is RolesEvent.SelfIdentified -> _state.value = _state.value.copy(selfId = event.adminId)
            is RolesEvent.RoleSelected -> _state.value = _state.value.copy(selectedRole = event.slug)
            is RolesEvent.GrantToggled -> toggle(event.roleSlug, event.permission)

            RolesEvent.NewRoleRequested -> _state.value = _state.value.copy(newRole = NewRole())
            RolesEvent.NewRoleDismissed -> _state.value = _state.value.copy(newRole = null)
            is RolesEvent.NewRoleSlugChanged -> editRole { it.copy(slug = event.value.trim()) }
            is RolesEvent.NewRoleNameChanged -> editRole { it.copy(name = event.value) }
            is RolesEvent.NewRoleDescriptionChanged -> editRole { it.copy(description = event.value) }
            RolesEvent.NewRoleSubmitted -> {
                val form = _state.value.newRole ?: return
                if (!form.canSubmit) return
                write { repository.createRole(form.slug, form.name.trim(), form.description.trim()) }
            }

            RolesEvent.NewStaffRequested -> _state.value = _state.value.copy(newStaff = NewStaff())
            RolesEvent.NewStaffDismissed -> _state.value = _state.value.copy(newStaff = null)
            is RolesEvent.NewStaffEmailChanged -> editStaff { it.copy(email = event.value) }
            is RolesEvent.NewStaffNameChanged -> editStaff { it.copy(name = event.value) }
            RolesEvent.NewStaffSubmitted -> {
                val form = _state.value.newStaff ?: return
                if (!form.canSubmit) return
                val email = form.email.trim().lowercase()
                // Added and invited in one go. They are two calls because they are
                // two systems — a row in Postgres, an account in Firebase — but
                // splitting them in the UI is what produced somebody on the staff
                // list with no way to sign in.
                write(message = Res.string.ad_staff_invited) {
                    repository.addStaff(email, form.name).flatMap { repository.invite(email) }
                }
            }

            is RolesEvent.InviteSent ->
                write(message = Res.string.ad_staff_invited) { repository.invite(event.member.email) }

            is RolesEvent.StaffRoleToggled -> {
                val held = event.roleSlug in event.member.roles
                if (held && !_state.value.mayRevokeRole(event.member, event.roleSlug)) return
                write { repository.setStaffRole(event.member.id, event.roleSlug, held = !held) }
            }

            is RolesEvent.StaffActiveToggled -> {
                if (!_state.value.mayDeactivate(event.member)) return
                write { repository.setStaffActive(event.member.id, !event.member.isActive) }
            }

            RolesEvent.MessageDismissed -> _state.value = _state.value.copy(message = null)
        }
    }

    private fun load() = readAll(
        { busy -> _state.value = _state.value.copy(busy = busy) },
        { readInto(roles) { repository.roles() } },
        { repository.grants().onRight { _state.value = _state.value.copy(grants = it) } },
        { repository.staff().onRight { _state.value = _state.value.copy(staff = it) } },
    )

    private fun editRole(block: (NewRole) -> NewRole) {
        _state.value = _state.value.copy(newRole = _state.value.newRole?.let(block))
    }

    private fun editStaff(block: (NewStaff) -> NewStaff) {
        _state.value = _state.value.copy(newStaff = _state.value.newStaff?.let(block))
    }

    /**
     * Every write, then a re-read.
     *
     * Re-reading rather than patching the local list: role membership counts, the
     * grid and the staff list are three views of the same rows, and updating one of
     * them by hand is how they start disagreeing.
     */
    private fun write(
        message: org.jetbrains.compose.resources.StringResource? = null,
        action: suspend () -> arrow.core.Either<com.hopcape.odo.web.core.domain.WebError, Unit>,
    ) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            action().fold(
                ifLeft = { error -> _state.value = _state.value.copy(busy = false, message = error.asUiText()) },
                ifRight = {
                    _state.value = _state.value.copy(
                        busy = false,
                        newRole = null,
                        newStaff = null,
                        message = message?.let(UiText::Resource),
                    )
                    load()
                },
            )
        }
    }


    private fun toggle(roleSlug: String, permission: Permission) {
        if (_state.value.busy) return
        val granted = _state.value.isGranted(roleSlug, permission)
        _state.value = _state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            repository.setGrant(roleSlug, permission.id, granted = !granted).fold(
                ifLeft = { error -> _state.value = _state.value.copy(busy = false, message = error.asUiText()) },
                ifRight = {
                    _state.value = _state.value.copy(busy = false)
                    // Re-read rather than flip the local set: what the grid now
                    // says is the server's answer, and a super-admin revoking
                    // their own last permission should see that immediately.
                    load()
                },
            )
        }
    }
}

/** Runs the second call only if the first succeeded, keeping the first's error. */
private inline fun <A> arrow.core.Either<com.hopcape.odo.web.core.domain.WebError, A>.flatMap(
    block: (A) -> arrow.core.Either<com.hopcape.odo.web.core.domain.WebError, Unit>,
): arrow.core.Either<com.hopcape.odo.web.core.domain.WebError, Unit> = fold({ arrow.core.Either.Left(it) }, block)

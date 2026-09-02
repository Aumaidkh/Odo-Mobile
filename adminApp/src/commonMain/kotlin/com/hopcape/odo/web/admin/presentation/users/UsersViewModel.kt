package com.hopcape.odo.web.admin.presentation.users

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import com.hopcape.odo.web.admin.domain.DirectoryUser
import com.hopcape.odo.web.admin.domain.ManagedUser
import com.hopcape.odo.web.admin.domain.Restriction
import com.hopcape.odo.web.admin.domain.UsersRepository
import com.hopcape.odo.web.admin.presentation.asUiText
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_users_cleared
import com.hopcape.odo.web.admin.resources.ad_users_none
import com.hopcape.odo.web.admin.resources.ad_users_reason_required
import com.hopcape.odo.web.admin.resources.ad_users_restriction_saved
import com.hopcape.odo.web.admin.resources.ad_users_saved
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.core.presentation.state.FormField
import com.hopcape.odo.web.core.presentation.state.UiText
import com.hopcape.odo.web.core.presentation.state.textField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

sealed interface UsersEvent {
    /** Open one account from the directory. */
    data class Opened(val id: String) : UsersEvent
    data object Closed : UsersEvent
    data class Revealed(val id: String) : UsersEvent
    data object NextPage : UsersEvent
    data object PreviousPage : UsersEvent
    data class QueryChanged(val value: String) : UsersEvent
    data object Search : UsersEvent
    data class RestrictionPicked(val restriction: Restriction) : UsersEvent
    data class ReasonChanged(val value: String) : UsersEvent
    data object RestrictionApplied : UsersEvent
    data class FeatureChanged(val value: String) : UsersEvent
    data class EntitlementSet(val granted: Boolean) : UsersEvent
    data class EntitlementCleared(val feature: String) : UsersEvent
    data object MessageDismissed : UsersEvent
}

@Immutable
data class UsersUiState(
    /** The directory, as the table draws it. */
    val directory: List<DirectoryUser> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    /** Contact details a reveal has unmasked, by account id. Never persisted. */
    val revealed: Map<String, String> = emptyMap(),
    val query: FormField<String> = textField(),
    val user: ManagedUser? = null,
    /** True once a search has run and found nothing, so the empty state is earned. */
    val searched: Boolean = false,
    /** The restriction the form is proposing, which may differ from the stored one. */
    val proposed: Restriction = Restriction.None,
    val reason: FormField<String> = textField(),
    val reasonError: UiText? = null,
    val feature: FormField<String> = textField("PRO"),
    val busy: Boolean = false,
    val message: UiText? = null,
) {
    val canSearch: Boolean get() = !busy && query.value.isNotBlank()
    val restrictionChanged: Boolean get() = user != null && proposed != user.restriction

    val firstShown: Int get() = if (directory.isEmpty()) 0 else page * PAGE_SIZE + 1
    val lastShown: Int get() = page * PAGE_SIZE + directory.size
    val hasNext: Boolean get() = lastShown < total
    val hasPrevious: Boolean get() = page > 0

    internal companion object {
        /** A screenful. The design shows a page, not an infinite scroll. */
        const val PAGE_SIZE = 25
    }
}

/**
 * Finding one account and changing what it may do.
 *
 * Every write re-reads the account rather than editing the state in place. The
 * restriction and the overrides are two tables and a trigger, and the only client
 * that knows what they add up to afterwards is one that asks again.
 */
class UsersViewModel(
    private val users: UsersRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UsersUiState())
    val state: StateFlow<UsersUiState> = _state.asStateFlow()

    fun onEvent(event: UsersEvent) {
        when (event) {
            is UsersEvent.QueryChanged -> {
                _state.value = _state.value.copy(query = _state.value.query.update(event.value))
                // The header's box drives the directory directly. Typing narrows the
                // list; Enter is only needed for the exact-match jump below.
                loadPage(0)
            }

            UsersEvent.Search -> search()

            is UsersEvent.Opened -> open(event.id)
            UsersEvent.Closed -> _state.value = _state.value.copy(user = null, searched = false)
            is UsersEvent.Revealed -> reveal(event.id)
            UsersEvent.NextPage -> if (_state.value.hasNext) loadPage(_state.value.page + 1)
            UsersEvent.PreviousPage -> if (_state.value.hasPrevious) loadPage(_state.value.page - 1)

            is UsersEvent.RestrictionPicked ->
                _state.value = _state.value.copy(proposed = event.restriction, reasonError = null)

            is UsersEvent.ReasonChanged ->
                _state.value = _state.value.copy(reason = _state.value.reason.update(event.value), reasonError = null)

            UsersEvent.RestrictionApplied -> applyRestriction()

            is UsersEvent.FeatureChanged ->
                _state.value = _state.value.copy(feature = _state.value.feature.update(event.value))

            is UsersEvent.EntitlementSet -> setEntitlement(event.granted)

            is UsersEvent.EntitlementCleared -> {
                val owner = _state.value.user?.id ?: return
                write(Res.string.ad_users_cleared) { users.clearEntitlement(owner, event.feature) }
            }

            UsersEvent.MessageDismissed -> _state.value = _state.value.copy(message = null)
        }
    }

    init {
        loadPage(0)
    }

    /**
     * The directory.
     *
     * Re-read from page zero whenever the search term changes: a term that matched
     * on page three has no page three.
     */
    private fun loadPage(page: Int) {
        viewModelScope.launch {
            users.list(_state.value.query.value.trim(), UsersUiState.PAGE_SIZE, page * UsersUiState.PAGE_SIZE).fold(
                ifLeft = { error -> _state.value = _state.value.copy(message = error.asUiText()) },
                ifRight = { result ->
                    _state.value = _state.value.copy(directory = result.rows, total = result.total, page = page)
                },
            )
        }
    }

    /** Open one account, by id, from a directory row. */
    private fun open(id: String) {
        _state.value = _state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            users.find(id).fold(
                ifLeft = { error -> _state.value = _state.value.copy(busy = false, message = error.asUiText()) },
                ifRight = { found ->
                    _state.value = _state.value.copy(
                        busy = false,
                        user = found,
                        searched = true,
                        proposed = found?.restriction ?: Restriction.None,
                        reason = textField(found?.restrictionReason.orEmpty()),
                    )
                },
            )
        }
    }

    /**
     * Unmask one account's contact details.
     *
     * The server writes the audit row before it answers, so there is nothing to
     * log here — and nothing this client could skip.
     */
    private fun reveal(id: String) {
        viewModelScope.launch {
            users.reveal(id).onRight { contact ->
                val shown = listOfNotNull(contact?.phone, contact?.email).joinToString(" · ")
                if (shown.isNotEmpty()) {
                    _state.value = _state.value.copy(revealed = _state.value.revealed + (id to shown))
                }
            }
        }
    }

    private fun search() {
        if (!_state.value.canSearch) return
        _state.value = _state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            users.find(_state.value.query.value.trim()).fold(
                ifLeft = { error ->
                    _state.value = _state.value.copy(busy = false, message = error.asUiText())
                },
                ifRight = { found ->
                    _state.value = _state.value.copy(
                        busy = false,
                        user = found,
                        searched = true,
                        // The form starts from what is stored, so "apply" is only
                        // ever enabled once somebody has actually changed something.
                        proposed = found?.restriction ?: Restriction.None,
                        reason = textField(found?.restrictionReason.orEmpty()),
                        message = if (found == null) UiText.Resource(Res.string.ad_users_none) else null,
                    )
                },
            )
        }
    }

    /**
     * Restricting somebody demands a reason.
     *
     * Not validation for its own sake: the reason is what the audit log shows, and
     * an entry that says an account was blocked without saying why is the entry
     * somebody will be reading in six months trying to work out whether to undo it.
     * Lifting a restriction needs none — "not restricted" explains itself.
     */
    private fun applyRestriction() {
        val user = _state.value.user ?: return
        val proposed = _state.value.proposed
        val reason = _state.value.reason.value.trim()
        if (proposed != Restriction.None && reason.isBlank()) {
            _state.value = _state.value.copy(reasonError = UiText.Resource(Res.string.ad_users_reason_required))
            return
        }
        write(Res.string.ad_users_restriction_saved) {
            users.setRestriction(user.id, proposed, reason.ifBlank { null })
        }
    }

    private fun setEntitlement(granted: Boolean) {
        val user = _state.value.user ?: return
        val feature = _state.value.feature.value.trim().uppercase()
        if (feature.isBlank()) return
        val reason = _state.value.reason.value.trim().ifBlank { DEFAULT_REASON }
        write(Res.string.ad_users_saved) {
            users.setEntitlement(user.id, feature, granted, reason)
        }
    }

    private fun write(done: StringResource, action: suspend () -> Either<WebError, Unit>) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            action().fold(
                ifLeft = { error -> _state.value = _state.value.copy(busy = false, message = error.asUiText()) },
                ifRight = {
                    _state.value = _state.value.copy(busy = false, message = UiText.Resource(done))
                    loadPage(_state.value.page)
                    // Re-read rather than patch the state: what the account now
                    // looks like is the server's answer, not this client's guess.
                    search()
                },
            )
        }
    }

    private companion object {
        /** `entitlement_overrides.reason` is NOT NULL, and a blank one helps nobody. */
        const val DEFAULT_REASON = "Granted from the admin panel"
    }
}

package com.hopcape.odo.web.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.web.admin.domain.AdminAuthRepository
import com.hopcape.odo.web.admin.domain.AdminSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Who is signed in, for the whole panel.
 *
 * Page-scoped rather than route-scoped: every section needs the answer, and
 * re-reading it on each navigation would flash the sign-in page between them.
 *
 * [State.Unknown] exists so that flash cannot happen the other way either.
 * Starting at "signed out" would send a signed-in admin to the login screen for as
 * long as the check takes; starting at "signed in" would show the panel's chrome
 * before anyone had checked. Neither is true yet, so the gate draws nothing.
 */
class SessionViewModel(
    private val auth: AdminAuthRepository,
) : ViewModel() {

    sealed interface State {
        /** The first read has not come back. Draw nothing. */
        data object Unknown : State
        data object SignedOut : State
        data class SignedIn(val session: AdminSession) : State
    }

    private val _state = MutableStateFlow<State>(State.Unknown)
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Called after a sign-in, so the gate re-reads instead of being told. */
    fun refresh() {
        viewModelScope.launch {
            // A failure and a null both mean "not signed in" to the gate. They are
            // different to the sign-in screen, which reports why; here the only
            // question is whether to draw the panel, and the answer to both is no.
            val session = auth.session().getOrNull()
            _state.value = if (session == null) State.SignedOut else State.SignedIn(session)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            _state.value = State.SignedOut
        }
    }
}

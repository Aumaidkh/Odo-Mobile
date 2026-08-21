package com.hopcape.odo.web.blog.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.web.blog.domain.AuthRepository
import com.hopcape.odo.web.blog.domain.model.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Who is signed in, for the whole CMS.
 *
 * Page-scoped rather than route-scoped: every admin screen needs the answer, and
 * re-reading it on each navigation would flash the sign-in page between the post
 * list and the editor.
 *
 * [Unknown] exists so that flash cannot happen the other way either. Starting at
 * "signed out" would send a signed-in author to the login screen for as long as
 * the check takes; starting at "signed in" would show them the CMS before anyone
 * had checked. Neither is true yet, so the gate draws nothing until it is.
 */
class SessionViewModel(
    private val auth: AuthRepository,
) : ViewModel() {

    sealed interface State {
        /** The first read has not come back. Draw nothing.  */
        data object Unknown : State
        data object SignedOut : State
        data class SignedIn(val session: Session) : State
    }

    private val _state = MutableStateFlow<State>(State.Unknown)
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Called after a sign-in, so the gate re-reads instead of being told. */
    fun refresh() {
        viewModelScope.launch {
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

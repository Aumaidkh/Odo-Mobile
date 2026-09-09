package com.hopcape.odo.web.blog.presentation.admin.signin

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.web.blog.domain.AuthRepository
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.blog.presentation.asUiText
import com.hopcape.odo.web.core.presentation.state.FormField
import com.hopcape.odo.web.core.presentation.state.UiText
import com.hopcape.odo.web.core.presentation.state.textField
import com.hopcape.odo.web.blog.resources.Res
import com.hopcape.odo.web.blog.resources.bl_admin_wrong_password
import com.hopcape.odo.web.blog.resources.bl_admin_wrong_password_locked
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface SignInEvent {
    data class EmailChanged(val value: String) : SignInEvent
    data class PasswordChanged(val value: String) : SignInEvent
    data object Submit : SignInEvent
}

/**
 * One-shot handoffs.
 *
 * The first place in this module that needs an effect channel. Everywhere else a
 * click leads straight to a navigation the screen can make itself; here the
 * navigation depends on an answer that arrives later, and putting "go to the post
 * list" in the state would make it fire again on every recomposition.
 */
sealed interface SignInEffect {
    data object SignedIn : SignInEffect
}

@Immutable
data class SignInUiState(
    val email: FormField<String>,
    val password: FormField<String>,
    val busy: Boolean,
    /** Sits under the password field, the way the design draws it. */
    val error: UiText?,
) {
    val canSubmit: Boolean
        get() = !busy && email.value.isNotBlank() && password.value.isNotBlank()
}

class SignInViewModel(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SignInUiState(textField(), textField(), busy = false, error = null),
    )
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    private val _effects = Channel<SignInEffect>(Channel.BUFFERED)
    val effects: Flow<SignInEffect> = _effects.receiveAsFlow()

    fun onEvent(event: SignInEvent) {
        when (event) {
            // Typing clears the last rejection. Leaving it up while somebody
            // corrects the password is telling them off for something they are
            // already fixing.
            is SignInEvent.EmailChanged ->
                _state.value = _state.value.copy(email = _state.value.email.update(event.value), error = null)

            is SignInEvent.PasswordChanged ->
                _state.value = _state.value.copy(password = _state.value.password.update(event.value), error = null)

            SignInEvent.Submit -> submit()
        }
    }

    private fun submit() {
        if (!_state.value.canSubmit) return
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            auth.signIn(_state.value.email.value, _state.value.password.value).fold(
                ifLeft = { error ->
                    _state.value = _state.value.copy(
                        busy = false,
                        // The password is cleared, the email is not: a wrong
                        // password means retyping the password, and clearing both
                        // punishes the half that was right.
                        password = textField(),
                        error = error.asSignInText(),
                    )
                },
                ifRight = {
                    _state.value = _state.value.copy(busy = false)
                    _effects.send(SignInEffect.SignedIn)
                },
            )
        }
    }

    /**
     * The countdown the design shows, and everything else as it already reads.
     *
     * Only the rejection is special-cased, because only it has a number in the
     * copy. Everything else goes through the shared mapping — an account that is
     * not an author and a project with password sign-in switched off each have
     * their own line, and collapsing them into "this did not work" would throw
     * away the one thing that tells somebody what to do about it.
     */
    private fun WebError.asSignInText(): UiText {
        if (this !is WebError.SignInRejected) return asUiText()
        // Read into a local first. `triesLeft` now lives in :webCore, and Kotlin
        // will not smart-cast a public property from another module — it cannot
        // prove the value has not changed between the check and the use.
        val remaining = triesLeft
        return when {
            // Zero gets its own line rather than "0 tries left", which reads like a
            // counter that has not been updated yet.
            remaining == null || remaining <= 0 -> UiText.Resource(Res.string.bl_admin_wrong_password_locked)
            else -> UiText.Resource(Res.string.bl_admin_wrong_password, listOf(remaining))
        }
    }
}

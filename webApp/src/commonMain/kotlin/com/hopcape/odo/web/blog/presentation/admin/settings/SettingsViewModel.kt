package com.hopcape.odo.web.blog.presentation.admin.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.web.blog.domain.AdminRepository
import com.hopcape.odo.web.blog.domain.model.Author
import com.hopcape.odo.web.blog.presentation.asUiText
import com.hopcape.odo.web.core.presentation.state.Loadable
import com.hopcape.odo.web.core.presentation.state.Submission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SettingsEvent {
    data class NameChanged(val value: String) : SettingsEvent
    data class BioChanged(val value: String) : SettingsEvent
    data class TopicsChanged(val value: String) : SettingsEvent
    data class SinceChanged(val value: String) : SettingsEvent
    data object Save : SettingsEvent
    data object Retry : SettingsEvent
}

@Immutable
data class SettingsUiState(
    val loaded: Loadable<Author>,
    val name: String,
    val bio: String,
    val topics: String,
    val since: String,
    val saving: Submission,
) {
    /** A byline with no name is the one field that cannot be left blank. */
    val canSave: Boolean get() = name.isNotBlank() && saving != Submission.Sending
}

/**
 * The author's own byline.
 *
 * This is what Settings is for. Everything else a CMS usually puts here — themes,
 * integrations, a danger zone — either does not exist or belongs somewhere it can
 * be found; the one thing an author needs to change about the site is how they
 * appear on it, and until now the author page showed whatever `blog-session`
 * guessed from an email address.
 */
class SettingsViewModel(
    private val admin: AdminRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(Loadable.Loading, "", "", "", "", Submission.Idle),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.NameChanged -> edit { copy(name = event.value) }
            is SettingsEvent.BioChanged -> edit { copy(bio = event.value) }
            is SettingsEvent.TopicsChanged -> edit { copy(topics = event.value) }
            is SettingsEvent.SinceChanged -> edit { copy(since = event.value) }
            SettingsEvent.Save -> save()
            SettingsEvent.Retry -> load()
        }
    }

    /** Typing clears the last outcome, so "Saved" does not sit over an unsaved edit. */
    private fun edit(change: SettingsUiState.() -> SettingsUiState) {
        _state.value = _state.value.change().copy(saving = Submission.Idle)
    }

    private fun load() {
        _state.value = _state.value.copy(loaded = Loadable.Loading)
        viewModelScope.launch {
            admin.profile().fold(
                ifLeft = { _state.value = _state.value.copy(loaded = Loadable.Failed(it.asUiText())) },
                ifRight = { author ->
                    _state.value = _state.value.copy(
                        loaded = Loadable.Ready(author),
                        name = author.name,
                        bio = author.bio,
                        topics = author.topics,
                        since = author.since,
                    )
                },
            )
        }
    }

    private fun save() {
        val current = _state.value
        if (!current.canSave) return
        _state.value = current.copy(saving = Submission.Sending)
        viewModelScope.launch {
            admin.saveProfile(
                name = current.name.trim(),
                bio = current.bio.trim(),
                topics = current.topics.trim(),
                since = current.since.trim(),
            ).fold(
                ifLeft = { _state.value = _state.value.copy(saving = Submission.Failed(it.asUiText())) },
                ifRight = { author ->
                    _state.value = _state.value.copy(
                        loaded = Loadable.Ready(author),
                        saving = Submission.Done,
                    )
                },
            )
        }
    }
}

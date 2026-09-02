package com.hopcape.odo.web.blog.presentation.admin.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.web.blog.domain.AdminRepository
import com.hopcape.odo.web.core.platform.UploadRequest
import com.hopcape.odo.web.blog.domain.model.Analytics
import com.hopcape.odo.web.blog.domain.model.MediaItem
import com.hopcape.odo.web.blog.presentation.asUiText
import com.hopcape.odo.web.blog.presentation.loadInto
import com.hopcape.odo.web.core.presentation.state.Loadable
import com.hopcape.odo.web.core.presentation.state.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The two read-only corners of the CMS.
 *
 * Together in one file because each is a single read with one event, and a file
 * per class would be three lines of package declaration around ten lines of code.
 */

sealed interface MediaEvent {
    data object Retry : MediaEvent

    /** A file the browser handed back. Picking it is the platform layer's job. */
    data class Upload(val file: UploadRequest) : MediaEvent
}

class MediaViewModel(
    private val admin: AdminRepository,
) : ViewModel() {

    private val _items = MutableStateFlow<Loadable<List<MediaItem>>>(Loadable.Loading)
    val items: StateFlow<Loadable<List<MediaItem>>> = _items.asStateFlow()

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()

    private val _error = MutableStateFlow<UiText?>(null)
    val error: StateFlow<UiText?> = _error.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: MediaEvent) {
        when (event) {
            MediaEvent.Retry -> load()
            is MediaEvent.Upload -> upload(event.file)
        }
    }

    private fun upload(file: UploadRequest) {
        _uploading.value = true
        _error.value = null
        viewModelScope.launch {
            admin.upload(file).fold(
                ifLeft = { _error.value = it.asUiText() },
                // Re-read rather than prepending the returned item: the list the
                // server has is the list, and guessing at it is how a grid ends up
                // showing an upload that failed on the way in.
                ifRight = { load() },
            )
            _uploading.value = false
        }
    }

    private fun load() = loadInto(_items) { admin.media() }
}

sealed interface AnalyticsEvent {
    data object Retry : AnalyticsEvent
}

class AnalyticsViewModel(
    private val admin: AdminRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<Loadable<Analytics>>(Loadable.Loading)
    val state: StateFlow<Loadable<Analytics>> = _state.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: AnalyticsEvent) {
        when (event) {
            AnalyticsEvent.Retry -> load()
        }
    }

    private fun load() = loadInto(_state) { admin.analytics() }
}

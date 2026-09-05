package com.hopcape.odo.feature.advisory.presentation.checklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.platform.file.PlatformDownloads
import com.hopcape.odo.core.platform.file.PlatformFileStore
import com.hopcape.odo.core.platform.file.StorageKey
import com.hopcape.odo.core.platform.share.EXPORT_DIRECTORY
import com.hopcape.odo.core.platform.share.ShareMimeType
import com.hopcape.odo.feature.advisory.domain.checklist.ServiceChecklistReader
import com.hopcape.odo.feature.advisory.presentation.AdvisoryTelemetry
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The checklist, read once when the screen opens.
 *
 * Read rather than observed: the bands come off the network, and re-reading them because a
 * service was filed elsewhere would change the figures under an owner who is standing at a
 * counter reading them.
 *
 * [entry] is which of the three doors was used. It is the only reason the conditional Home
 * card can be judged against the two permanent entries — without it they are one number.
 */
internal class ChecklistViewModel(
    private val entry: String,
    private val read: ServiceChecklistReader,
    private val files: PlatformFileStore,
    private val downloads: PlatformDownloads,
    private val telemetry: AdvisoryTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(ChecklistUiState())
    val state: StateFlow<ChecklistUiState> = _state.asStateFlow()

    private val _effects = Channel<ChecklistEffect>(Channel.BUFFERED, BufferOverflow.DROP_OLDEST)
    val effects = _effects.receiveAsFlow()

    init {
        load()
    }

    fun onEvent(event: ChecklistEvent) = when (event) {
        ChecklistEvent.BackClicked -> emit(ChecklistEffect.NavigateBack)
        is ChecklistEvent.SaveClicked -> save(event.png)
    }

    private fun load() {
        viewModelScope.launch(telemetry.op(AdvisoryTelemetry.Trace.CHECKLIST_LOAD)) {
            val span = telemetry.checklistLoadStarted()
            read.read().fold(
                ifLeft = {
                    telemetry.checklistUnavailable(it::class.simpleName)
                    _state.update { state -> state.copy(isLoading = false) }
                },
                ifRight = { checklist ->
                    if (checklist.scheduleUnavailable) telemetry.scheduleUnavailable()
                    telemetry.checklistShown(
                        due = checklist.checklist.due.size,
                        priced = checklist.cost?.pricedItems ?: 0,
                        entry = entry,
                    )
                    _state.update { it.copy(isLoading = false, checklist = checklist) }
                },
            )
            telemetry.checklistLoadEnded(span)
        }
    }

    /**
     * Put the bytes in the export directory, then leave a copy where the owner keeps their
     * downloads. One file, overwritten: the card is derived from figures they already have,
     * and a history of it would fill their storage with pictures of the same list.
     */
    private fun save(png: ByteArray?) {
        // Counted before the guard, never after: a capture that produced nothing is still a
        // tap, and counting only the ones that got past it makes the funnel report more
        // failures than clicks.
        telemetry.checklistSaveClicked()
        if (png == null || png.isEmpty()) {
            fail("the card produced no bytes")
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val key = StorageKey.of(
                directory = EXPORT_DIRECTORY,
                fileName = FILE_NAME_STEM,
                rawExtension = PNG_EXTENSION,
            )
            files.write(key, png).fold(
                ifLeft = { fail(it.toString()) },
                ifRight = { written ->
                    downloads.saveCopy(written, FILE_NAME, ShareMimeType.PNG).fold(
                        ifLeft = { fail(it.toString()) },
                        ifRight = { emit(ChecklistEffect.Saved) },
                    )
                },
            )
            _state.update { it.copy(saving = false) }
        }
    }

    private fun fail(reason: String) {
        // The type of failure, never the card: it carries the owner's own car and figures.
        telemetry.checklistSaveFailed(reason)
        _state.update { it.copy(saving = false) }
        emit(ChecklistEffect.SaveFailed)
    }

    private fun emit(effect: ChecklistEffect) {
        _effects.trySend(effect)
    }

    private companion object {
        const val PNG_EXTENSION = "png"
        const val FILE_NAME_STEM = "odo-before-you-go-in"

        /** What the owner sees in their downloads, so it is a name rather than an id. */
        const val FILE_NAME = "$FILE_NAME_STEM.$PNG_EXTENSION"
    }
}

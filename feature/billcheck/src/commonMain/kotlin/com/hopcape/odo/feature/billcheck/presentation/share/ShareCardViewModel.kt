package com.hopcape.odo.feature.billcheck.presentation.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.platform.file.PlatformDownloads
import com.hopcape.odo.core.platform.file.PlatformFileStore
import com.hopcape.odo.core.platform.file.StorageKey
import com.hopcape.odo.core.platform.share.EXPORT_DIRECTORY
import com.hopcape.odo.core.platform.share.ShareMimeType
import com.hopcape.odo.feature.billcheck.presentation.BillCheckTelemetry
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Writes the drawn card to a file, then sends it or saves it.
 *
 * The pixels belong to the screen — only it knows what was laid out — so the bytes arrive
 * with the event. Everything after that is this: one file in the export directory, reused by
 * both buttons, replaced on every capture.
 *
 * **One file, overwritten.** The card is derived from figures the owner already has, so
 * keeping a history of it would fill their storage with pictures of the same number.
 */
internal class ShareCardViewModel(
    amountPaise: Long,
    flagged: Int,
    lines: Int,
    private val files: PlatformFileStore,
    private val downloads: PlatformDownloads,
    private val telemetry: BillCheckTelemetry,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ShareCardUiState(
            amount = Amount.of(amountPaise).getOrNull() ?: Amount.ZERO,
            flagged = flagged,
            lines = lines,
        ),
    )
    val state: StateFlow<ShareCardUiState> = _state.asStateFlow()

    private val _effects = Channel<ShareCardEffect>(Channel.BUFFERED, BufferOverflow.DROP_OLDEST)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: ShareCardEvent) = when (event) {
        ShareCardEvent.BackClicked -> emit(ShareCardEffect.NavigateBack)
        is ShareCardEvent.SendOnWhatsAppClicked -> send(event.png)
        is ShareCardEvent.SaveClicked -> save(event.png)
    }

    private fun send(png: ByteArray?) {
        telemetry.shareClicked()
        write(png) { key -> emit(ShareCardEffect.ShareImage(key)) }
    }

    private fun save(png: ByteArray?) {
        telemetry.cardSaved()
        write(png) { key ->
            downloads.saveCopy(key, FILE_NAME, ShareMimeType.PNG).fold(
                ifLeft = { fail(it.toString()) },
                ifRight = { emit(ShareCardEffect.Saved) },
            )
        }
    }

    /** Put the bytes in the export directory, then do [then] with the key they landed under. */
    private fun write(png: ByteArray?, then: suspend (String) -> Unit) {
        if (png == null || png.isEmpty()) {
            fail("the card produced no bytes")
            return
        }
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            val key = StorageKey.of(
                directory = EXPORT_DIRECTORY,
                fileName = FILE_NAME_STEM,
                rawExtension = PNG_EXTENSION,
            )
            files.write(key, png).fold(
                ifLeft = { fail(it.toString()) },
                ifRight = { written -> then(written) },
            )
            _state.update { it.copy(working = false) }
        }
    }

    private fun fail(reason: String) {
        // The type of failure, never the card: it carries the owner's own figures.
        telemetry.shareCardFailed(reason)
        _state.update { it.copy(working = false) }
        emit(ShareCardEffect.Failed)
    }

    private fun emit(effect: ShareCardEffect) {
        _effects.trySend(effect)
    }

    private companion object {
        const val PNG_EXTENSION = "png"
        const val FILE_NAME_STEM = "odo-saved"

        /** What the owner sees in their downloads, so it is a name rather than an id. */
        const val FILE_NAME = "$FILE_NAME_STEM.$PNG_EXTENSION"
    }
}

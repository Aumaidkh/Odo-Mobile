package com.hopcape.odo.feature.documentvault.presentation.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.document.model.DocumentValidity
import com.hopcape.odo.core.domain.showcase.ShowcaseArbiter
import com.hopcape.odo.core.domain.showcase.ShowcaseHookId
import com.hopcape.odo.feature.documentvault.domain.usecase.DocumentVaultSnapshot
import com.hopcape.odo.feature.documentvault.domain.usecase.ObserveDocumentVaultUseCase
import com.hopcape.odo.feature.documentvault.domain.usecase.VaultSlot
import com.hopcape.odo.feature.documentvault.presentation.DocumentVaultTelemetry
import com.hopcape.odo.feature.documentvault.presentation.state.Loadable
import com.hopcape.odo.feature.documentvault.resources.Res
import com.hopcape.odo.feature.documentvault.resources.dv_error_load_failed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

/**
 * State holder for the vault overview. Holds [DocumentVaultUiState], consumes
 * [DocumentVaultEvent]s and emits [DocumentVaultEffect]s.
 *
 * The car comes from [ActiveCarProvider] rather than the navigation key: the vault is a tab,
 * reached without naming a car, and every per-car surface answering "which car?" for itself
 * is how the app ends up opening someone else's.
 */
internal class DocumentVaultViewModel(
    activeCar: ActiveCarProvider,
    observeVault: ObserveDocumentVaultUseCase,
    private val showcase: ShowcaseArbiter,
    private val telemetry: DocumentVaultTelemetry,
) : ViewModel() {

    /** True while the reminders coach mark holds the arbiter's grant (#231). */
    private val vaultShowcaseVisible = MutableStateFlow(false)

    /** One ask per visit — reset when the surface is left, so the next visit may ask again. */
    private var vaultShowcaseRequested = false

    private val _effects = Channel<DocumentVaultEffect>(Channel.BUFFERED)
    val effects: Flow<DocumentVaultEffect> = _effects.receiveAsFlow()

    /** Guards the opened event so a re-emission does not count a second visit. */
    private var reportedOpen = false

    /**
     * The car's documents, resolved for today.
     *
     * A failed read becomes [Loadable.Failed] rather than an empty vault. The local DB is
     * the source of truth, so a read that fails means the vault is unreadable, and telling
     * an owner with four documents that they have none is the worse of the two lies.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<DocumentVaultUiState> = activeCar.activeCarId
        .flatMapLatest { carId ->
            // No car yet means setup has not finished. Nothing truthful can be said about a
            // vault that has no car, so the screen keeps waiting.
            if (carId == null) flowOf(DocumentVaultUiState()) else observeVault(carId).map(::toUiState)
        }
        .combine(vaultShowcaseVisible) { ui, visible -> ui.copy(vaultShowcase = visible) }
        .onEach(::maybeRequestVaultShowcase)
        .onEach(::reportOpened)
        .catch { cause ->
            telemetry.readFailed(DocumentVaultTelemetry.Screen.VAULT, cause)
            emit(DocumentVaultUiState(Loadable.Failed(UiText(Res.string.dv_error_load_failed))))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = DocumentVaultUiState(),
        )

    fun onEvent(event: DocumentVaultEvent) = when (event) {
        is DocumentVaultEvent.DocumentTapped -> emit(DocumentVaultEffect.OpenDocument(event.id))
        is DocumentVaultEvent.RenewTapped -> emit(DocumentVaultEffect.OpenAdd(event.type))
        is DocumentVaultEvent.AddTapped -> emit(DocumentVaultEffect.OpenAdd(event.type))
        DocumentVaultEvent.AddAnyTapped -> emit(DocumentVaultEffect.OpenAdd(prefillType = null))
        DocumentVaultEvent.BackTapped -> emit(DocumentVaultEffect.NavigateBack)

        DocumentVaultEvent.VaultShowcaseDismissed -> {
            vaultShowcaseVisible.value = false
            viewModelScope.launch { showcase.dismissed(ShowcaseHookId.DOCUMENT_REMINDERS) }
            Unit
        }

        DocumentVaultEvent.VaultShowcaseActedOn -> {
            vaultShowcaseVisible.value = false
            viewModelScope.launch { showcase.actedOn(ShowcaseHookId.DOCUMENT_REMINDERS) }
            emit(DocumentVaultEffect.OpenAdd(prefillType = null))
        }

        // Not seen: the owner never answered — a navigation did. The hook keeps its one
        // showing, and the reset lets the next visit ask again.
        DocumentVaultEvent.VaultShowcaseLeft -> {
            if (vaultShowcaseVisible.value) showcase.surfaceLeft(ShowcaseHookId.DOCUMENT_REMINDERS)
            vaultShowcaseVisible.value = false
            vaultShowcaseRequested = false
        }
    }

    /**
     * The reminders hook's due-condition (#231): at most one document on file — the empty
     * vault, or the first paper just saved (whichever the owner meets first; seen-once
     * keeps it to one showing total). The payoff being taught is that filing sets an
     * alarm; it says nothing about the free plan's cap, on purpose.
     */
    private suspend fun maybeRequestVaultShowcase(ui: DocumentVaultUiState) {
        if (vaultShowcaseRequested) return
        val content = (ui.content as? Loadable.Ready)?.value ?: return
        if (content.rows.filterIsInstance<DocumentRow.OnFile>().size > 1) return
        vaultShowcaseRequested = true
        if (showcase.request(ShowcaseHookId.DOCUMENT_REMINDERS)) {
            vaultShowcaseVisible.value = true
        }
    }

    private fun emit(effect: DocumentVaultEffect) {
        _effects.trySend(effect)
        Unit
    }

    /** The three counts the vault exists to move, reported once per visit. */
    private fun reportOpened(state: DocumentVaultUiState) {
        val content = (state.content as? Loadable.Ready)?.value ?: return
        if (reportedOpen) return
        reportedOpen = true
        telemetry.vaultOpened(
            onFile = content.rows.count { it is DocumentRow.OnFile },
            missing = content.rows.count { it is DocumentRow.Missing },
            needsAttention = content.rows.count { it is DocumentRow.OnFile && it.needsAttention },
        )
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}

/** Domain snapshot to display state. The header is decided here, once. */
private fun toUiState(snapshot: DocumentVaultSnapshot): DocumentVaultUiState =
    DocumentVaultUiState(
        Loadable.Ready(
            VaultContent(
                rows = snapshot.slots.map(::toRow),
                header = snapshot.toHeader(),
            ),
        ),
    )

private fun toRow(slot: VaultSlot): DocumentRow = when (slot) {
    is VaultSlot.Missing -> DocumentRow.Missing(slot.type)
    is VaultSlot.OnFile -> DocumentRow.OnFile(
        id = slot.document.id,
        type = slot.type,
        title = slot.document.title?.value,
        validity = slot.validity,
        reminderDaysBefore = slot.nextReminder?.daysBefore,
    )
}

private fun DocumentVaultSnapshot.toHeader(): VaultHeader = when {
    needsAttention.isNotEmpty() -> VaultHeader.NeedsAttention(
        count = needsAttention.size,
        // The most urgent one leads the copy: expired before expiring, then soonest first.
        first = needsAttention.minBy { it.validity.urgency() }.type,
    )

    isFullyCovered -> VaultHeader.Covered(count = onFileCount)
    else -> VaultHeader.AddPrompt
}

/** Lower sorts first: an expired document outranks one that is merely close. */
private fun DocumentValidity.urgency(): Int = when (this) {
    is DocumentValidity.Expired -> -daysAgo
    is DocumentValidity.ExpiringSoon -> daysLeft
    else -> Int.MAX_VALUE
}

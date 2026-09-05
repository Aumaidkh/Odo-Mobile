package com.hopcape.odo.web.admin.presentation.social

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hopcape.odo.web.admin.domain.CredentialStatus
import com.hopcape.odo.web.admin.domain.PostingMode
import com.hopcape.odo.web.admin.domain.SlotApproval
import com.hopcape.odo.web.admin.domain.SocialAccount
import com.hopcape.odo.web.admin.domain.SocialFact
import com.hopcape.odo.web.admin.domain.SocialPlatform
import com.hopcape.odo.web.admin.domain.SocialPostRecord
import com.hopcape.odo.web.admin.domain.SocialQueueItem
import com.hopcape.odo.web.admin.domain.SocialRepository
import com.hopcape.odo.web.admin.domain.SocialSettings
import com.hopcape.odo.web.admin.domain.SocialSlot
import com.hopcape.odo.web.admin.domain.TelegramRecipient
import com.hopcape.odo.web.admin.presentation.asUiText
import com.hopcape.odo.web.admin.presentation.isRetryable
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_social_saved
import com.hopcape.odo.web.core.presentation.state.Loadable
import com.hopcape.odo.web.core.presentation.state.UiText
import com.hopcape.odo.web.core.presentation.state.asUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which part of the pipeline is on screen. One section, six things to set. */
enum class SocialTab(val label: String) {
    SETTINGS("Settings"),
    SCHEDULE("Schedule"),
    ACCOUNTS("Accounts"),
    TELEGRAM("Telegram"),
    QUEUE("Queue"),
    FACTS("Fact bank"),
}

sealed interface SocialEvent {
    data object Refresh : SocialEvent
    data class TabChanged(val tab: SocialTab) : SocialEvent
    data object MessageDismissed : SocialEvent

    // Settings
    data class ModeChanged(val mode: PostingMode) : SocialEvent
    data object PauseToggled : SocialEvent
    data class TimezoneChanged(val value: String) : SocialEvent
    data object SettingsSaved : SocialEvent

    // Credentials — set only. Nothing here can read one back.
    data class CredentialDraftChanged(val key: String, val value: String) : SocialEvent
    data class CredentialSaved(val key: String) : SocialEvent
    data class CredentialCleared(val key: String) : SocialEvent

    // Schedule
    data class SlotEditing(val slot: SocialSlot?) : SocialEvent
    data class SlotDraftChanged(val slot: SocialSlot) : SocialEvent
    data object SlotSaved : SocialEvent
    data class SlotDeleted(val id: String) : SocialEvent
    data class SlotEnabledToggled(val slot: SocialSlot) : SocialEvent

    // Accounts
    data class AccountDraftChanged(val draft: AccountDraft) : SocialEvent
    data object AccountConnected : SocialEvent
    data class AccountEnabledToggled(val account: SocialAccount) : SocialEvent
    data class AccountDisconnected(val id: String) : SocialEvent

    // Telegram
    data class RecipientDraftChanged(val draft: RecipientDraft) : SocialEvent
    data object RecipientAdded : SocialEvent
    data class RecipientToggledNotify(val recipient: TelegramRecipient) : SocialEvent
    data class RecipientToggledApprove(val recipient: TelegramRecipient) : SocialEvent
    data class RecipientRemoved(val chatId: Long) : SocialEvent

    // Queue
    data class QueueApproved(val id: Long) : SocialEvent
    data class QueueRejected(val id: Long) : SocialEvent
    data class QueueRetried(val id: Long) : SocialEvent

    // Fact bank
    data class FactEditing(val fact: SocialFact?) : SocialEvent
    data class FactDraftChanged(val fact: SocialFact) : SocialEvent
    data object FactSaved : SocialEvent
    data class FactDeleted(val id: Long) : SocialEvent
}

/** What "connect an account" asks for: an id, a name, and the token that goes with them. */
@Immutable
data class AccountDraft(
    val platform: SocialPlatform = SocialPlatform.INSTAGRAM,
    val displayName: String = "",
    val externalId: String = "",
    val token: String = "",
) {
    val canSubmit: Boolean
        get() = displayName.isNotBlank() && externalId.isNotBlank() && token.isNotBlank()
}

/** A Telegram chat being added. The id is what the bot addresses, so it must be a number. */
@Immutable
data class RecipientDraft(
    val chatId: String = "",
    val name: String = "",
    val canApprove: Boolean = false,
) {
    val parsedChatId: Long? get() = chatId.trim().toLongOrNull()
    val canSubmit: Boolean get() = name.isNotBlank() && parsedChatId != null
}

@Immutable
data class SocialUiState(
    val tab: SocialTab = SocialTab.SETTINGS,
    val settings: Loadable<SocialSettings> = Loadable.Loading,
    val slots: List<SocialSlot> = emptyList(),
    val accounts: List<SocialAccount> = emptyList(),
    val recipients: List<TelegramRecipient> = emptyList(),
    val queue: List<SocialQueueItem> = emptyList(),
    val log: List<SocialPostRecord> = emptyList(),
    val facts: List<SocialFact> = emptyList(),
    /** Which secrets exist and when they changed. Never their values. */
    val credentials: List<CredentialStatus> = emptyList(),
    /** What is typed into a secret field, before it is sent and forgotten. */
    val credentialDrafts: Map<String, String> = emptyMap(),
    val editingSlot: SocialSlot? = null,
    val accountDraft: AccountDraft = AccountDraft(),
    val recipientDraft: RecipientDraft = RecipientDraft(),
    val editingFact: SocialFact? = null,
    val busy: Boolean = false,
    val message: UiText? = null,
) {
    val current: SocialSettings get() = (settings as? Loadable.Ready)?.value ?: SocialSettings()

    /** Per-slot approval only means anything under `scheduled`. */
    val slotApprovalApplies: Boolean get() = current.mode == PostingMode.SCHEDULED

    fun credentialSetAt(key: String): String? = credentials.firstOrNull { it.key == key }?.updatedAt

    /** Approvals are only reachable while somebody in Telegram can act on them, or from here. */
    val nobodyCanApprove: Boolean get() = recipients.none { it.canApprove }

    val pending: List<SocialQueueItem> get() = queue.filter { it.isPending }
    val failed: List<SocialQueueItem> get() = queue.filter { it.hasFailed }
}

/**
 * The whole social section, behind one ViewModel.
 *
 * Six tabs and one state, rather than six ViewModels, because they are six views of one
 * pipeline and they contradict each other when read apart: a schedule slot's approval is
 * meaningless unless the mode is `scheduled`, and a queue waiting on Telegram is stuck unless
 * somebody there may approve. Both facts are on screen only if both were loaded together.
 */
class SocialViewModel(
    private val social: SocialRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SocialUiState())
    val state: StateFlow<SocialUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: SocialEvent) {
        when (event) {
            SocialEvent.Refresh -> load()
            is SocialEvent.TabChanged -> _state.update { it.copy(tab = event.tab) }
            SocialEvent.MessageDismissed -> _state.update { it.copy(message = null) }

            is SocialEvent.ModeChanged -> editSettings { it.copy(mode = event.mode) }
            SocialEvent.PauseToggled -> {
                // Applied at once rather than behind Save: a pause that needed a second
                // click is a pause that arrives after whatever it was meant to stop.
                val next = _state.value.current.let { it.copy(paused = !it.paused) }
                _state.update { it.copy(settings = Loadable.Ready(next)) }
                write { social.saveSettings(next) }
            }
            is SocialEvent.TimezoneChanged -> editSettings { it.copy(timezone = event.value) }
            SocialEvent.SettingsSaved -> write { social.saveSettings(_state.value.current) }

            is SocialEvent.CredentialDraftChanged -> _state.update {
                it.copy(credentialDrafts = it.credentialDrafts + (event.key to event.value))
            }
            is SocialEvent.CredentialSaved -> {
                val value = _state.value.credentialDrafts[event.key].orEmpty()
                if (value.isBlank()) return
                // Cleared from state the moment it is sent: a secret that lingers in a
                // ViewModel is a secret in a heap dump.
                _state.update { it.copy(credentialDrafts = it.credentialDrafts - event.key) }
                write { social.setCredential(event.key, value) }
            }
            is SocialEvent.CredentialCleared -> write { social.clearCredential(event.key) }

            is SocialEvent.SlotEditing -> _state.update { it.copy(editingSlot = event.slot) }
            is SocialEvent.SlotDraftChanged -> _state.update { it.copy(editingSlot = event.slot) }
            SocialEvent.SlotSaved -> _state.value.editingSlot?.let { slot ->
                _state.update { it.copy(editingSlot = null) }
                write { social.saveSlot(slot) }
            }
            is SocialEvent.SlotDeleted -> write { social.deleteSlot(event.id) }
            is SocialEvent.SlotEnabledToggled ->
                write { social.saveSlot(event.slot.copy(enabled = !event.slot.enabled)) }

            is SocialEvent.AccountDraftChanged -> _state.update { it.copy(accountDraft = event.draft) }
            SocialEvent.AccountConnected -> {
                val draft = _state.value.accountDraft
                if (!draft.canSubmit) return
                _state.update { it.copy(accountDraft = AccountDraft()) }
                write {
                    social.connectAccount(
                        account = SocialAccount(
                            id = "",
                            platform = draft.platform,
                            displayName = draft.displayName.trim(),
                            externalId = draft.externalId.trim(),
                        ),
                        token = draft.token.trim(),
                    )
                }
            }
            is SocialEvent.AccountEnabledToggled ->
                write { social.setAccountEnabled(event.account.id, !event.account.enabled) }
            is SocialEvent.AccountDisconnected -> write { social.disconnectAccount(event.id) }

            is SocialEvent.RecipientDraftChanged -> _state.update { it.copy(recipientDraft = event.draft) }
            SocialEvent.RecipientAdded -> {
                val draft = _state.value.recipientDraft
                val chatId = draft.parsedChatId ?: return
                _state.update { it.copy(recipientDraft = RecipientDraft()) }
                write {
                    social.saveRecipient(
                        TelegramRecipient(
                            chatId = chatId,
                            name = draft.name.trim(),
                            canApprove = draft.canApprove,
                        ),
                    )
                }
            }
            is SocialEvent.RecipientToggledNotify ->
                write { social.saveRecipient(event.recipient.copy(notify = !event.recipient.notify)) }
            is SocialEvent.RecipientToggledApprove ->
                write { social.saveRecipient(event.recipient.copy(canApprove = !event.recipient.canApprove)) }
            is SocialEvent.RecipientRemoved -> write { social.removeRecipient(event.chatId) }

            is SocialEvent.QueueApproved -> write { social.setQueueStatus(event.id, "approved") }
            is SocialEvent.QueueRejected -> write { social.setQueueStatus(event.id, "rejected") }
            // Back to draft is what a retry is: the pipeline picks a draft up again.
            is SocialEvent.QueueRetried -> write { social.setQueueStatus(event.id, "draft") }

            is SocialEvent.FactEditing -> _state.update { it.copy(editingFact = event.fact) }
            is SocialEvent.FactDraftChanged -> _state.update { it.copy(editingFact = event.fact) }
            SocialEvent.FactSaved -> _state.value.editingFact?.let { fact ->
                if (fact.fact.isBlank()) return
                _state.update { it.copy(editingFact = null) }
                write { social.saveFact(fact) }
            }
            is SocialEvent.FactDeleted -> write { social.deleteFact(event.id) }
        }
    }

    private fun editSettings(change: (SocialSettings) -> SocialSettings) {
        _state.update { it.copy(settings = Loadable.Ready(change(it.current))) }
    }

    /**
     * Everything, in one pass.
     *
     * The lists are read side by side rather than per tab: switching tabs then costs nothing,
     * and the two cross-tab warnings this screen shows — an approval nobody can give, a slot
     * whose approval the mode ignores — need two tabs' data at once to be true.
     */
    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val settings = social.settings()
            _state.update { current ->
                current.copy(
                    busy = false,
                    settings = settings.fold(
                        ifLeft = { Loadable.Failed(it.asUiText(), it.isRetryable, it) },
                        ifRight = { Loadable.Ready(it) },
                    ),
                    slots = social.slotsOrEmpty(),
                    accounts = social.accountsOrEmpty(),
                    recipients = social.recipientsOrEmpty(),
                    queue = social.queueOrEmpty(),
                    log = social.logOrEmpty(),
                    facts = social.factsOrEmpty(),
                    credentials = social.credentialsOrEmpty(),
                )
            }
        }
    }

    /**
     * Do the write, say so, and re-read.
     *
     * Re-reading rather than patching the list in place: a row here can be changed by the
     * pipeline between two clicks — the tick stamps a slot, the publisher moves a queue item —
     * and a screen that trusted its own copy would show a post as pending after it went out.
     */
    private fun write(block: suspend () -> arrow.core.Either<com.hopcape.odo.web.core.domain.WebError, Unit>) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            block().fold(
                ifLeft = { error -> _state.update { it.copy(busy = false, message = error.asUiText()) } },
                ifRight = { _state.update { it.copy(message = Res.string.ad_social_saved.asUiText()) } },
            )
            load()
        }
    }
}

/*
 * A failed list is an empty list on this screen, and only here.
 *
 * The settings read decides whether the section can be shown at all, so its failure is kept
 * and rendered. The six lists beside it are independent: a fact bank that would not load is
 * no reason to hide a queue somebody is waiting to approve.
 */
private suspend fun SocialRepository.slotsOrEmpty() = slots().getOrNull().orEmpty()
private suspend fun SocialRepository.accountsOrEmpty() = accounts().getOrNull().orEmpty()
private suspend fun SocialRepository.recipientsOrEmpty() = recipients().getOrNull().orEmpty()
private suspend fun SocialRepository.queueOrEmpty() = queue().getOrNull().orEmpty()
private suspend fun SocialRepository.logOrEmpty() = postLog().getOrNull().orEmpty()
private suspend fun SocialRepository.factsOrEmpty() = facts().getOrNull().orEmpty()
private suspend fun SocialRepository.credentialsOrEmpty() = credentials().getOrNull().orEmpty()

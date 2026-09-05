package com.hopcape.odo.web.admin.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.admin.domain.PostingMode
import com.hopcape.odo.web.admin.domain.SlotApproval
import com.hopcape.odo.web.admin.domain.SocialAccount
import com.hopcape.odo.web.admin.domain.SocialFact
import com.hopcape.odo.web.admin.domain.SocialPlatform
import com.hopcape.odo.web.admin.domain.SocialQueueItem
import com.hopcape.odo.web.admin.domain.SocialSlot
import com.hopcape.odo.web.admin.domain.CredentialStatus
import com.hopcape.odo.web.admin.domain.TelegramRecipient
import com.hopcape.odo.web.admin.presentation.social.AccountDraft
import com.hopcape.odo.web.admin.presentation.social.RecipientDraft
import com.hopcape.odo.web.admin.presentation.social.SocialEvent
import com.hopcape.odo.web.admin.presentation.social.SocialTab
import com.hopcape.odo.web.admin.presentation.social.SocialUiState
import com.hopcape.odo.web.admin.ui.component.AdminField
import com.hopcape.odo.web.admin.ui.component.Banner
import com.hopcape.odo.web.admin.ui.component.Cell
import com.hopcape.odo.web.admin.ui.component.CellPrimary
import com.hopcape.odo.web.admin.ui.component.CellSecondary
import com.hopcape.odo.web.admin.ui.component.FieldLabel
import com.hopcape.odo.web.admin.ui.component.Muted
import com.hopcape.odo.web.admin.ui.component.Panel
import com.hopcape.odo.web.admin.ui.component.PanelHeader
import com.hopcape.odo.web.admin.ui.component.Pill
import com.hopcape.odo.web.admin.ui.component.PrimaryAction
import com.hopcape.odo.web.admin.ui.component.ReloadAction
import com.hopcape.odo.web.admin.ui.component.RowAction
import com.hopcape.odo.web.admin.ui.component.TableHead
import com.hopcape.odo.web.admin.ui.component.TableRow
import com.hopcape.odo.web.admin.ui.theme.AdminTokens
import com.hopcape.odo.web.core.presentation.state.resolve

/**
 * The social pipeline, on one screen with six tabs.
 *
 * Two warnings ride above the tabs rather than inside them, because both are true about the
 * pipeline and neither is visible from the tab that causes it: a queue nobody in Telegram may
 * approve, and a pause that is stopping everything. A person who came here to ask why nothing
 * posted should read the answer before they start clicking.
 */
@Composable
fun SocialScreen(state: SocialUiState, onEvent: (SocialEvent) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header(state, onEvent)

            if (state.current.paused) {
                Panel { Warning("PAUSED — nothing is generated, rendered or published.") }
            }
            if (state.nobodyCanApprove && state.pending.isNotEmpty()) {
                Panel {
                    Warning(
                        "${state.pending.size} post(s) waiting, and no Telegram recipient may " +
                            "approve. Approve here, or give somebody the right under Telegram.",
                    )
                }
            }

            when (state.tab) {
                SocialTab.SETTINGS -> SettingsTab(state, onEvent)
                SocialTab.SCHEDULE -> ScheduleTab(state, onEvent)
                SocialTab.ACCOUNTS -> AccountsTab(state, onEvent)
                SocialTab.TELEGRAM -> TelegramTab(state, onEvent)
                SocialTab.QUEUE -> QueueTab(state, onEvent)
                SocialTab.FACTS -> FactsTab(state, onEvent)
            }
        }
        state.message?.let { message -> Banner(message.resolve()) { onEvent(SocialEvent.MessageDismissed) } }
    }
}

@Composable
private fun Header(state: SocialUiState, onEvent: (SocialEvent) -> Unit) {
    Panel {
        PanelHeader("SOCIAL PIPELINE") {
            ReloadAction(onClick = { onEvent(SocialEvent.Refresh) }, busy = state.busy)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SocialTab.entries.forEach { tab ->
                RowAction(
                    label = if (tab == state.tab) "▸ ${tab.label}" else tab.label,
                    onClick = { onEvent(SocialEvent.TabChanged(tab)) },
                    color = if (tab == state.tab) AdminTokens.textStrong else AdminTokens.text,
                )
            }
        }
    }
}

/* ------------------------------ settings ------------------------------ */

@Composable
private fun SettingsTab(state: SocialUiState, onEvent: (SocialEvent) -> Unit) {
    val settings = state.current
    Panel {
        PanelHeader("HOW IT POSTS")
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FieldLabel("MODE")
            PostingMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RowAction(
                        label = if (settings.mode == mode) "●" else "○",
                        onClick = { onEvent(SocialEvent.ModeChanged(mode)) },
                    )
                    Column(Modifier.weight(1f)) {
                        CellPrimary(mode.title())
                        CellSecondary(mode.explanation())
                    }
                }
            }

            FieldLabel("TIMEZONE")
            AdminField(
                value = settings.timezone,
                onValueChange = { onEvent(SocialEvent.TimezoneChanged(it)) },
                placeholder = "Asia/Kolkata",
            )
            Muted("Schedule slots are written in this zone, not the server's.")

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryAction("Save", onClick = { onEvent(SocialEvent.SettingsSaved) }, enabled = !state.busy)
                RowAction(
                    label = if (settings.paused) "Resume pipeline" else "Pause everything",
                    onClick = { onEvent(SocialEvent.PauseToggled) },
                    color = if (settings.paused) AdminTokens.textStrong else AdminTokens.danger,
                )
            }
        }
    }

    Panel {
        PanelHeader("KEYS")
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Muted("Set here and never shown again. What is stored is readable only by the functions.")
            SecretField(state, onEvent, CredentialStatus.GEMINI, "Gemini API key")
            SecretField(state, onEvent, CredentialStatus.TELEGRAM_BOT, "Telegram bot token")
        }
    }
}

/**
 * One secret.
 *
 * There is no "current value" to render, by design — the panel can write a credential and can
 * never read one back. What it shows instead is whether one exists and when it last changed,
 * which is enough to answer "is this configured" without answering "what is it".
 */
@Composable
private fun SecretField(
    state: SocialUiState,
    onEvent: (SocialEvent) -> Unit,
    key: String,
    label: String,
) {
    val setAt = state.credentialSetAt(key)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(label.uppercase())
            Pill(
                text = if (setAt != null) "SET · $setAt" else "NOT SET",
                dot = if (setAt != null) AdminTokens.accent else AdminTokens.danger,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AdminField(
                value = state.credentialDrafts[key].orEmpty(),
                onValueChange = { onEvent(SocialEvent.CredentialDraftChanged(key, it)) },
                placeholder = if (setAt != null) "Replace it" else "Paste it",
                masked = true,
                modifier = Modifier.weight(1f),
            )
            RowAction("Save", onClick = { onEvent(SocialEvent.CredentialSaved(key)) })
            if (setAt != null) {
                RowAction(
                    label = "Clear",
                    onClick = { onEvent(SocialEvent.CredentialCleared(key)) },
                    color = AdminTokens.danger,
                )
            }
        }
    }
}

/* ------------------------------ schedule ------------------------------ */

@Composable
private fun ScheduleTab(state: SocialUiState, onEvent: (SocialEvent) -> Unit) {
    Panel {
        PanelHeader("SCHEDULE") {
            RowAction("+ Slot", onClick = { onEvent(SocialEvent.SlotEditing(blankSlot())) })
        }
        if (!state.slotApprovalApplies) {
            Muted(
                "Mode is ${state.current.mode.title()}, so a slot's own approval is not consulted. " +
                    "Switch to Scheduled for it to mean anything.",
                Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            )
        }
        TableHead(
            columns = listOf("LABEL", "WHEN", "PLATFORMS", "APPROVAL", "LAST FIRED", ""),
            weights = listOf(2f, 2f, 2f, 1.2f, 1.6f, 1.4f),
        )
        state.editingSlot?.let { SlotEditor(it, onEvent) }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(state.slots, key = { it.id }) { slot ->
                TableRow {
                    Cell(slot.label, Modifier.weight(2f), if (slot.enabled) AdminTokens.textStrong else AdminTokens.textMuted)
                    Cell("${slot.timeOfDay} · ${slot.whenLabel()}", Modifier.weight(2f))
                    Cell(slot.platformLabel(), Modifier.weight(2f))
                    Cell(slot.approval.name.lowercase(), Modifier.weight(1.2f))
                    Cell(slot.lastFiredAt ?: "—", Modifier.weight(1.6f))
                    Row(Modifier.weight(1.4f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RowAction("Edit", onClick = { onEvent(SocialEvent.SlotEditing(slot)) })
                        RowAction(
                            label = if (slot.enabled) "Off" else "On",
                            onClick = { onEvent(SocialEvent.SlotEnabledToggled(slot)) },
                        )
                        RowAction("Delete", onClick = { onEvent(SocialEvent.SlotDeleted(slot.id)) }, color = AdminTokens.danger)
                    }
                }
            }
        }
        if (state.slots.isEmpty()) Muted("No slots. Nothing is scheduled.", Modifier.padding(18.dp))
    }
}

@Composable
private fun SlotEditor(slot: SocialSlot, onEvent: (SocialEvent) -> Unit) {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AdminField(slot.label, { onEvent(SocialEvent.SlotDraftChanged(slot.copy(label = it))) }, "Morning post")
        AdminField(slot.timeOfDay, { onEvent(SocialEvent.SlotDraftChanged(slot.copy(timeOfDay = it))) }, "09:00")

        FieldLabel("DAYS — none picked means every day")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DAY_LABELS.forEachIndexed { index, label ->
                val iso = index + 1
                val on = iso in slot.daysOfWeek
                RowAction(
                    label = if (on) "[$label]" else label,
                    onClick = {
                        val next = if (on) slot.daysOfWeek - iso else slot.daysOfWeek + iso
                        onEvent(SocialEvent.SlotDraftChanged(slot.copy(daysOfWeek = next.sorted())))
                    },
                )
            }
        }

        AdminField(
            value = slot.dayOfMonth?.toString().orEmpty(),
            onValueChange = {
                onEvent(SocialEvent.SlotDraftChanged(slot.copy(dayOfMonth = it.trim().toIntOrNull())))
            },
            placeholder = "Day of month — blank unless monthly",
        )

        FieldLabel("PLATFORMS — none picked means every connected account")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SocialPlatform.entries.forEach { platform ->
                val on = platform in slot.platforms
                RowAction(
                    label = if (on) "[${platform.label}]" else platform.label,
                    onClick = {
                        val next = if (on) slot.platforms - platform else slot.platforms + platform
                        onEvent(SocialEvent.SlotDraftChanged(slot.copy(platforms = next)))
                    },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RowAction(
                label = "Approval: ${slot.approval.name.lowercase()}",
                onClick = {
                    val next = if (slot.approval == SlotApproval.MANUAL) SlotApproval.AUTO else SlotApproval.MANUAL
                    onEvent(SocialEvent.SlotDraftChanged(slot.copy(approval = next)))
                },
            )
            RowAction(
                label = if (slot.includeStory) "Story: yes" else "Story: no",
                onClick = { onEvent(SocialEvent.SlotDraftChanged(slot.copy(includeStory = !slot.includeStory))) },
            )
            PrimaryAction("Save slot", onClick = { onEvent(SocialEvent.SlotSaved) })
            RowAction("Cancel", onClick = { onEvent(SocialEvent.SlotEditing(null)) })
        }
    }
}

/* ------------------------------ accounts ------------------------------ */

@Composable
private fun AccountsTab(state: SocialUiState, onEvent: (SocialEvent) -> Unit) {
    Panel {
        PanelHeader("CONNECT AN ACCOUNT")
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val draft = state.accountDraft
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SocialPlatform.entries.forEach { platform ->
                    RowAction(
                        label = if (draft.platform == platform) "[${platform.label}]" else platform.label,
                        onClick = { onEvent(SocialEvent.AccountDraftChanged(draft.copy(platform = platform))) },
                    )
                }
            }
            AdminField(draft.displayName, { onEvent(SocialEvent.AccountDraftChanged(draft.copy(displayName = it))) }, "What to call it")
            AdminField(draft.externalId, { onEvent(SocialEvent.AccountDraftChanged(draft.copy(externalId = it))) }, "User id / page id")
            AdminField(
                value = draft.token,
                onValueChange = { onEvent(SocialEvent.AccountDraftChanged(draft.copy(token = it))) },
                placeholder = "Access token",
                masked = true,
            )
            Muted("The token is stored where only the functions can read it. It is never shown again.")
            PrimaryAction("Connect", onClick = { onEvent(SocialEvent.AccountConnected) }, enabled = draft.canSubmit && !state.busy)
        }
    }

    Panel {
        PanelHeader("CONNECTED")
        TableHead(
            columns = listOf("PLATFORM", "NAME", "ID", "TOKEN", "STATE", ""),
            weights = listOf(1.4f, 2f, 2f, 2f, 1f, 1.6f),
        )
        LazyColumn(Modifier.fillMaxWidth()) {
            items(state.accounts, key = { it.id }) { account ->
                TableRow {
                    Cell(account.platform.label, Modifier.weight(1.4f))
                    Cell(account.displayName, Modifier.weight(2f))
                    CellSecondary(account.externalId, Modifier.weight(2f))
                    Box(Modifier.weight(2f)) { TokenPill(state, account) }
                    Cell(if (account.enabled) "on" else "off", Modifier.weight(1f))
                    Row(Modifier.weight(1.6f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RowAction(
                            label = if (account.enabled) "Disable" else "Enable",
                            onClick = { onEvent(SocialEvent.AccountEnabledToggled(account)) },
                        )
                        RowAction("Disconnect", onClick = { onEvent(SocialEvent.AccountDisconnected(account.id)) }, color = AdminTokens.danger)
                    }
                }
            }
        }
        if (state.accounts.isEmpty()) Muted("Nothing connected. Nothing can be posted.", Modifier.padding(18.dp))
    }
}

/**
 * Whether this account has a token, and whether it is about to stop working.
 *
 * Instagram's long-lived token lasts about sixty days and then the pipeline goes quiet with
 * no other symptom, so the expiry is worth a colour rather than a date nobody reads.
 */
@Composable
private fun TokenPill(state: SocialUiState, account: SocialAccount) {
    val setAt = state.credentialSetAt(CredentialStatus.forAccount(account.id))
    when {
        setAt == null -> Pill("NO TOKEN", dot = AdminTokens.danger)
        account.tokenExpiresAt != null -> Pill("EXPIRES ${account.tokenExpiresAt}", dot = AdminTokens.textMuted)
        else -> Pill("SET · $setAt", dot = AdminTokens.accent)
    }
}

/* ------------------------------ telegram ------------------------------ */

@Composable
private fun TelegramTab(state: SocialUiState, onEvent: (SocialEvent) -> Unit) {
    Panel {
        PanelHeader("ADD A CHAT")
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val draft = state.recipientDraft
            AdminField(draft.name, { onEvent(SocialEvent.RecipientDraftChanged(draft.copy(name = it))) }, "Who it is")
            AdminField(draft.chatId, { onEvent(SocialEvent.RecipientDraftChanged(draft.copy(chatId = it))) }, "Chat id")
            RowAction(
                label = if (draft.canApprove) "[Can approve]" else "Can approve",
                onClick = { onEvent(SocialEvent.RecipientDraftChanged(draft.copy(canApprove = !draft.canApprove))) },
            )
            PrimaryAction("Add", onClick = { onEvent(SocialEvent.RecipientAdded) }, enabled = draft.canSubmit && !state.busy)
        }
    }

    Panel {
        PanelHeader("RECIPIENTS")
        Muted(
            "While this list is empty the webhook falls back to its own env, so nothing breaks. " +
                "The first row added takes over — and only rows with 'can approve' may press a button.",
            Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
        )
        TableHead(columns = listOf("NAME", "CHAT ID", "NOTIFY", "APPROVE", ""), weights = listOf(2f, 2f, 1f, 1f, 1.6f))
        LazyColumn(Modifier.fillMaxWidth()) {
            items(state.recipients, key = { it.chatId }) { recipient ->
                TableRow {
                    Cell(recipient.name, Modifier.weight(2f))
                    CellSecondary(recipient.chatId.toString(), Modifier.weight(2f))
                    Cell(if (recipient.notify) "yes" else "no", Modifier.weight(1f))
                    Cell(if (recipient.canApprove) "yes" else "no", Modifier.weight(1f))
                    Row(Modifier.weight(1.6f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RowAction("Notify", onClick = { onEvent(SocialEvent.RecipientToggledNotify(recipient)) })
                        RowAction("Approve", onClick = { onEvent(SocialEvent.RecipientToggledApprove(recipient)) })
                        RowAction("Remove", onClick = { onEvent(SocialEvent.RecipientRemoved(recipient.chatId)) }, color = AdminTokens.danger)
                    }
                }
            }
        }
        if (state.recipients.isEmpty()) Muted("Nobody added.", Modifier.padding(18.dp))
    }
}

/* ------------------------------ queue ------------------------------ */

@Composable
private fun QueueTab(state: SocialUiState, onEvent: (SocialEvent) -> Unit) {
    Panel {
        PanelHeader("WAITING")
        LazyColumn(Modifier.fillMaxWidth()) {
            items(state.queue, key = { it.id }) { item -> QueueRow(item, onEvent) }
        }
        if (state.queue.isEmpty()) Muted("Nothing in the queue.", Modifier.padding(18.dp))
    }

    Panel {
        PanelHeader("PUBLISHED")
        TableHead(columns = listOf("WHEN", "QUEUE", "MEDIA", "STORY"), weights = listOf(2f, 1f, 2f, 2f))
        LazyColumn(Modifier.fillMaxWidth()) {
            items(state.log, key = { it.id }) { record ->
                TableRow {
                    Cell(record.publishedAt, Modifier.weight(2f))
                    CellSecondary(record.queueId?.toString() ?: "—", Modifier.weight(1f))
                    CellSecondary(record.mediaId ?: "—", Modifier.weight(2f))
                    CellSecondary(record.storyId ?: "—", Modifier.weight(2f))
                }
            }
        }
        if (state.log.isEmpty()) Muted("Nothing has gone out yet.", Modifier.padding(18.dp))
    }
}

@Composable
private fun QueueRow(item: SocialQueueItem, onEvent: (SocialEvent) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(item.status.uppercase(), dot = item.statusColour())
            CellPrimary(item.headline.ifBlank { "(no headline)" }, Modifier.weight(1f))
            CellSecondary(item.createdAt)
        }
        if (item.caption.isNotBlank()) CellSecondary(item.caption)
        if (item.hashtags.isNotBlank()) Muted(item.hashtags)
        item.error?.takeIf { it.isNotBlank() }?.let { Warning(it) }
        // The rendered image as a link rather than an <img>: it is a public storage URL and
        // the panel has no image loader, so a link is honest where a broken box is not.
        item.postImageUrl?.let { Muted(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RowAction("Approve", onClick = { onEvent(SocialEvent.QueueApproved(item.id)) })
            RowAction("Reject", onClick = { onEvent(SocialEvent.QueueRejected(item.id)) }, color = AdminTokens.danger)
            if (item.hasFailed) RowAction("Retry", onClick = { onEvent(SocialEvent.QueueRetried(item.id)) })
        }
    }
}

/* ------------------------------ fact bank ------------------------------ */

@Composable
private fun FactsTab(state: SocialUiState, onEvent: (SocialEvent) -> Unit) {
    Panel {
        PanelHeader("FACT BANK") {
            RowAction("+ Fact", onClick = { onEvent(SocialEvent.FactEditing(SocialFact(null, "", ""))) })
        }
        Muted(
            "Every number in a post traces back to a row here. The model writes copy around a " +
                "fact and never invents one.",
            Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
        )
        state.editingFact?.let { FactEditor(it, onEvent) }
        TableHead(columns = listOf("CATEGORY", "FACT", "LAST USED", ""), weights = listOf(1.4f, 4f, 1.6f, 1.4f))
        LazyColumn(Modifier.fillMaxWidth()) {
            items(state.facts, key = { it.id ?: 0L }) { fact ->
                TableRow {
                    Cell(fact.category, Modifier.weight(1.4f))
                    Cell(fact.fact, Modifier.weight(4f))
                    CellSecondary(fact.lastUsedAt ?: "never", Modifier.weight(1.6f))
                    Row(Modifier.weight(1.4f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RowAction("Edit", onClick = { onEvent(SocialEvent.FactEditing(fact)) })
                        fact.id?.let { id ->
                            RowAction("Delete", onClick = { onEvent(SocialEvent.FactDeleted(id)) }, color = AdminTokens.danger)
                        }
                    }
                }
            }
        }
        if (state.facts.isEmpty()) Muted("No facts. The generator has nothing to write about.", Modifier.padding(18.dp))
    }
}

@Composable
private fun FactEditor(fact: SocialFact, onEvent: (SocialEvent) -> Unit) {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AdminField(fact.category, { onEvent(SocialEvent.FactDraftChanged(fact.copy(category = it))) }, "money_saved")
        AdminField(fact.fact, { onEvent(SocialEvent.FactDraftChanged(fact.copy(fact = it))) }, "The verified claim, plain Hinglish")
        AdminField(fact.cta, { onEvent(SocialEvent.FactDraftChanged(fact.copy(cta = it))) }, "Odo — free to start")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryAction("Save fact", onClick = { onEvent(SocialEvent.FactSaved) })
            RowAction("Cancel", onClick = { onEvent(SocialEvent.FactEditing(null)) })
        }
    }
}

/* ------------------------------ bits ------------------------------ */

@Composable
private fun Warning(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp))
        Cell(text, color = AdminTokens.danger)
    }
}

private fun PostingMode.title(): String = when (this) {
    PostingMode.AUTO -> "Auto"
    PostingMode.CUSTOM -> "Custom, with manual approval"
    PostingMode.SCHEDULED -> "Scheduled"
}

private fun PostingMode.explanation(): String = when (this) {
    PostingMode.AUTO -> "Whatever is generated goes out. Nobody is asked."
    PostingMode.CUSTOM -> "Nothing runs on its own. A post is made on demand and always waits for a person."
    PostingMode.SCHEDULED -> "The schedule drives it, and each slot says whether it needs approving."
}

private fun SocialSlot.whenLabel(): String = when {
    dayOfMonth != null && daysOfWeek.isEmpty() -> "day $dayOfMonth"
    daysOfWeek.isEmpty() -> "every day"
    else -> daysOfWeek.joinToString(" ") { DAY_LABELS.getOrElse(it - 1) { "?" } }
}

private fun SocialSlot.platformLabel(): String =
    if (platforms.isEmpty()) "all connected" else platforms.joinToString(", ") { it.label }

@Composable
private fun SocialQueueItem.statusColour(): Color = when {
    hasFailed -> AdminTokens.danger
    status == "published" -> AdminTokens.accent
    else -> AdminTokens.textMuted
}

private fun blankSlot() = SocialSlot(id = "", label = "", timeOfDay = "09:00")

private val DAY_LABELS = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

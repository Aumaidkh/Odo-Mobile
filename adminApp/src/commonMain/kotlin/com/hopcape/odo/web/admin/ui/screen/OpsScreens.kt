package com.hopcape.odo.web.admin.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.admin.domain.ServiceItem
import com.hopcape.odo.web.admin.domain.Subscription
import com.hopcape.odo.web.admin.domain.Ticket
import com.hopcape.odo.web.admin.presentation.catalogue.BillingEvent
import com.hopcape.odo.web.admin.presentation.catalogue.BillingUiState
import com.hopcape.odo.web.admin.presentation.catalogue.CatalogueEvent
import com.hopcape.odo.web.admin.presentation.catalogue.CatalogueUiState
import com.hopcape.odo.web.admin.presentation.catalogue.ItemEditor
import com.hopcape.odo.web.admin.presentation.catalogue.TicketsEvent
import com.hopcape.odo.web.admin.presentation.catalogue.TicketsUiState
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_billing_active
import com.hopcape.odo.web.admin.resources.ad_billing_cancelled
import com.hopcape.odo.web.admin.resources.ad_billing_col_renews
import com.hopcape.odo.web.admin.resources.ad_billing_col_started
import com.hopcape.odo.web.admin.resources.ad_billing_col_status
import com.hopcape.odo.web.admin.resources.ad_billing_col_subscriber
import com.hopcape.odo.web.admin.resources.ad_billing_col_tier
import com.hopcape.odo.web.admin.resources.ad_billing_count
import com.hopcape.odo.web.admin.resources.ad_billing_empty
import com.hopcape.odo.web.admin.resources.ad_billing_no_contact
import com.hopcape.odo.web.admin.resources.ad_billing_past_due
import com.hopcape.odo.web.admin.resources.ad_billing_readonly
import com.hopcape.odo.web.admin.resources.ad_billing_renewing
import com.hopcape.odo.web.admin.resources.ad_billing_title
import com.hopcape.odo.web.admin.resources.ad_cat_col_action
import com.hopcape.odo.web.admin.resources.ad_cat_col_benchmark
import com.hopcape.odo.web.admin.resources.ad_cat_col_interval
import com.hopcape.odo.web.admin.resources.ad_cat_col_item
import com.hopcape.odo.web.admin.resources.ad_cat_count
import com.hopcape.odo.web.admin.resources.ad_cat_edit
import com.hopcape.odo.web.admin.resources.ad_cat_edit_title
import com.hopcape.odo.web.admin.resources.ad_cat_empty
import com.hopcape.odo.web.admin.resources.ad_cat_every_km
import com.hopcape.odo.web.admin.resources.ad_cat_every_months
import com.hopcape.odo.web.admin.resources.ad_cat_field_km
import com.hopcape.odo.web.admin.resources.ad_cat_field_months
import com.hopcape.odo.web.admin.resources.ad_cat_field_name
import com.hopcape.odo.web.admin.resources.ad_cat_field_notes
import com.hopcape.odo.web.admin.resources.ad_cat_field_rupees
import com.hopcape.odo.web.admin.resources.ad_cat_hint
import com.hopcape.odo.web.admin.resources.ad_cat_no_benchmark
import com.hopcape.odo.web.admin.resources.ad_cat_no_interval
import com.hopcape.odo.web.admin.resources.ad_cat_or
import com.hopcape.odo.web.admin.resources.ad_cat_restore
import com.hopcape.odo.web.admin.resources.ad_cat_retire
import com.hopcape.odo.web.admin.resources.ad_cat_retired
import com.hopcape.odo.web.admin.resources.ad_cat_rupees
import com.hopcape.odo.web.admin.resources.ad_cat_title
import com.hopcape.odo.web.admin.resources.ad_cities_cancel
import com.hopcape.odo.web.admin.resources.ad_cities_save
import com.hopcape.odo.web.admin.resources.ad_tickets_col_from
import com.hopcape.odo.web.admin.resources.ad_tickets_col_opened
import com.hopcape.odo.web.admin.resources.ad_tickets_col_priority
import com.hopcape.odo.web.admin.resources.ad_tickets_col_status
import com.hopcape.odo.web.admin.resources.ad_tickets_col_subject
import com.hopcape.odo.web.admin.resources.ad_tickets_col_action
import com.hopcape.odo.web.admin.resources.ad_tickets_count
import com.hopcape.odo.web.admin.resources.ad_tickets_escalate
import com.hopcape.odo.web.admin.resources.ad_tickets_empty
import com.hopcape.odo.web.admin.resources.ad_tickets_open
import com.hopcape.odo.web.admin.resources.ad_tickets_seeded
import com.hopcape.odo.web.admin.resources.ad_tickets_title
import com.hopcape.odo.web.admin.resources.ad_users_showing
import com.hopcape.odo.web.admin.ui.component.AdminField
import com.hopcape.odo.web.admin.ui.component.Banner
import com.hopcape.odo.web.admin.ui.component.Cell
import com.hopcape.odo.web.admin.ui.component.CellPrimary
import com.hopcape.odo.web.admin.ui.component.CellSecondary
import com.hopcape.odo.web.admin.ui.component.FieldLabel
import com.hopcape.odo.web.admin.ui.component.LoadingPanel
import com.hopcape.odo.web.admin.ui.component.Muted
import com.hopcape.odo.web.admin.ui.component.Pager
import com.hopcape.odo.web.admin.ui.component.Panel
import com.hopcape.odo.web.admin.ui.component.RowPanel
import com.hopcape.odo.web.admin.ui.component.PanelHeader
import com.hopcape.odo.web.admin.ui.component.Pill
import com.hopcape.odo.web.admin.ui.component.PrimaryAction
import com.hopcape.odo.web.admin.ui.component.RowAction
import com.hopcape.odo.web.admin.ui.component.StatusText
import com.hopcape.odo.web.admin.ui.component.TableHead
import com.hopcape.odo.web.admin.ui.component.TableRow
import com.hopcape.odo.web.admin.ui.theme.AdminTokens
import com.hopcape.odo.web.admin.ui.theme.AdminType
import com.hopcape.odo.web.core.presentation.state.resolve
import com.hopcape.odo.web.core.presentation.state.Loadable
import org.jetbrains.compose.resources.stringResource

// ── Service catalogue ────────────────────────────────────────────────────────

private val CAT_COLUMNS = listOf(2.4f, 1.6f, 1f, 1.4f)

/**
 * Service items: how often each is due, and what it ought to cost.
 *
 * The benchmark is a reference figure the fairness verdict reads. It is not a
 * price anybody is charged, and the hint on the edit form says so — a column of
 * rupee figures in a staff tool invites exactly that misreading.
 */
@Composable
fun CatalogueScreen(state: CatalogueUiState, onEvent: (CatalogueEvent) -> Unit) {
    // Loading is not emptiness: before this guard the table drew its "nothing
    // here" copy while the first read was still in flight.
    if (state.items is Loadable.Loading) {
        LoadingPanel()
        return
    }
    (state.items as? Loadable.Failed)?.let { failure ->
        LoadingPanel(
            message = failure.message.resolve(),
            onRetry = if (failure.retryable) ({ onEvent(CatalogueEvent.Refresh) }) else null,
        )
        return
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Panel {
                    PanelHeader(stringResource(Res.string.ad_cat_title)) {
                        Pill(stringResource(Res.string.ad_cat_count, state.matching.size))
                    }
                    TableHead(
                        listOf(
                            stringResource(Res.string.ad_cat_col_item),
                            stringResource(Res.string.ad_cat_col_interval),
                            stringResource(Res.string.ad_cat_col_benchmark),
                            stringResource(Res.string.ad_cat_col_action),
                        ),
                        CAT_COLUMNS,
                    )
                }
            }

            if (state.visible.isEmpty()) {
                item { Panel { Muted(stringResource(Res.string.ad_cat_empty)) } }
            } else {
                items(state.visible, key = { it.id }) { item -> ItemRow(item, state.busy, onEvent) }
                item {
                    Pager(
                        state.page, state.matching.size,
                        stringResource(
                            Res.string.ad_users_showing,
                            state.page.first(state.matching.size),
                            state.page.last(state.matching.size),
                            state.matching.size,
                        ),
                        { onEvent(CatalogueEvent.PreviousPage) },
                        { onEvent(CatalogueEvent.NextPage) },
                    )
                }
            }
        }

        state.message?.let { Banner(it.resolve()) { onEvent(CatalogueEvent.MessageDismissed) } }
    }

    state.editor?.let { EditItemDialog(it, state.busy, onEvent) }
}

@Composable
private fun ItemRow(item: ServiceItem, busy: Boolean, onEvent: (CatalogueEvent) -> Unit) {
    RowPanel {
        TableRow {
            Column(Modifier.weight(CAT_COLUMNS[0])) {
                CellPrimary(item.name, color = if (item.isActive) AdminTokens.text else AdminTokens.textFaint)
                CellSecondary(item.notes ?: item.slug)
            }
            Cell(item.intervalLabel(), Modifier.weight(CAT_COLUMNS[1]))
            Cell(
                item.benchmarkRupees?.let { stringResource(Res.string.ad_cat_rupees, it.grouped()) }
                    ?: stringResource(Res.string.ad_cat_no_benchmark),
                Modifier.weight(CAT_COLUMNS[2]),
            )
            Row(Modifier.weight(CAT_COLUMNS[3]), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (!item.isActive) {
                    StatusText(stringResource(Res.string.ad_cat_retired), AdminTokens.textDim)
                }
                RowAction(stringResource(Res.string.ad_cat_edit), { onEvent(CatalogueEvent.EditRequested(item)) }, !busy)
                RowAction(
                    if (item.isActive) stringResource(Res.string.ad_cat_retire) else stringResource(Res.string.ad_cat_restore),
                    { onEvent(CatalogueEvent.ActiveToggled(item)) },
                    !busy,
                )
            }
        }
    }
}

/** "every 10,000 km, or every 12 months" — both, when both apply. */
@Composable
private fun ServiceItem.intervalLabel(): String {
    val km = intervalKm?.let { stringResource(Res.string.ad_cat_every_km, it.toLong().grouped()) }
    val months = intervalMonths?.let { stringResource(Res.string.ad_cat_every_months, it) }
    return when {
        km != null && months != null -> km + stringResource(Res.string.ad_cat_or) + months
        km != null -> km
        months != null -> months
        else -> stringResource(Res.string.ad_cat_no_interval)
    }
}

/**
 * Thousands separators, by hand.
 *
 * `toString()` on a Long gives 1450000, which nobody reads as fourteen and a half
 * thousand rupees at a glance. Grouped in threes rather than the Indian
 * lakh/crore grouping, because the same helper formats kilometres.
 */
private fun Long.grouped(): String =
    toString().reversed().chunked(3).joinToString(",").reversed()

@Composable
private fun EditItemDialog(editor: ItemEditor, busy: Boolean, onEvent: (CatalogueEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(CatalogueEvent.EditorDismissed) },
        containerColor = AdminTokens.card,
        titleContentColor = AdminTokens.text,
        textContentColor = AdminTokens.textStrong,
        title = { Text(stringResource(Res.string.ad_cat_edit_title), style = AdminType.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(stringResource(Res.string.ad_cat_field_name), editor.name.value) {
                    onEvent(CatalogueEvent.NameChanged(it))
                }
                Field(stringResource(Res.string.ad_cat_field_km), editor.km.value) {
                    onEvent(CatalogueEvent.KmChanged(it))
                }
                Field(stringResource(Res.string.ad_cat_field_months), editor.months.value) {
                    onEvent(CatalogueEvent.MonthsChanged(it))
                }
                Field(stringResource(Res.string.ad_cat_field_rupees), editor.rupees.value) {
                    onEvent(CatalogueEvent.RupeesChanged(it))
                }
                Field(stringResource(Res.string.ad_cat_field_notes), editor.notes.value) {
                    onEvent(CatalogueEvent.NotesChanged(it))
                }
                Text(
                    stringResource(Res.string.ad_cat_hint),
                    style = AdminType.caption,
                    color = AdminTokens.textDim,
                )
            }
        },
        confirmButton = {
            PrimaryAction(
                stringResource(Res.string.ad_cities_save),
                { onEvent(CatalogueEvent.EditorSubmitted) },
                enabled = editor.canSubmit && !busy,
            )
        },
        dismissButton = {
            RowAction(stringResource(Res.string.ad_cities_cancel), { onEvent(CatalogueEvent.EditorDismissed) })
        },
    )
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    Column {
        FieldLabel(label.uppercase())
        AdminField(value, onChange, label, Modifier.fillMaxWidth())
    }
}

// ── Support tickets ──────────────────────────────────────────────────────────

private val TICKET_COLUMNS = listOf(2.6f, 1.3f, 0.9f, 0.9f, 0.9f, 1.6f)

private val STATUSES = listOf("open", "pending", "resolved", "closed")

/**
 * The queue, oldest open first.
 *
 * A row expands to show the body rather than opening a page: a ticket is three
 * lines, and a navigation away from the queue to read them is a navigation back.
 */
@Composable
fun TicketsScreen(state: TicketsUiState, onEvent: (TicketsEvent) -> Unit, onOpen: (Long) -> Unit) {
    // Loading is not emptiness: before this guard the table drew its "nothing
    // here" copy while the first read was still in flight.
    if (state.tickets is Loadable.Loading) {
        LoadingPanel()
        return
    }
    (state.tickets as? Loadable.Failed)?.let { failure ->
        LoadingPanel(
            message = failure.message.resolve(),
            onRetry = if (failure.retryable) ({ onEvent(TicketsEvent.Refresh) }) else null,
        )
        return
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Panel {
                    PanelHeader(stringResource(Res.string.ad_tickets_title)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (state.openCount > 0) {
                                Pill(stringResource(Res.string.ad_tickets_open, state.openCount), dot = AdminTokens.accent)
                            }
                            Pill(stringResource(Res.string.ad_tickets_count, state.matching.size))
                        }
                    }
                    Text(
                        stringResource(Res.string.ad_tickets_seeded),
                        style = AdminType.caption,
                        color = AdminTokens.textDim,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    TableHead(
                        listOf(
                            stringResource(Res.string.ad_tickets_col_subject),
                            stringResource(Res.string.ad_tickets_col_from),
                            stringResource(Res.string.ad_tickets_col_opened),
                            stringResource(Res.string.ad_tickets_col_priority),
                            stringResource(Res.string.ad_tickets_col_status),
                            stringResource(Res.string.ad_tickets_col_action),
                        ),
                        TICKET_COLUMNS,
                    )
                }
            }

            if (state.visible.isEmpty()) {
                item { Panel { Muted(stringResource(Res.string.ad_tickets_empty)) } }
            } else {
                items(state.visible, key = { it.id }) { ticket ->
                    TicketRow(ticket, state.busy, onEvent, onOpen)
                }
                item {
                    Pager(
                        state.page, state.matching.size,
                        stringResource(
                            Res.string.ad_users_showing,
                            state.page.first(state.matching.size),
                            state.page.last(state.matching.size),
                            state.matching.size,
                        ),
                        { onEvent(TicketsEvent.PreviousPage) },
                        { onEvent(TicketsEvent.NextPage) },
                    )
                }
            }
        }

        state.message?.let { Banner(it.resolve()) { onEvent(TicketsEvent.MessageDismissed) } }
    }
}

@Composable
private fun TicketRow(ticket: Ticket, busy: Boolean, onEvent: (TicketsEvent) -> Unit, onOpen: (Long) -> Unit) {
    RowPanel {
        TableRow(onClick = { onOpen(ticket.id) }) {
            Column(Modifier.weight(TICKET_COLUMNS[0])) {
                CellPrimary(ticket.subject)
                // The first line of the body, as a hint at what it is about. The
                // whole of it is on the ticket's own page — this cell is one line
                // high, and expanding into it was why clicking a row read as a
                // no-op.
                CellSecondary(ticket.body)
            }
            Cell(ticket.contact, Modifier.weight(TICKET_COLUMNS[1]))
            Cell(ticket.createdAt, Modifier.weight(TICKET_COLUMNS[2]))
            StatusText(ticket.priority.uppercase(), ticket.priorityColor(), Modifier.weight(TICKET_COLUMNS[3]))
            StatusText(ticket.status.uppercase(), ticket.statusColor(), Modifier.weight(TICKET_COLUMNS[4]))
            Row(Modifier.weight(TICKET_COLUMNS[5]), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
                // The next state, not every state. A queue is worked forwards, and
                // four buttons on every row is a wall rather than a choice.
                ticket.nextStatus()?.let { next ->
                    RowAction(next.replaceFirstChar { it.uppercase() }, { onEvent(TicketsEvent.StatusChanged(ticket, next)) }, !busy)
                }
                if (ticket.priority != "urgent" && ticket.isOpen) {
                    RowAction(stringResource(Res.string.ad_tickets_escalate), { onEvent(TicketsEvent.PriorityChanged(ticket, "urgent")) }, !busy)
                }
            }
        }
    }
}

/** open → pending → resolved → closed. Null at the end of the line. */
private fun Ticket.nextStatus(): String? =
    STATUSES.getOrNull(STATUSES.indexOf(status) + 1)

@Composable
private fun Ticket.priorityColor(): Color = when (priority) {
    "urgent" -> AdminTokens.danger
    "high" -> AdminTokens.accent
    "low" -> AdminTokens.textDim
    else -> AdminTokens.textStrong
}

@Composable
private fun Ticket.statusColor(): Color = when (status) {
    "open" -> AdminTokens.text
    "pending" -> AdminTokens.accent
    else -> AdminTokens.textDim
}

// ── Billing ──────────────────────────────────────────────────────────────────

private val BILLING_COLUMNS = listOf(2f, 1f, 1f, 1f, 1f)

/**
 * Subscriptions, read-only.
 *
 * The note says why there is nothing to click: a subscription's truth is the
 * store's, and a row edited here would disagree with what the owner was charged.
 * Comping somebody is the entitlement override on their user record.
 */
@Composable
fun BillingScreen(state: BillingUiState, onEvent: (BillingEvent) -> Unit) {
    // Loading is not emptiness: before this guard the table drew its "nothing
    // here" copy while the first read was still in flight.
    if (state.subscriptions is Loadable.Loading) {
        LoadingPanel()
        return
    }
    (state.subscriptions as? Loadable.Failed)?.let { failure ->
        LoadingPanel(
            message = failure.message.resolve(),
            onRetry = if (failure.retryable) ({ onEvent(BillingEvent.Refresh) }) else null,
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Panel {
                PanelHeader(stringResource(Res.string.ad_billing_title)) {
                    Pill(stringResource(Res.string.ad_billing_count, state.matching.size))
                }
                state.summary?.let { summary ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Pill(stringResource(Res.string.ad_billing_active, summary.active))
                        Pill(stringResource(Res.string.ad_billing_past_due, summary.pastDue), dot = AdminTokens.accent)
                        Pill(stringResource(Res.string.ad_billing_cancelled, summary.cancelled))
                        Pill(stringResource(Res.string.ad_billing_renewing, summary.renewing30d))
                    }
                }
                Text(
                    stringResource(Res.string.ad_billing_readonly),
                    style = AdminType.caption,
                    color = AdminTokens.textDim,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                )
                TableHead(
                    listOf(
                        stringResource(Res.string.ad_billing_col_subscriber),
                        stringResource(Res.string.ad_billing_col_tier),
                        stringResource(Res.string.ad_billing_col_started),
                        stringResource(Res.string.ad_billing_col_renews),
                        stringResource(Res.string.ad_billing_col_status),
                    ),
                    BILLING_COLUMNS,
                )
            }
        }

        if (state.visible.isEmpty()) {
            item { Panel { Muted(stringResource(Res.string.ad_billing_empty)) } }
        } else {
            items(state.visible, key = { it.id }) { row -> SubscriptionRow(row) }
            item {
                Pager(
                    state.page, state.matching.size,
                    stringResource(
                        Res.string.ad_users_showing,
                        state.page.first(state.matching.size),
                        state.page.last(state.matching.size),
                        state.matching.size,
                    ),
                    { onEvent(BillingEvent.PreviousPage) },
                    { onEvent(BillingEvent.NextPage) },
                )
            }
        }
    }
}

@Composable
private fun SubscriptionRow(row: Subscription) {
    RowPanel {
        TableRow {
            Column(Modifier.weight(BILLING_COLUMNS[0])) {
                CellPrimary(row.ownerContact ?: stringResource(Res.string.ad_billing_no_contact))
                CellSecondary(row.ownerId)
            }
            Cell(row.tier.uppercase(), Modifier.weight(BILLING_COLUMNS[1]))
            Cell(row.startedOn, Modifier.weight(BILLING_COLUMNS[2]))
            Cell(row.renewsOn ?: "—", Modifier.weight(BILLING_COLUMNS[3]))
            StatusText(
                row.status.uppercase(),
                when (row.status) {
                    "active" -> AdminTokens.text
                    "past_due" -> AdminTokens.accent
                    "expired" -> AdminTokens.danger
                    else -> AdminTokens.textDim
                },
                Modifier.weight(BILLING_COLUMNS[4]),
            )
        }
    }
}

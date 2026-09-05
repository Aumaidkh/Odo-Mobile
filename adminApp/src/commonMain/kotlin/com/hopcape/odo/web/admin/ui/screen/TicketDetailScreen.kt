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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.admin.domain.Ticket
import com.hopcape.odo.web.admin.presentation.catalogue.TicketsEvent
import com.hopcape.odo.web.admin.presentation.catalogue.TicketsUiState
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_ticket_back
import com.hopcape.odo.web.admin.resources.ad_ticket_attachments
import com.hopcape.odo.web.admin.resources.ad_ticket_attachments_none
import com.hopcape.odo.web.admin.resources.ad_ticket_body
import com.hopcape.odo.web.admin.resources.ad_ticket_details
import com.hopcape.odo.web.admin.resources.ad_ticket_diagnostics
import com.hopcape.odo.web.admin.resources.ad_ticket_diagnostics_hint
import com.hopcape.odo.web.admin.resources.ad_ticket_meta_kind
import com.hopcape.odo.web.admin.resources.ad_ticket_gone
import com.hopcape.odo.web.admin.resources.ad_ticket_meta_contact
import com.hopcape.odo.web.admin.resources.ad_ticket_meta_opened
import com.hopcape.odo.web.admin.resources.ad_ticket_meta_priority
import com.hopcape.odo.web.admin.resources.ad_ticket_meta_status
import com.hopcape.odo.web.admin.resources.ad_ticket_move
import com.hopcape.odo.web.admin.resources.ad_ticket_priority
import com.hopcape.odo.web.admin.resources.ad_tickets_col_action
import com.hopcape.odo.web.admin.ui.component.Banner
import com.hopcape.odo.web.admin.ui.component.LoadingPanel
import com.hopcape.odo.web.admin.ui.component.Muted
import com.hopcape.odo.web.admin.ui.component.Panel
import com.hopcape.odo.web.admin.ui.component.PanelHeader
import com.hopcape.odo.web.admin.ui.component.Pill
import com.hopcape.odo.web.admin.ui.component.PrimaryAction
import com.hopcape.odo.web.admin.ui.component.RowAction
import com.hopcape.odo.web.admin.ui.theme.AdminTokens
import com.hopcape.odo.web.admin.ui.theme.AdminType
import com.hopcape.odo.web.core.presentation.state.Loadable
import com.hopcape.odo.web.core.presentation.state.resolve
import org.jetbrains.compose.resources.stringResource

private val STATUSES = listOf("open", "pending", "resolved", "closed")
private val PRIORITIES = listOf("low", "normal", "high", "urgent")

/**
 * One ticket, in full.
 *
 * Its own page rather than an expanded row. The body is paragraphs somebody has to
 * actually read, and unfolding that inside a table pushes the rest of the queue off
 * the screen — the row was expanding into a single-line cell, which is why clicking
 * a ticket looked like it did nothing at all.
 *
 * Every status and every priority is offered here, unlike the list, which offers
 * only the next one. The list is worked forwards at speed; this is where somebody
 * has stopped to decide, and sending a ticket back to `open` is a decision the
 * queue view should not make easy but this one should allow.
 */
@Composable
fun TicketDetailScreen(
    id: Long,
    state: TicketsUiState,
    onEvent: (TicketsEvent) -> Unit,
    onBack: () -> Unit,
) {
    val ticket = state.tickets.let { it as? Loadable.Ready }?.value?.firstOrNull { it.id == id }

    if (ticket == null) {
        val failure = state.tickets as? Loadable.Failed
        when {
            state.tickets is Loadable.Loading -> LoadingPanel(rows = 3)
            failure != null -> LoadingPanel(
                message = failure.message.resolve(),
                onRetry = if (failure.retryable) ({ onEvent(TicketsEvent.Refresh) }) else null,
            )
            // Loaded, and this id is not in it: a deleted ticket, or a typed URL.
            else -> Column(Modifier.fillMaxSize().padding(26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel { Muted(stringResource(Res.string.ad_ticket_gone, id)) }
                PrimaryAction(stringResource(Res.string.ad_ticket_back), onBack)
            }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Panel {
                    PanelHeader(ticket.subject) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Pill(ticket.priority.uppercase(), dot = ticket.priorityColour())
                            Pill(ticket.status.uppercase())
                            RowAction(stringResource(Res.string.ad_ticket_back), onBack)
                        }
                    }
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetaLine(stringResource(Res.string.ad_ticket_meta_contact), ticket.contact)
                        MetaLine(stringResource(Res.string.ad_ticket_meta_opened), ticket.createdAt)
                        MetaLine(stringResource(Res.string.ad_ticket_meta_status), ticket.status)
                        MetaLine(stringResource(Res.string.ad_ticket_meta_priority), ticket.priority)
                        // Only for a ticket the app filed. A blank row on the eleven that
                        // predate it would read as data that failed to load.
                        if (ticket.isFromApp) {
                            MetaLine(
                                stringResource(Res.string.ad_ticket_meta_kind),
                                ticket.kind.lowercase().replace('_', ' '),
                            )
                        }
                    }
                }
            }

            // The fields the form collected, as fields. This is what lets the queue be worked
            // without reading every body: the area a report was filed against, the job and
            // the figure behind a price correction.
            if (ticket.details.isNotEmpty()) {
                item {
                    Panel {
                        PanelHeader(stringResource(Res.string.ad_ticket_details))
                        Column(modifier = Modifier.padding(16.dp)) {
                            ticket.details.entries.sortedBy { it.key }.forEach { (key, value) ->
                                MetaLine(key.replace('_', ' '), value)
                            }
                        }
                    }
                }
            }

            if (ticket.isFromApp) {
                item {
                    Panel {
                        PanelHeader(stringResource(Res.string.ad_ticket_attachments))
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Said even when there are none, so nobody wonders whether a
                            // photograph failed to load or was never sent.
                            Text(
                                ticket.attachments.joinToString(", ")
                                    .ifBlank { stringResource(Res.string.ad_ticket_attachments_none) },
                                style = AdminType.body,
                                color = AdminTokens.textStrong,
                            )
                        }
                    }
                }
            }

            // The most useful line on a bug report: the code the uploaded logs are filed
            // under, so whoever works this can find them without asking the owner.
            ticket.diagnosticsReference?.let { reference ->
                item {
                    Panel {
                        PanelHeader(stringResource(Res.string.ad_ticket_diagnostics))
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(reference, style = AdminType.body, color = AdminTokens.textStrong)
                            Muted(stringResource(Res.string.ad_ticket_diagnostics_hint))
                        }
                    }
                }
            }

            item {
                Panel {
                    PanelHeader(stringResource(Res.string.ad_ticket_body))
                    Text(
                        // A ticket with an empty body is possible and reads as a bug
                        // unless it is said, so it is said.
                        ticket.body.ifBlank { "—" },
                        style = AdminType.body,
                        color = AdminTokens.textStrong,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            item {
                Panel {
                    PanelHeader(stringResource(Res.string.ad_ticket_move))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        STATUSES.forEach { status ->
                            Choice(
                                label = status.replaceFirstChar { it.uppercase() },
                                selected = ticket.status == status,
                                enabled = !state.busy && ticket.status != status,
                            ) { onEvent(TicketsEvent.StatusChanged(ticket, status)) }
                        }
                    }
                }
            }

            item {
                Panel {
                    PanelHeader(stringResource(Res.string.ad_ticket_priority))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PRIORITIES.forEach { priority ->
                            Choice(
                                label = priority.replaceFirstChar { it.uppercase() },
                                selected = ticket.priority == priority,
                                enabled = !state.busy && ticket.priority != priority,
                            ) { onEvent(TicketsEvent.PriorityChanged(ticket, priority)) }
                        }
                    }
                }
            }
        }

        state.message?.let { Banner(it.resolve()) { onEvent(TicketsEvent.MessageDismissed) } }
    }
}

@Composable
private fun MetaLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), style = AdminType.eyebrow, color = AdminTokens.textFaint, modifier = Modifier.width(110.dp))
        Text(value, style = AdminType.body, color = AdminTokens.textStrong)
    }
}

/**
 * One option in a row of them.
 *
 * The current value is shown selected and disabled rather than hidden: a row of
 * four where one is missing makes somebody hunt for what changed.
 */
@Composable
private fun Choice(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    if (selected) {
        Pill(label, textColor = AdminTokens.text)
    } else {
        RowAction(label, onClick, enabled)
    }
}

@Composable
private fun Ticket.priorityColour(): Color = when (priority) {
    "urgent" -> AdminTokens.danger
    "high" -> AdminTokens.accent
    "low" -> AdminTokens.textDim
    else -> AdminTokens.textStrong
}

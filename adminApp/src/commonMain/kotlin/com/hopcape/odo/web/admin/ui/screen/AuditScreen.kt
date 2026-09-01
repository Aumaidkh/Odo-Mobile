package com.hopcape.odo.web.admin.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.admin.presentation.audit.AuditEvent
import com.hopcape.odo.web.admin.presentation.audit.AuditUiState
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_audit_col_action
import com.hopcape.odo.web.admin.resources.ad_audit_col_actor
import com.hopcape.odo.web.admin.resources.ad_audit_col_table
import com.hopcape.odo.web.admin.resources.ad_audit_col_when
import com.hopcape.odo.web.admin.resources.ad_audit_count
import com.hopcape.odo.web.admin.resources.ad_audit_empty
import com.hopcape.odo.web.admin.resources.ad_audit_system
import com.hopcape.odo.web.admin.resources.ad_audit_title
import com.hopcape.odo.web.admin.ui.component.Cell
import com.hopcape.odo.web.admin.ui.component.CellPrimary
import com.hopcape.odo.web.admin.ui.component.CellSecondary
import com.hopcape.odo.web.admin.ui.component.LoadingPanel
import com.hopcape.odo.web.admin.ui.component.Muted
import com.hopcape.odo.web.admin.resources.ad_users_showing
import com.hopcape.odo.web.admin.ui.component.Pager
import com.hopcape.odo.web.admin.ui.component.Panel
import com.hopcape.odo.web.admin.ui.component.RowPanel
import com.hopcape.odo.web.admin.ui.component.PanelHeader
import com.hopcape.odo.web.admin.ui.component.Pill
import com.hopcape.odo.web.admin.ui.component.TableHead
import com.hopcape.odo.web.admin.ui.component.TableRow
import com.hopcape.odo.web.core.presentation.state.Loadable
import com.hopcape.odo.web.core.presentation.state.resolve
import org.jetbrains.compose.resources.stringResource

private val COLUMNS = listOf(1.4f, 1.6f, 2f, 1.2f)

/**
 * Who changed what, newest first.
 *
 * Read-only, and there is no way to make it anything else: `admin_audit_log` has
 * a select policy and nothing more, so the definer-owned trigger is its only
 * writer and no session can rewrite what it recorded.
 *
 * The header's search box filters it — there is one box in the chrome and this
 * screen does not need a second.
 */
@Composable
fun AuditScreen(state: AuditUiState, onEvent: (AuditEvent) -> Unit) {
    // Loading is not emptiness. Before this guard the table drew its "nothing here"
    // copy while the first read was still in flight, which is indistinguishable
    // from a genuinely empty table — and on a cold Wasm boot that is a long time to
    // be telling somebody a lie.
    if (state.entries is Loadable.Loading) {
        LoadingPanel()
        return
    }
    (state.entries as? Loadable.Failed)?.let { failure ->
        LoadingPanel(
            message = failure.message.resolve(),
            onRetry = if (failure.retryable) ({ onEvent(AuditEvent.Refresh) }) else null,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Panel {
                PanelHeader(stringResource(Res.string.ad_audit_title)) {
                    Pill(stringResource(Res.string.ad_audit_count, state.matching.size))
                }
                TableHead(
                    listOf(
                        stringResource(Res.string.ad_audit_col_action),
                        stringResource(Res.string.ad_audit_col_table),
                        stringResource(Res.string.ad_audit_col_actor),
                        stringResource(Res.string.ad_audit_col_when),
                    ),
                    COLUMNS,
                )
            }
        }

        if (state.visible.isEmpty()) {
            item { Panel { Muted(stringResource(Res.string.ad_audit_empty)) } }
        } else {
            items(state.visible, key = { it.id }) { entry ->
                RowPanel {
                    TableRow {
                        CellPrimary(entry.action, Modifier.weight(COLUMNS[0]))
                        Cell(entry.subjectType, Modifier.weight(COLUMNS[1]))
                        Column(Modifier.weight(COLUMNS[2])) {
                            // "system" rather than a blank: a service-role change is
                            // unattributed on purpose, and an empty column reads
                            // like the log failed to record something.
                            Cell(entry.actorEmail ?: stringResource(Res.string.ad_audit_system))
                            entry.subjectId?.let { CellSecondary(it) }
                        }
                        Cell(entry.at, Modifier.weight(COLUMNS[3]))
                    }
                }
            }
            item {
                Pager(
                    page = state.page,
                    total = state.matching.size,
                    label = stringResource(
                        Res.string.ad_users_showing,
                        state.page.first(state.matching.size),
                        state.page.last(state.matching.size),
                        state.matching.size,
                    ),
                    onPrevious = { onEvent(AuditEvent.PreviousPage) },
                    onNext = { onEvent(AuditEvent.NextPage) },
                )
            }
        }
    }
}

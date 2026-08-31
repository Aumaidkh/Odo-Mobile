package com.hopcape.odo.web.admin.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.admin.presentation.audit.AuditEvent
import com.hopcape.odo.web.admin.presentation.audit.AuditUiState
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_audit_count
import com.hopcape.odo.web.admin.resources.ad_audit_empty
import com.hopcape.odo.web.admin.resources.ad_audit_filter
import com.hopcape.odo.web.admin.resources.ad_audit_system
import com.hopcape.odo.web.admin.resources.ad_audit_title
import org.jetbrains.compose.resources.stringResource

/**
 * Who changed what, newest first.
 *
 * Read-only, and there is no way to make it anything else: `admin_audit_log` has
 * a select policy and nothing more, so the definer-owned trigger is its only
 * writer and no session can rewrite what it recorded.
 */
@Composable
fun AuditScreen(state: AuditUiState, onEvent: (AuditEvent) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Column(Modifier.padding(top = 32.dp)) {
                Text(stringResource(Res.string.ad_audit_title), style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = state.filter,
                    onValueChange = { onEvent(AuditEvent.FilterChanged(it)) },
                    label = { Text(stringResource(Res.string.ad_audit_filter)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        }
        item {
            SectionHeading(
                title = stringResource(Res.string.ad_audit_title),
                count = stringResource(Res.string.ad_audit_count, state.visible.size),
            )
        }

        if (state.visible.isEmpty()) {
            item { Muted(stringResource(Res.string.ad_audit_empty)) }
        } else {
            items(state.visible, key = { it.id }) { entry ->
                RowCard {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${entry.action} ${entry.subjectType}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            // "system" rather than a blank: a service-role change is
                            // unattributed on purpose, and an empty column reads
                            // like the log failed to record something.
                            entry.actorEmail ?: stringResource(Res.string.ad_audit_system),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        entry.at,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { Row(Modifier.padding(bottom = 32.dp)) {} }
    }
}

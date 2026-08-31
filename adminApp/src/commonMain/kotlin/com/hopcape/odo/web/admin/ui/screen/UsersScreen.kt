package com.hopcape.odo.web.admin.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.admin.domain.ManagedUser
import com.hopcape.odo.web.admin.domain.Restriction
import com.hopcape.odo.web.admin.presentation.users.UsersEvent
import com.hopcape.odo.web.admin.presentation.users.UsersUiState
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_users_access
import com.hopcape.odo.web.admin.resources.ad_users_access_blocked
import com.hopcape.odo.web.admin.resources.ad_users_access_blocked_hint
import com.hopcape.odo.web.admin.resources.ad_users_access_none
import com.hopcape.odo.web.admin.resources.ad_users_access_none_hint
import com.hopcape.odo.web.admin.resources.ad_users_access_read_only
import com.hopcape.odo.web.admin.resources.ad_users_access_read_only_hint
import com.hopcape.odo.web.admin.resources.ad_users_apply
import com.hopcape.odo.web.admin.resources.ad_users_clear
import com.hopcape.odo.web.admin.resources.ad_users_entitlements
import com.hopcape.odo.web.admin.resources.ad_users_entitlements_empty
import com.hopcape.odo.web.admin.resources.ad_users_feature
import com.hopcape.odo.web.admin.resources.ad_users_find
import com.hopcape.odo.web.admin.resources.ad_users_grant
import com.hopcape.odo.web.admin.resources.ad_users_granted
import com.hopcape.odo.web.admin.resources.ad_users_hint
import com.hopcape.odo.web.admin.resources.ad_users_no_email
import com.hopcape.odo.web.admin.resources.ad_users_override_hint
import com.hopcape.odo.web.admin.resources.ad_users_reason
import com.hopcape.odo.web.admin.resources.ad_users_revoke
import com.hopcape.odo.web.admin.resources.ad_users_revoked
import com.hopcape.odo.web.admin.resources.ad_users_search
import com.hopcape.odo.web.admin.resources.ad_users_since
import com.hopcape.odo.web.admin.resources.ad_users_title
import com.hopcape.odo.web.core.presentation.state.resolve
import org.jetbrains.compose.resources.stringResource

/**
 * One account at a time.
 *
 * There is no list, and that is the design rather than an omission: support looks
 * somebody up because that person is on the phone, and a browsable directory of
 * every account is a different tool with a different risk profile.
 */
@Composable
fun UsersScreen(state: UsersUiState, onEvent: (UsersEvent) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(Res.string.ad_users_title), style = MaterialTheme.typography.headlineSmall)

            Row(
                modifier = Modifier.widthIn(max = 640.dp).padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = state.query.value,
                    onValueChange = { onEvent(UsersEvent.QueryChanged(it)) },
                    label = { Text(stringResource(Res.string.ad_users_search)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { onEvent(UsersEvent.Search) }, enabled = state.canSearch) {
                    Text(stringResource(Res.string.ad_users_find))
                }
            }
            Muted(stringResource(Res.string.ad_users_hint))

            state.user?.let { user ->
                Identity(user)
                AccessSection(state, onEvent)
                EntitlementSection(state, user, onEvent)
            }
        }

        state.message?.let { message ->
            Banner(message.resolve()) { onEvent(UsersEvent.MessageDismissed) }
        }
    }
}

@Composable
private fun Identity(user: ManagedUser) {
    Column(Modifier.padding(top = 24.dp)) {
        Text(user.phone ?: user.id, style = MaterialTheme.typography.titleMedium)
        Text(
            user.email ?: stringResource(Res.string.ad_users_no_email),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(Res.string.ad_users_since, user.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccessSection(state: UsersUiState, onEvent: (UsersEvent) -> Unit) {
    SectionHeading(title = stringResource(Res.string.ad_users_access), count = "")
    Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Restriction.entries.forEach { option ->
                FilterChip(
                    selected = state.proposed == option,
                    onClick = { onEvent(UsersEvent.RestrictionPicked(option)) },
                    label = { Text(option.label()) },
                )
            }
        }
        // What the chosen level actually does, in the panel rather than in a doc:
        // "blocked" and "read only" are very different promises to make about
        // somebody's account, and the difference is not in the words.
        Muted(state.proposed.hint())

        OutlinedTextField(
            value = state.reason.value,
            onValueChange = { onEvent(UsersEvent.ReasonChanged(it)) },
            label = { Text(stringResource(Res.string.ad_users_reason)) },
            singleLine = true,
            isError = state.reasonError != null,
            supportingText = state.reasonError?.let { { Text(it.resolve()) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onEvent(UsersEvent.RestrictionApplied) },
            enabled = state.restrictionChanged && !state.busy,
        ) {
            Text(stringResource(Res.string.ad_users_apply))
        }
    }
}

@Composable
private fun EntitlementSection(state: UsersUiState, user: ManagedUser, onEvent: (UsersEvent) -> Unit) {
    SectionHeading(title = stringResource(Res.string.ad_users_entitlements), count = "")
    Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (user.entitlements.isEmpty()) {
            Muted(stringResource(Res.string.ad_users_entitlements_empty))
        } else {
            user.entitlements.forEach { override ->
                RowCard {
                    Column(Modifier.weight(1f)) {
                        Text(override.feature, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (override.granted) {
                                stringResource(Res.string.ad_users_granted)
                            } else {
                                stringResource(Res.string.ad_users_revoked)
                            } + " · " + override.reason,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = { onEvent(UsersEvent.EntitlementCleared(override.feature)) },
                        enabled = !state.busy,
                    ) {
                        Text(stringResource(Res.string.ad_users_clear))
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.feature.value,
                onValueChange = { onEvent(UsersEvent.FeatureChanged(it)) },
                label = { Text(stringResource(Res.string.ad_users_feature)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { onEvent(UsersEvent.EntitlementSet(true)) }, enabled = !state.busy) {
                Text(stringResource(Res.string.ad_users_grant))
            }
            TextButton(onClick = { onEvent(UsersEvent.EntitlementSet(false)) }, enabled = !state.busy) {
                Text(stringResource(Res.string.ad_users_revoke))
            }
        }
        Muted(stringResource(Res.string.ad_users_override_hint))
    }
}

@Composable
private fun Restriction.label(): String = when (this) {
    Restriction.None -> stringResource(Res.string.ad_users_access_none)
    Restriction.ReadOnly -> stringResource(Res.string.ad_users_access_read_only)
    Restriction.Blocked -> stringResource(Res.string.ad_users_access_blocked)
}

@Composable
private fun Restriction.hint(): String = when (this) {
    Restriction.None -> stringResource(Res.string.ad_users_access_none_hint)
    Restriction.ReadOnly -> stringResource(Res.string.ad_users_access_read_only_hint)
    Restriction.Blocked -> stringResource(Res.string.ad_users_access_blocked_hint)
}

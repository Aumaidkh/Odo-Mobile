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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.admin.domain.DirectoryUser
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
import com.hopcape.odo.web.admin.resources.ad_users_account
import com.hopcape.odo.web.admin.resources.ad_users_apply
import com.hopcape.odo.web.admin.resources.ad_users_clear
import com.hopcape.odo.web.admin.resources.ad_users_close
import com.hopcape.odo.web.admin.resources.ad_users_col_action
import com.hopcape.odo.web.admin.resources.ad_users_col_cars
import com.hopcape.odo.web.admin.resources.ad_users_col_city
import com.hopcape.odo.web.admin.resources.ad_users_col_phone
import com.hopcape.odo.web.admin.resources.ad_users_col_plan
import com.hopcape.odo.web.admin.resources.ad_users_col_status
import com.hopcape.odo.web.admin.resources.ad_users_col_user
import com.hopcape.odo.web.admin.resources.ad_users_directory
import com.hopcape.odo.web.admin.resources.ad_users_empty
import com.hopcape.odo.web.admin.resources.ad_users_entitlements
import com.hopcape.odo.web.admin.resources.ad_users_entitlements_empty
import com.hopcape.odo.web.admin.resources.ad_users_feature
import com.hopcape.odo.web.admin.resources.ad_users_grant
import com.hopcape.odo.web.admin.resources.ad_users_granted
import com.hopcape.odo.web.admin.resources.ad_users_masked_note
import com.hopcape.odo.web.admin.resources.ad_users_next
import com.hopcape.odo.web.admin.resources.ad_users_no_email
import com.hopcape.odo.web.admin.resources.ad_users_open
import com.hopcape.odo.web.admin.resources.ad_users_override_hint
import com.hopcape.odo.web.admin.resources.ad_users_plan_granted
import com.hopcape.odo.web.admin.resources.ad_users_plan_revoked
import com.hopcape.odo.web.admin.resources.ad_users_plan_store
import com.hopcape.odo.web.admin.resources.ad_users_previous
import com.hopcape.odo.web.admin.resources.ad_users_reason
import com.hopcape.odo.web.admin.resources.ad_users_reveal
import com.hopcape.odo.web.admin.resources.ad_users_revoke
import com.hopcape.odo.web.admin.resources.ad_users_revoked
import com.hopcape.odo.web.admin.resources.ad_users_showing
import com.hopcape.odo.web.admin.resources.ad_users_since
import com.hopcape.odo.web.admin.resources.ad_users_status_blocked
import com.hopcape.odo.web.admin.resources.ad_users_status_none
import com.hopcape.odo.web.admin.resources.ad_users_status_read_only
import com.hopcape.odo.web.admin.resources.ad_users_unnamed
import com.hopcape.odo.web.admin.ui.component.AdminField
import com.hopcape.odo.web.admin.ui.component.Banner
import com.hopcape.odo.web.admin.ui.component.Cell
import com.hopcape.odo.web.admin.ui.component.CellPrimary
import com.hopcape.odo.web.admin.ui.component.CellSecondary
import com.hopcape.odo.web.admin.ui.component.FieldLabel
import com.hopcape.odo.web.admin.ui.component.Muted
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val COLUMNS = listOf(1.9f, 1.5f, 1f, 0.6f, 0.9f, 0.9f, 0.9f)

/**
 * The directory, with one account opened over it.
 *
 * Contact details arrive masked from the database rather than being masked here.
 * A client that received the real number and drew dots over it would be one
 * developer console away from showing everything, and the reveal would never be
 * logged — so the only route to a real value is the reveal call, which writes its
 * audit row before it answers.
 */
@Composable
fun UsersScreen(state: UsersUiState, onEvent: (UsersEvent) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            state.user?.let { user ->
                item { AccountPanel(user, state, onEvent) }
            }

            item {
                Panel {
                    PanelHeader(stringResource(Res.string.ad_users_directory)) {
                        Pill(stringResource(Res.string.ad_users_masked_note), dot = AdminTokens.accent)
                    }
                    TableHead(
                        listOf(
                            stringResource(Res.string.ad_users_col_user),
                            stringResource(Res.string.ad_users_col_phone),
                            stringResource(Res.string.ad_users_col_city),
                            stringResource(Res.string.ad_users_col_cars),
                            stringResource(Res.string.ad_users_col_plan),
                            stringResource(Res.string.ad_users_col_status),
                            stringResource(Res.string.ad_users_col_action),
                        ),
                        COLUMNS,
                    )
                }
            }

            if (state.directory.isEmpty()) {
                item { Panel { Muted(stringResource(Res.string.ad_users_empty)) } }
            } else {
                items(state.directory, key = { it.id }) { row ->
                    RowPanel { DirectoryRow(row, state, onEvent) }
                }
                item { Pager(state, onEvent) }
            }
        }

        state.message?.let { message ->
            Banner(message.resolve()) { onEvent(UsersEvent.MessageDismissed) }
        }
    }
}

@Composable
private fun DirectoryRow(row: DirectoryUser, state: UsersUiState, onEvent: (UsersEvent) -> Unit) {
    val revealed = state.revealed[row.id]
    TableRow {
        Column(Modifier.weight(COLUMNS[0])) {
            CellPrimary(row.name ?: stringResource(Res.string.ad_users_unnamed))
            CellSecondary(revealed ?: row.maskedEmail ?: stringResource(Res.string.ad_users_no_email))
        }
        // The masked value is a control, not a label: clicking it is the reveal,
        // which is the only way to the real number and always logged.
        Row(
            modifier = Modifier.weight(COLUMNS[1]),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Cell(revealed ?: row.maskedPhone ?: "—")
            if (revealed == null && row.maskedPhone != null) {
                RowAction(stringResource(Res.string.ad_users_reveal), { onEvent(UsersEvent.Revealed(row.id)) })
            }
        }
        Cell(row.city ?: "—", Modifier.weight(COLUMNS[2]))
        Cell(row.cars.toString(), Modifier.weight(COLUMNS[3]))
        PlanCell(row.proOverride, Modifier.weight(COLUMNS[4]))
        StatusText(stringResource(row.restriction.labelResource()), row.restriction.color(), Modifier.weight(COLUMNS[5]))
        Row(Modifier.weight(COLUMNS[6]), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            RowAction(stringResource(Res.string.ad_users_open), { onEvent(UsersEvent.Opened(row.id)) }, !state.busy)
        }
    }
}

/**
 * What support has decided about this account's plan.
 *
 * "STORE" means nobody has decided anything and RevenueCat's answer stands. The
 * plan itself is not shown, because the database does not know it — the store
 * does, and reporting a stale copy of it here would be worse than saying nothing.
 */
@Composable
private fun PlanCell(override: Boolean?, modifier: Modifier) {
    when (override) {
        true -> StatusText(stringResource(Res.string.ad_users_plan_granted), AdminTokens.text, modifier)
        false -> StatusText(stringResource(Res.string.ad_users_plan_revoked), AdminTokens.accent, modifier)
        null -> StatusText(stringResource(Res.string.ad_users_plan_store), AdminTokens.textDim, modifier)
    }
}

@Composable
private fun Pager(state: UsersUiState, onEvent: (UsersEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(Res.string.ad_users_showing, state.firstShown, state.lastShown, state.total),
            style = AdminType.body,
            color = AdminTokens.textFaint,
            modifier = Modifier.weight(1f),
        )
        RowAction(stringResource(Res.string.ad_users_previous), { onEvent(UsersEvent.PreviousPage) }, state.hasPrevious)
        RowAction(stringResource(Res.string.ad_users_next), { onEvent(UsersEvent.NextPage) }, state.hasNext)
    }
}

/** One account, opened. Everything that can be changed about it is here. */
@Composable
private fun AccountPanel(user: ManagedUser, state: UsersUiState, onEvent: (UsersEvent) -> Unit) {
    Panel {
        PanelHeader(stringResource(Res.string.ad_users_account)) {
            RowAction(stringResource(Res.string.ad_users_close), { onEvent(UsersEvent.Closed) })
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                CellPrimary(user.phone ?: user.id)
                CellSecondary(user.email ?: stringResource(Res.string.ad_users_no_email))
                CellSecondary(stringResource(Res.string.ad_users_since, user.createdAt))
            }

            Column(Modifier.widthIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel(stringResource(Res.string.ad_users_access).uppercase())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Restriction.entries.forEach { option ->
                        // Keyed, so each chip keeps its own slot — see
                        // AdminRoute.labelResource() for what happens without it.
                        key(option) {
                            RestrictionChip(option, state.proposed == option) {
                                onEvent(UsersEvent.RestrictionPicked(option))
                            }
                        }
                    }
                }
                // What the chosen level actually does, on the page rather than in a
                // doc: "blocked" and "read only" are very different promises to make
                // about somebody's account, and the difference is not in the words.
                Text(stringResource(state.proposed.hintResource()), style = AdminType.caption, color = AdminTokens.textFaint)
                AdminField(
                    state.reason.value,
                    { onEvent(UsersEvent.ReasonChanged(it)) },
                    stringResource(Res.string.ad_users_reason),
                    Modifier.fillMaxWidth(),
                )
                state.reasonError?.let { StatusText(it.resolve(), AdminTokens.danger) }
                PrimaryAction(
                    stringResource(Res.string.ad_users_apply),
                    { onEvent(UsersEvent.RestrictionApplied) },
                    enabled = state.restrictionChanged && !state.busy,
                )
            }

            Column(Modifier.widthIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel(stringResource(Res.string.ad_users_entitlements).uppercase())
                if (user.entitlements.isEmpty()) {
                    Text(
                        stringResource(Res.string.ad_users_entitlements_empty),
                        style = AdminType.body,
                        color = AdminTokens.textFaint,
                    )
                } else {
                    user.entitlements.forEach { override ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                CellPrimary(override.feature)
                                CellSecondary(
                                    (
                                        if (override.granted) {
                                            stringResource(Res.string.ad_users_granted)
                                        } else {
                                            stringResource(Res.string.ad_users_revoked)
                                        }
                                        ) + " · " + override.reason,
                                )
                            }
                            RowAction(
                                stringResource(Res.string.ad_users_clear),
                                { onEvent(UsersEvent.EntitlementCleared(override.feature)) },
                                !state.busy,
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AdminField(
                        state.feature.value,
                        { onEvent(UsersEvent.FeatureChanged(it)) },
                        stringResource(Res.string.ad_users_feature),
                        Modifier.weight(1f),
                    )
                    PrimaryAction(
                        stringResource(Res.string.ad_users_grant),
                        { onEvent(UsersEvent.EntitlementSet(true)) },
                        enabled = !state.busy,
                    )
                    RowAction(
                        stringResource(Res.string.ad_users_revoke),
                        { onEvent(UsersEvent.EntitlementSet(false)) },
                        !state.busy,
                    )
                }
                Text(
                    stringResource(Res.string.ad_users_override_hint),
                    style = AdminType.caption,
                    color = AdminTokens.textDim,
                )
            }
        }
    }
}

@Composable
private fun RestrictionChip(option: Restriction, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .background(if (selected) AdminTokens.text else AdminTokens.field)
            .border(
                1.dp,
                if (selected) AdminTokens.text else AdminTokens.border,
                androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            // One call site, changing argument.
            stringResource(option.labelResource()),
            style = AdminType.strong,
            color = if (selected) AdminTokens.canvas else AdminTokens.textStrong,
        )
    }
}

/**
 * The resource, picked without composing — see `AdminRoute.labelResource()` for why
 * a `when` full of `stringResource` calls breaks inside an unkeyed loop.
 */
private fun Restriction.labelResource(): StringResource = when (this) {
    Restriction.None -> Res.string.ad_users_status_none
    Restriction.ReadOnly -> Res.string.ad_users_status_read_only
    Restriction.Blocked -> Res.string.ad_users_status_blocked
}

@Composable
private fun Restriction.color(): Color = when (this) {
    Restriction.None -> AdminTokens.textDim
    Restriction.ReadOnly -> AdminTokens.accent
    Restriction.Blocked -> AdminTokens.danger
}

private fun Restriction.hintResource(): StringResource = when (this) {
    Restriction.None -> Res.string.ad_users_access_none_hint
    Restriction.ReadOnly -> Res.string.ad_users_access_read_only_hint
    Restriction.Blocked -> Res.string.ad_users_access_blocked_hint
}

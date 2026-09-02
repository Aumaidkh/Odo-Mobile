package com.hopcape.odo.web.admin.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import com.hopcape.odo.web.admin.presentation.roles.NewRole
import com.hopcape.odo.web.admin.presentation.roles.NewStaff
import com.hopcape.odo.web.admin.ui.component.AdminField
import com.hopcape.odo.web.admin.ui.component.FieldLabel
import com.hopcape.odo.web.admin.ui.component.PrimaryAction
import com.hopcape.odo.web.admin.ui.component.RowAction
import com.hopcape.odo.web.admin.ui.component.StatusText
import com.hopcape.odo.web.admin.ui.component.Pill
import com.hopcape.odo.web.admin.ui.component.Muted
import com.hopcape.odo.web.admin.domain.Permission
import com.hopcape.odo.web.admin.presentation.roles.RolesEvent
import com.hopcape.odo.web.admin.presentation.roles.RolesUiState
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_cities_cancel
import com.hopcape.odo.web.admin.resources.ad_roles_description
import com.hopcape.odo.web.admin.resources.ad_roles_name
import com.hopcape.odo.web.admin.resources.ad_roles_new
import com.hopcape.odo.web.admin.resources.ad_roles_new_hint
import com.hopcape.odo.web.admin.resources.ad_roles_new_title
import com.hopcape.odo.web.admin.resources.ad_roles_slug
import com.hopcape.odo.web.admin.resources.ad_roles_slug_error
import com.hopcape.odo.web.admin.resources.ad_staff_add
import com.hopcape.odo.web.admin.resources.ad_staff_invite
import com.hopcape.odo.web.admin.resources.ad_staff_add_hint
import com.hopcape.odo.web.admin.resources.ad_staff_add_title
import com.hopcape.odo.web.admin.resources.ad_staff_email
import com.hopcape.odo.web.admin.resources.ad_staff_empty
import com.hopcape.odo.web.admin.resources.ad_staff_hint
import com.hopcape.odo.web.admin.resources.ad_staff_name
import com.hopcape.odo.web.admin.resources.ad_staff_pending
import com.hopcape.odo.web.admin.resources.ad_staff_restore
import com.hopcape.odo.web.admin.resources.ad_staff_revoke
import com.hopcape.odo.web.admin.resources.ad_staff_revoked
import com.hopcape.odo.web.admin.resources.ad_staff_roles
import com.hopcape.odo.web.admin.resources.ad_staff_title
import com.hopcape.odo.web.admin.resources.ad_staff_you
import com.hopcape.odo.web.admin.resources.ad_roles_denied
import com.hopcape.odo.web.admin.resources.ad_roles_granted
import com.hopcape.odo.web.admin.resources.ad_roles_list
import com.hopcape.odo.web.admin.resources.ad_roles_matrix
import com.hopcape.odo.web.admin.resources.ad_roles_matrix_hint
import com.hopcape.odo.web.admin.resources.ad_roles_members
import com.hopcape.odo.web.admin.resources.ad_roles_resource
import com.hopcape.odo.web.admin.resources.ad_roles_two_states
import com.hopcape.odo.web.admin.ui.component.Banner
import com.hopcape.odo.web.admin.ui.component.CellPrimary
import com.hopcape.odo.web.admin.ui.component.CellSecondary
import com.hopcape.odo.web.admin.ui.component.Hairline
import com.hopcape.odo.web.admin.ui.component.Panel
import com.hopcape.odo.web.admin.ui.component.PanelHeader
import com.hopcape.odo.web.admin.ui.theme.AdminTokens
import com.hopcape.odo.web.admin.ui.theme.AdminType
import com.hopcape.odo.web.core.presentation.state.resolve
import com.hopcape.odo.web.admin.ui.component.LoadingPanel
import com.hopcape.odo.web.core.presentation.state.Loadable
import org.jetbrains.compose.resources.stringResource

/**
 * The permission grid: roles across the top, resources down the side.
 *
 * Two states per cell, not the design's four. `admin_role_permissions` stores a
 * permission or does not, and `admin_has()` answers yes or no — drawing a READ
 * cell the server cannot tell from WRITE would be a promise nothing keeps. The
 * note on the page says so, so nobody has to read this comment to find out.
 */
@Composable
fun RolesScreen(state: RolesUiState, onEvent: (RolesEvent) -> Unit) {
    // Loading is not emptiness: before this guard the table drew its "nothing
    // here" copy while the first read was still in flight.
    if (state.roles is Loadable.Loading) {
        LoadingPanel()
        return
    }
    (state.roles as? Loadable.Failed)?.let { failure ->
        LoadingPanel(
            message = failure.message.resolve(),
            onRetry = if (failure.retryable) ({ onEvent(RolesEvent.Refresh) }) else null,
        )
        return
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StaffPanel(state, onEvent)

            Panel {
                PanelHeader(stringResource(Res.string.ad_roles_matrix))
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        stringResource(Res.string.ad_roles_matrix_hint),
                        style = AdminType.body,
                        color = AdminTokens.textFaint,
                    )
                    Text(
                        stringResource(Res.string.ad_roles_two_states),
                        style = AdminType.caption,
                        color = AdminTokens.textDim,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Hairline(AdminTokens.railBorder)
                Matrix(state, onEvent)
            }

            Panel {
                PanelHeader(stringResource(Res.string.ad_roles_list)) {
                    PrimaryAction(
                        stringResource(Res.string.ad_roles_new),
                        { onEvent(RolesEvent.NewRoleRequested) },
                        !state.busy,
                    )
                }
                state.allRoles.forEach { role ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEvent(RolesEvent.RoleSelected(role.slug)) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            CellPrimary(role.name)
                            CellSecondary(role.description)
                        }
                        Text(
                            stringResource(Res.string.ad_roles_members, role.memberCount),
                            style = AdminType.strong,
                            color = AdminTokens.textFaint,
                        )
                    }
                    Hairline()
                }
            }
        }

        state.message?.let { message ->
            Banner(message.resolve()) { onEvent(RolesEvent.MessageDismissed) }
        }
    }

    state.newRole?.let { NewRoleDialog(it, state.busy, onEvent) }
    state.newStaff?.let { NewStaffDialog(it, state.busy, onEvent) }
}

/**
 * Who is staff, and what each of them holds.
 *
 * First on the page because it is the question somebody arrives with. The grid
 * below decides what a *role* may do; this decides who has one, and the two were
 * previously only half here — the grid shipped, and adding a person meant an INSERT
 * by hand against the database.
 */
@Composable
private fun StaffPanel(state: RolesUiState, onEvent: (RolesEvent) -> Unit) {
    Panel {
        PanelHeader(stringResource(Res.string.ad_staff_title)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(state.staff.count { it.isActive }.toString())
                PrimaryAction(
                    stringResource(Res.string.ad_staff_add),
                    { onEvent(RolesEvent.NewStaffRequested) },
                    !state.busy,
                )
            }
        }
        Text(
            stringResource(Res.string.ad_staff_hint),
            style = AdminType.caption,
            color = AdminTokens.textDim,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Hairline(AdminTokens.railBorder)

        if (state.staff.isEmpty()) {
            Muted(stringResource(Res.string.ad_staff_empty))
            return@Panel
        }

        state.staff.forEach { member ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CellPrimary(
                                member.name,
                                color = if (member.isActive) AdminTokens.text else AdminTokens.textFaint,
                            )
                            if (state.isSelf(member)) {
                                Text(
                                    stringResource(Res.string.ad_staff_you),
                                    style = AdminType.micro,
                                    color = AdminTokens.textDim,
                                )
                            }
                        }
                        CellSecondary(member.email)
                        // An address added but never signed into looks exactly like
                        // a working account until somebody asks why their
                        // permissions do nothing.
                        if (!member.boundToAccount) {
                            StatusText(stringResource(Res.string.ad_staff_pending), AdminTokens.accent)
                        }
                    }
                    if (!member.isActive) {
                        StatusText(stringResource(Res.string.ad_staff_revoked), AdminTokens.textDim)
                    }
                    // Offered for anybody active, not just the never-signed-in:
                    // "I never got the email" and "I forgot my password" are the
                    // same button, and both are answered by a fresh reset link.
                    if (member.isActive) {
                        RowAction(
                            stringResource(Res.string.ad_staff_invite),
                            { onEvent(RolesEvent.InviteSent(member)) },
                            !state.busy,
                        )
                    }
                    RowAction(
                        if (member.isActive) {
                            stringResource(Res.string.ad_staff_revoke)
                        } else {
                            stringResource(Res.string.ad_staff_restore)
                        },
                        { onEvent(RolesEvent.StaffActiveToggled(member)) },
                        // Refused for yourself: deactivating your own row locks you
                        // out of the only screen that could undo it.
                        !state.busy && state.mayDeactivate(member),
                        color = if (member.isActive) AdminTokens.danger else AdminTokens.text,
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(Res.string.ad_staff_roles),
                        style = AdminType.eyebrow,
                        color = AdminTokens.textFaint,
                    )
                    state.allRoles.forEach { role ->
                        val held = role.slug in member.roles
                        RoleChip(
                            label = role.name,
                            held = held,
                            // The last role cannot be taken off your own row for
                            // the same reason as above.
                            enabled = !state.busy && (!held || state.mayRevokeRole(member, role.slug)),
                        ) { onEvent(RolesEvent.StaffRoleToggled(member, role.slug)) }
                    }
                }
            }
            Hairline()
        }
    }
}

/**
 * One role on one person: filled when held, outlined when not, clickable either way.
 *
 * Clickable **when held** is the point — that is how a role is taken off. An earlier
 * version drew the held state as a plain `Pill`, which has no click at all, so roles
 * could be granted and never revoked.
 */
@Composable
private fun RoleChip(label: String, held: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (held) AdminTokens.text else AdminTokens.field)
            .border(1.dp, if (held) AdminTokens.text else AdminTokens.border, shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            style = AdminType.strong,
            color = when {
                // Filled chip: the label sits on the text colour, so it takes the
                // canvas colour to stay readable in both themes.
                held -> AdminTokens.canvas
                enabled -> AdminTokens.textStrong
                else -> AdminTokens.textDim
            },
            maxLines = 1,
        )
    }
}

/**
 * The grid itself, horizontally scrollable.
 *
 * Nine resources by however many roles exist does not fit a laptop at a readable
 * cell width, and shrinking the cells to fit is how a grid becomes unreadable.
 */
@Composable
private fun Matrix(state: RolesUiState, onEvent: (RolesEvent) -> Unit) {
    val roles = state.allRoles
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.background(AdminTokens.tableHeader).padding(horizontal = 16.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(Res.string.ad_roles_resource),
                style = AdminType.columnHead,
                color = AdminTokens.textFaint,
                modifier = Modifier.width(240.dp),
            )
            roles.forEach { role ->
                Text(
                    role.name.uppercase(),
                    style = AdminType.columnHead,
                    color = AdminTokens.textFaint,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(96.dp),
                )
            }
        }
        Hairline(AdminTokens.railBorder)

        Permission.entries.forEach { permission ->
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.width(240.dp)) {
                    CellPrimary(permission.id)
                }
                roles.forEach { role ->
                    val granted = state.isGranted(role.slug, permission)
                    Cell(
                        granted = granted,
                        enabled = !state.busy,
                        onClick = { onEvent(RolesEvent.GrantToggled(role.slug, permission)) },
                    )
                }
            }
            Hairline()
        }
    }
}

/**
 * One cell. Filled white when granted, an outlined dash when not.
 *
 * The filled state is the loud one because a granted permission is the thing
 * worth noticing on a grid that is mostly empty.
 */
@Composable
private fun Cell(granted: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (granted) AdminTokens.text else AdminTokens.tableHeader)
            .border(
                1.dp,
                if (granted) AdminTokens.text else AdminTokens.border,
                RoundedCornerShape(6.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (granted) {
                stringResource(Res.string.ad_roles_granted)
            } else {
                stringResource(Res.string.ad_roles_denied)
            },
            style = AdminType.strong,
            color = if (granted) AdminTokens.canvas else AdminTokens.textDim,
        )
    }
}

/**
 * Adding a person.
 *
 * Two fields, and the address is the one that matters. The row is created before
 * the person has an account at all — `admin-session` refuses anybody not on this
 * list, and the first sign-in binds their account to the row — so the order is add
 * here, then tell them to sign in.
 *
 * No role is chosen here. A new row has none, and the chips on the list are where
 * that decision is made and re-made; a role picked in a creation dialog is a
 * decision taken once, in the place least likely to be revisited.
 */
@Composable
private fun NewStaffDialog(form: NewStaff, busy: Boolean, onEvent: (RolesEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(RolesEvent.NewStaffDismissed) },
        containerColor = AdminTokens.card,
        titleContentColor = AdminTokens.text,
        textContentColor = AdminTokens.textStrong,
        title = { Text(stringResource(Res.string.ad_staff_add_title), style = AdminType.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    FieldLabel(stringResource(Res.string.ad_staff_email).uppercase())
                    AdminField(
                        form.email,
                        { onEvent(RolesEvent.NewStaffEmailChanged(it)) },
                        stringResource(Res.string.ad_staff_email),
                        Modifier.fillMaxWidth(),
                    )
                }
                Column {
                    FieldLabel(stringResource(Res.string.ad_staff_name).uppercase())
                    AdminField(
                        form.name,
                        { onEvent(RolesEvent.NewStaffNameChanged(it)) },
                        stringResource(Res.string.ad_staff_name),
                        Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    stringResource(Res.string.ad_staff_add_hint),
                    style = AdminType.caption,
                    color = AdminTokens.textDim,
                )
            }
        },
        confirmButton = {
            PrimaryAction(
                stringResource(Res.string.ad_staff_add),
                { onEvent(RolesEvent.NewStaffSubmitted) },
                form.canSubmit && !busy,
            )
        },
        dismissButton = {
            RowAction(stringResource(Res.string.ad_cities_cancel), { onEvent(RolesEvent.NewStaffDismissed) })
        },
    )
}

/**
 * Adding a role.
 *
 * The new role starts with no permissions. They are granted in the grid above,
 * which is already the one place permissions are decided — offering a second place
 * in this dialog would mean two screens that can disagree about what a role is.
 */
@Composable
private fun NewRoleDialog(form: NewRole, busy: Boolean, onEvent: (RolesEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(RolesEvent.NewRoleDismissed) },
        containerColor = AdminTokens.card,
        titleContentColor = AdminTokens.text,
        textContentColor = AdminTokens.textStrong,
        title = { Text(stringResource(Res.string.ad_roles_new_title), style = AdminType.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    FieldLabel(stringResource(Res.string.ad_roles_slug).uppercase())
                    AdminField(
                        form.slug,
                        { onEvent(RolesEvent.NewRoleSlugChanged(it)) },
                        stringResource(Res.string.ad_roles_slug),
                        Modifier.fillMaxWidth(),
                    )
                    if (form.slugError) {
                        StatusText(
                            stringResource(Res.string.ad_roles_slug_error),
                            AdminTokens.danger,
                            Modifier.padding(top = 4.dp),
                        )
                    }
                }
                Column {
                    FieldLabel(stringResource(Res.string.ad_roles_name).uppercase())
                    AdminField(
                        form.name,
                        { onEvent(RolesEvent.NewRoleNameChanged(it)) },
                        stringResource(Res.string.ad_roles_name),
                        Modifier.fillMaxWidth(),
                    )
                }
                Column {
                    FieldLabel(stringResource(Res.string.ad_roles_description).uppercase())
                    AdminField(
                        form.description,
                        { onEvent(RolesEvent.NewRoleDescriptionChanged(it)) },
                        stringResource(Res.string.ad_roles_description),
                        Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    stringResource(Res.string.ad_roles_new_hint),
                    style = AdminType.caption,
                    color = AdminTokens.textDim,
                )
            }
        },
        confirmButton = {
            PrimaryAction(
                stringResource(Res.string.ad_roles_new),
                { onEvent(RolesEvent.NewRoleSubmitted) },
                form.canSubmit && !busy,
            )
        },
        dismissButton = {
            RowAction(stringResource(Res.string.ad_cities_cancel), { onEvent(RolesEvent.NewRoleDismissed) })
        },
    )
}

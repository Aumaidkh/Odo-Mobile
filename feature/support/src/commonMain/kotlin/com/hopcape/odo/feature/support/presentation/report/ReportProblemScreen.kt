package com.hopcape.odo.feature.support.presentation.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoChip
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoInputField
import com.hopcape.odo.core.designsystem.component.OdoListItem
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoSwitch
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcPlusLarge
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.support.presentation.SectionLabel
import com.hopcape.odo.feature.support.resources.Res
import com.hopcape.odo.feature.support.resources.sp_area_bill_scan
import com.hopcape.odo.feature.support.resources.sp_area_challan
import com.hopcape.odo.feature.support.resources.sp_area_other
import com.hopcape.odo.feature.support.resources.sp_area_payment
import com.hopcape.odo.feature.support.resources.sp_area_refuel
import com.hopcape.odo.feature.support.resources.sp_area_reminders
import com.hopcape.odo.feature.support.resources.sp_rp_add_screenshot
import com.hopcape.odo.feature.support.resources.sp_rp_attach_logs
import com.hopcape.odo.feature.support.resources.sp_rp_attach_logs_sub
import com.hopcape.odo.feature.support.resources.sp_rp_email_hint
import com.hopcape.odo.feature.support.resources.sp_rp_email_invalid
import com.hopcape.odo.feature.support.resources.sp_rp_email_label
import com.hopcape.odo.feature.support.resources.sp_rp_email_why
import com.hopcape.odo.feature.support.resources.sp_rp_hint
import com.hopcape.odo.feature.support.resources.sp_rp_remove_attachment
import com.hopcape.odo.feature.support.resources.sp_rp_reply_to
import com.hopcape.odo.feature.support.resources.sp_rp_screenshot_note
import com.hopcape.odo.feature.support.resources.sp_rp_send
import com.hopcape.odo.feature.support.resources.sp_rp_title
import com.hopcape.odo.feature.support.resources.sp_rp_what
import com.hopcape.odo.feature.support.resources.sp_rp_where
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A bug report that becomes a ticket rather than a mail draft.
 *
 * **The area is asked first.** It is what routes the ticket, and it is also the cheapest thing
 * on the screen to answer — a row of chips answers itself, where an empty box is the point most
 * people give up at.
 *
 * The reply address is stated at the bottom rather than asked for, when the account has one.
 * When it does not, the same space becomes the field that asks: a report nobody can answer is
 * the owner's time spent twice.
 */
@Composable
internal fun ReportProblemScreen(
    state: ReportUiState,
    onEvent: (ReportEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.sp_rp_title),
        onBack = { onEvent(ReportEvent.BackClicked) },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(OdoTheme.spacing.screenEdge),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OdoButton(
                    text = stringResource(Res.string.sp_rp_send),
                    onClick = { onEvent(ReportEvent.SendClicked) },
                    enabled = state.canSend,
                    loading = state.sending,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!state.asksForEmail) {
                    OdoText(
                        text = stringResource(Res.string.sp_rp_reply_to, state.maskedEmail),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.textMuted,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            SectionLabel(stringResource(Res.string.sp_rp_where))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            ) {
                ReportArea.entries.forEach { area ->
                    OdoChip(
                        label = area.label(),
                        selected = state.area == area,
                        onClick = { onEvent(ReportEvent.AreaPicked(area)) },
                    )
                }
            }

            SectionLabel(stringResource(Res.string.sp_rp_what))
            OdoInputField(
                value = state.message,
                onValueChange = { onEvent(ReportEvent.MessageChanged(it)) },
                placeholder = stringResource(Res.string.sp_rp_hint),
                singleLine = false,
                // Tall enough to look like somewhere to write a paragraph. A single-line box
                // invites a single line, and one line is rarely enough to act on.
                modifier = Modifier.fillMaxWidth().heightIn(min = MESSAGE_MIN_HEIGHT),
            )

            OdoText(
                text = stringResource(Res.string.sp_rp_screenshot_note),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textMuted,
            )
            Attachments(state = state, onEvent = onEvent)

            OdoCard(contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.Top) {
                OdoListItem(
                    headline = stringResource(Res.string.sp_rp_attach_logs),
                    supporting = stringResource(Res.string.sp_rp_attach_logs_sub),
                    onClick = { onEvent(ReportEvent.AttachLogsToggled(!state.attachLogs)) },
                    modifier = Modifier.padding(horizontal = OdoTheme.spacing.cardPadding),
                    trailing = {
                        OdoSwitch(
                            checked = state.attachLogs,
                            onCheckedChange = { onEvent(ReportEvent.AttachLogsToggled(it)) },
                        )
                    },
                )
            }

            if (state.asksForEmail) {
                OdoInputField(
                    value = state.email,
                    onValueChange = { onEvent(ReportEvent.EmailChanged(it)) },
                    label = stringResource(Res.string.sp_rp_email_label),
                    placeholder = stringResource(Res.string.sp_rp_email_hint),
                    helperText = stringResource(Res.string.sp_rp_email_why),
                    errorText = stringResource(Res.string.sp_rp_email_invalid)
                        .takeIf { state.emailInvalid },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * The add tile, then what has been added.
 *
 * Add first and always in the same place: a grid whose button moves as files are added is one
 * the owner has to re-find every time.
 */
@Composable
private fun Attachments(state: ReportUiState, onEvent: (ReportEvent) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
    ) {
        OdoCard(
            onClick = { onEvent(ReportEvent.AddAttachmentClicked) },
            modifier = Modifier.size(TILE_SIZE),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                OdoIcon(
                    imageVector = IcPlusLarge,
                    contentDescription = stringResource(Res.string.sp_rp_add_screenshot),
                )
            }
        }
        state.attachments.forEach { attachment ->
            val removeLabel = stringResource(Res.string.sp_rp_remove_attachment, attachment.name)
            OdoCard(
                // Tapping removes it. There is one thing to do with an attachment already
                // added, and a separate cross on a 96dp tile is a smaller target than the
                // tile itself.
                onClick = { onEvent(ReportEvent.AttachmentRemoved(attachment.ref)) },
                modifier = Modifier
                    .size(TILE_SIZE)
                    // What tapping does, said once. The tile draws the file name; a screen
                    // reader needs the action, which the drawing does not carry.
                    .semantics {
                        contentDescription = removeLabel
                    },
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
                    OdoText(
                        text = attachment.name,
                        style = OdoTheme.typography.caption,
                        color = OdoTheme.colors.textDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(OdoTheme.spacing.sm),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportArea.label(): String = stringResource(labelResource())

/**
 * The area's wording, shared with the confirmation screen.
 *
 * Here rather than beside the enum: the enum is what a ticket carries, and the words are what
 * a screen shows. One list of them, so the chip and the confirmation cannot say two things
 * about the same report.
 */
internal fun ReportArea.labelResource(): StringResource = when (this) {
    ReportArea.BILL_SCAN -> Res.string.sp_area_bill_scan
    ReportArea.REMINDERS -> Res.string.sp_area_reminders
    ReportArea.CHALLAN -> Res.string.sp_area_challan
    ReportArea.REFUEL -> Res.string.sp_area_refuel
    ReportArea.PAYMENT -> Res.string.sp_area_payment
    ReportArea.OTHER -> Res.string.sp_area_other
}

private val MESSAGE_MIN_HEIGHT = 132.dp
private val TILE_SIZE = 96.dp

@OdoThemePreviews
@Composable
private fun ReportProblemPreview() = OdoPreview(padded = false) {
    ReportProblemScreen(
        state = ReportUiState(
            message = "The labour charge came out as Rs. 450 but the bill says Rs. 4,500.",
            attachments = listOf(ReportAttachment(ref = "1", name = "bill.jpg")),
            maskedEmail = "r•••@gmail.com",
        ),
        onEvent = {},
    )
}

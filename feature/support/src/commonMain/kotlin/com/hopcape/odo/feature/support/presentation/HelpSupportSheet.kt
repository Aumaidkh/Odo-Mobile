package com.hopcape.odo.feature.support.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoChip
import com.hopcape.odo.core.designsystem.component.OdoCircularIconButton
import com.hopcape.odo.core.designsystem.component.OdoCircularIconButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcChatOutlined
import com.hopcape.odo.core.designsystem.icons.IcClose
import com.hopcape.odo.core.designsystem.icons.IcEnvelope
import com.hopcape.odo.core.designsystem.icons.IcFileFilled
import com.hopcape.odo.core.designsystem.icons.IcLightbulbFilled
import com.hopcape.odo.core.designsystem.icons.IcMagnifier
import com.hopcape.odo.core.designsystem.icons.IcPencil
import com.hopcape.odo.core.designsystem.icons.IcStarFilled
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.support.resources.Res
import com.hopcape.odo.feature.support.resources.sp_chat
import com.hopcape.odo.feature.support.resources.sp_chat_hours
import com.hopcape.odo.feature.support.resources.sp_chat_online
import com.hopcape.odo.feature.support.resources.sp_close
import com.hopcape.odo.feature.support.resources.sp_email
import com.hopcape.odo.feature.support.resources.sp_email_address
import com.hopcape.odo.feature.support.resources.sp_faqs
import com.hopcape.odo.feature.support.resources.sp_flag
import com.hopcape.odo.feature.support.resources.sp_flag_sub
import com.hopcape.odo.feature.support.resources.sp_idea
import com.hopcape.odo.feature.support.resources.sp_idea_sub
import com.hopcape.odo.feature.support.resources.sp_licences
import com.hopcape.odo.feature.support.resources.sp_privacy
import com.hopcape.odo.feature.support.resources.sp_rate
import com.hopcape.odo.feature.support.resources.sp_rate_sub
import com.hopcape.odo.feature.support.resources.sp_report
import com.hopcape.odo.feature.support.resources.sp_report_sub
import com.hopcape.odo.feature.support.resources.sp_search
import com.hopcape.odo.feature.support.resources.sp_section_contact
import com.hopcape.odo.feature.support.resources.sp_section_feedback
import com.hopcape.odo.feature.support.resources.sp_section_resources
import com.hopcape.odo.feature.support.resources.sp_subtitle
import com.hopcape.odo.feature.support.resources.sp_terms
import com.hopcape.odo.feature.support.resources.sp_tickets
import com.hopcape.odo.feature.support.resources.sp_tickets_sub
import com.hopcape.odo.feature.support.resources.sp_title
import com.hopcape.odo.feature.support.resources.sp_version
import org.jetbrains.compose.resources.stringResource

/**
 * The Help & support hub ([com.hopcape.odo.core.navigation.OdoDestination.Support.Help]),
 * shown as a bottom-sheet destination from Profile's "Help & support" row.
 *
 * Every row is a navigation affordance and nothing else — this sheet holds no state and
 * makes no decision; each callback pushes the row's own destination. The sheet chrome
 * (drag handle, scrim, swipe-to-dismiss) comes from the navigation layer.
 *
 * The chat availability, the open-ticket count and the version line are **sample values**
 * until the support backend and `BuildConfig` are wired: they are rendered so the layout
 * is real, not because the data is.
 */
@Composable
internal fun HelpSupportSheetContent(
    onClose: () -> Unit,
    onSearch: () -> Unit,
    onChat: () -> Unit,
    onEmail: () -> Unit,
    onTickets: () -> Unit,
    onReportProblem: () -> Unit,
    onSuggestIdea: () -> Unit,
    onFlagPriceData: () -> Unit,
    onRate: () -> Unit,
    onFaqs: () -> Unit,
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
    onLicences: () -> Unit,
    onSendDiagnostics: () -> Unit,
) {
    SupportSheet {
        Header(onClose = onClose)
        SearchBox(onClick = onSearch)

        SectionLabel(stringResource(Res.string.sp_section_contact))
        SupportGroup {
            SupportRow(
                icon = IcChatOutlined,
                title = stringResource(Res.string.sp_chat),
                subtitle = stringResource(Res.string.sp_chat_hours),
                onClick = onChat,
                // Sample availability — the real state arrives with the support backend.
                trailing = { OdoBadge(stringResource(Res.string.sp_chat_online), tone = OdoBadgeTone.Success) },
            )
            OdoDivider()
            SupportRow(
                icon = IcEnvelope,
                title = stringResource(Res.string.sp_email),
                subtitle = stringResource(Res.string.sp_email_address),
                onClick = onEmail,
            )
            OdoDivider()
            SupportRow(
                icon = IcFileFilled,
                title = stringResource(Res.string.sp_tickets),
                subtitle = stringResource(Res.string.sp_tickets_sub),
                onClick = onTickets,
                // Sample open-ticket count, same as the subtitle above it.
                trailing = { OdoBadge(SAMPLE_OPEN_TICKETS, tone = OdoBadgeTone.Warning) },
            )
        }

        SectionLabel(stringResource(Res.string.sp_section_feedback))
        SupportGroup {
            SupportRow(
                icon = IcWarning,
                title = stringResource(Res.string.sp_report),
                subtitle = stringResource(Res.string.sp_report_sub),
                onClick = onReportProblem,
                iconTint = OdoTheme.colors.danger,
            )
            OdoDivider()
            SupportRow(
                icon = IcLightbulbFilled,
                title = stringResource(Res.string.sp_idea),
                subtitle = stringResource(Res.string.sp_idea_sub),
                onClick = onSuggestIdea,
                iconTint = OdoTheme.colors.textDim,
            )
            OdoDivider()
            SupportRow(
                icon = IcPencil,
                title = stringResource(Res.string.sp_flag),
                subtitle = stringResource(Res.string.sp_flag_sub),
                onClick = onFlagPriceData,
                iconTint = OdoTheme.colors.textDim,
            )
            OdoDivider()
            SupportRow(
                icon = IcStarFilled,
                title = stringResource(Res.string.sp_rate),
                subtitle = stringResource(Res.string.sp_rate_sub),
                onClick = onRate,
                iconTint = OdoTheme.colors.warning,
            )
        }

        SectionLabel(stringResource(Res.string.sp_section_resources))
        // Wraps rather than scrolls: four short chips fit one line on most phones and
        // fall to a second on the narrowest, instead of hiding "Licences" off-screen.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        ) {
            OdoChip(stringResource(Res.string.sp_faqs), onClick = onFaqs)
            OdoChip(stringResource(Res.string.sp_terms), onClick = onTerms)
            OdoChip(stringResource(Res.string.sp_privacy), onClick = onPrivacy)
            OdoChip(stringResource(Res.string.sp_licences), onClick = onLicences)
        }

        VersionFooter(onClick = onSendDiagnostics)
    }
}

/** Title + reply-time promise, with the circular close that pops the sheet. */
@Composable
private fun Header(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            OdoText(stringResource(Res.string.sp_title), style = OdoTheme.typography.title)
            OdoText(
                stringResource(Res.string.sp_subtitle),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }
        OdoCircularIconButton(
            IcClose,
            contentDescription = stringResource(Res.string.sp_close),
            onClick = onClose,
            variant = OdoCircularIconButtonVariant.Raised,
        )
    }
}

/**
 * The search entry point. A tappable card rather than a live text field: typing happens on
 * the search screen, so the hub never owns a query it would have to hand over.
 */
@Composable
private fun SearchBox(onClick: () -> Unit) {
    OdoCard(onClick = onClick, color = OdoTheme.colors.surfaceRaised) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoIcon(
                IcMagnifier,
                contentDescription = null,
                tint = OdoTheme.colors.textDim,
                size = OdoTheme.iconSizes.medium,
            )
            OdoText(
                stringResource(Res.string.sp_search),
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.textDim,
                maxLines = 1,
            )
        }
    }
}

/** Version + build line; tapping it sends the current session's logs for a support ticket. */
@Composable
private fun VersionFooter(onClick: () -> Unit) {
    OdoText(
        // Sample build stamp — replaced by the real version once it is passed in.
        stringResource(Res.string.sp_version, SAMPLE_VERSION),
        style = OdoTheme.typography.caption,
        color = OdoTheme.colors.textMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = OdoTheme.spacing.sm),
    )
}

private const val SAMPLE_VERSION = "v1.4.0 (build 412)"
private const val SAMPLE_OPEN_TICKETS = "1"

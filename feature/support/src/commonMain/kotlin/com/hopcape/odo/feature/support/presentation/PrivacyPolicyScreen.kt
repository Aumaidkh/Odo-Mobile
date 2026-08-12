package com.hopcape.odo.feature.support.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoIconButton
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcClock
import com.hopcape.odo.core.designsystem.icons.IcLink
import com.hopcape.odo.core.designsystem.icons.IcShare
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.support.resources.Res
import com.hopcape.odo.feature.support.resources.sp_pp_cd_opens_browser
import com.hopcape.odo.feature.support.resources.sp_pp_cd_share
import com.hopcape.odo.feature.support.resources.sp_pp_collect
import com.hopcape.odo.feature.support.resources.sp_pp_collect_body
import com.hopcape.odo.feature.support.resources.sp_pp_lede
import com.hopcape.odo.feature.support.resources.sp_pp_read_full
import com.hopcape.odo.feature.support.resources.sp_pp_revision
import com.hopcape.odo.feature.support.resources.sp_pp_share
import com.hopcape.odo.feature.support.resources.sp_pp_share_body
import com.hopcape.odo.feature.support.resources.sp_pp_short
import com.hopcape.odo.feature.support.resources.sp_pp_terms
import com.hopcape.odo.feature.support.resources.sp_pp_title
import com.hopcape.odo.feature.support.resources.sp_pp_why
import com.hopcape.odo.feature.support.resources.sp_pp_why_body
import org.jetbrains.compose.resources.stringResource

/**
 * The privacy policy, summarised in the app and linked in full.
 *
 * Native rather than a web view of the hosted page, for three reasons that all point the
 * same way: it renders with no network, in the app's own theme, at whatever text size the
 * owner has set. Someone opening a privacy notice is often doing it because they are
 * deciding whether to stay, and that is the worst possible moment for a blank screen.
 *
 * The summary is not the policy and does not pretend to be — the revision line says what it
 * was written against, and the first row out goes to the authoritative document.
 *
 * [onOpenPrivacy] / [onOpenTerms] are null when this build has no backend configured, and
 * their rows are then left out rather than shown dead.
 */
@Composable
internal fun PrivacyPolicyScreen(
    onBack: () -> Unit,
    onShare: (() -> Unit)?,
    onOpenPrivacy: (() -> Unit)?,
    onOpenTerms: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.sp_pp_title),
        onBack = onBack,
        actions = {
            if (onShare != null) {
                OdoIconButton(
                    IcShare,
                    contentDescription = stringResource(Res.string.sp_pp_cd_share),
                    onClick = onShare,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            RevisionPill()

            OdoCard {
                OdoText(stringResource(Res.string.sp_pp_short), style = OdoTheme.typography.title)
                OdoText(
                    stringResource(Res.string.sp_pp_lede),
                    style = OdoTheme.typography.body,
                    color = OdoTheme.colors.textDim,
                )
                Section(stringResource(Res.string.sp_pp_collect), stringResource(Res.string.sp_pp_collect_body))
                Section(stringResource(Res.string.sp_pp_why), stringResource(Res.string.sp_pp_why_body))
                Section(stringResource(Res.string.sp_pp_share), stringResource(Res.string.sp_pp_share_body))
            }

            if (onOpenPrivacy != null || onOpenTerms != null) {
                Spacer(Modifier.heightIn(min = OdoTheme.spacing.sm))
                SupportGroup {
                    onOpenPrivacy?.let {
                        ExternalRow(stringResource(Res.string.sp_pp_read_full), it)
                    }
                    onOpenTerms?.let {
                        ExternalRow(stringResource(Res.string.sp_pp_terms), it)
                    }
                }
            }
        }
    }
}

/**
 * When the documents last changed, and which summary this is.
 *
 * Worth the space at the top: the first thing anyone checks about a policy is whether it is
 * the one they already read.
 */
@Composable
private fun RevisionPill() {
    OdoCard(color = OdoTheme.colors.surfaceRaised) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoIcon(
                IcClock,
                contentDescription = null,
                tint = OdoTheme.colors.textMuted,
                size = OdoTheme.iconSizes.small,
            )
            OdoText(
                stringResource(
                    Res.string.sp_pp_revision,
                    PolicyRevision.LAST_UPDATED,
                    PolicyRevision.SUMMARY_VERSION,
                ),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }
    }
}

/** One headed paragraph of the summary. */
@Composable
private fun Section(heading: String, body: String) {
    Column(
        modifier = Modifier.padding(top = OdoTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OdoText(heading, style = OdoTheme.typography.caption, color = OdoTheme.colors.accent)
        OdoText(body, style = OdoTheme.typography.body, color = OdoTheme.colors.textDim)
    }
}

/**
 * A row that leaves the app.
 *
 * The link glyph rather than a chevron, and a content description that says so: a chevron
 * promises another screen of Odo's, and what is actually on the other side is a browser.
 */
@Composable
private fun ExternalRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = OdoTheme.spacing.minTouchTarget)
            .padding(vertical = OdoTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoText(title, style = OdoTheme.typography.heading, modifier = Modifier.weight(1f))
        OdoIcon(
            IcLink,
            contentDescription = stringResource(Res.string.sp_pp_cd_opens_browser),
            tint = OdoTheme.colors.textDim,
            size = OdoTheme.iconSizes.small,
        )
    }
}

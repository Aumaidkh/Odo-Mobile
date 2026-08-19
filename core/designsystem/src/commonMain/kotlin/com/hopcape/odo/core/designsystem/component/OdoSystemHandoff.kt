package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcChevronLeft
import com.hopcape.odo.core.designsystem.icons.IcInfo
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * One row of the mocked-up system screen — an app, and whether its switch is on.
 *
 * @param initial the letter drawn in place of the app's icon. A real icon cannot be used:
 *   these are other people's apps and the drawing is an illustration, not a listing.
 * @param highlighted whether this is Odo's own row. Exactly one row should set it — the whole
 *   picture exists to say "this is the line you are looking for".
 */
@Immutable
data class OdoSystemToggleRow(
    val initial: String,
    val label: String,
    val on: Boolean,
    val highlighted: Boolean = false,
)

/**
 * The screen shown just before handing the owner to a system settings page.
 *
 * Some permissions have no dialog. They have a page in Settings that the owner has to walk to,
 * find the app on, and switch on themselves — and the app controls nothing about it. Two things
 * go wrong there. The page may carry a warning written for the whole permission class rather
 * than for this app, which reads as an accusation. And the owner may simply not find the row,
 * because every manufacturer lays the page out differently and calls it something else.
 *
 * This screen answers both before the handoff: it says in the app's own words what the next
 * screen will claim, and it draws the row to look for. A picture of the destination is worth
 * more than instructions about it, because the owner is matching shapes by the time they get
 * there, not reading.
 *
 * It is deliberately not a dialog. The owner leaves the app on the next tap and comes back
 * later, and a dialog that vanished while they were away would leave them with no way to
 * re-read what they were told.
 *
 * @param eyebrow the small line above the title, e.g. "BEFORE YOU TAP". Pass it already cased.
 * @param instruction the one thing to do on the system page, stated as a single sentence.
 * @param previewRows the mocked-up list. One of them should be [OdoSystemToggleRow.highlighted].
 * @param previewNote the caption under the picture — where to say that the page differs by phone.
 */
@Composable
fun OdoSystemHandoff(
    screenTitle: String,
    onBack: () -> Unit,
    backContentDescription: String?,
    eyebrow: String,
    title: String,
    body: String,
    instruction: String,
    previewLabel: String,
    previewHeader: String,
    previewRows: List<OdoSystemToggleRow>,
    previewNote: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = IcInfo,
) {
    OdoScreen(
        modifier = modifier,
        title = screenTitle,
        onBack = onBack,
        backContentDescription = backContentDescription,
        bottomBar = { HandoffButtons(confirmLabel, onConfirm, dismissLabel, onDismiss) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
                // Tinted rather than the usual raised grey. This screen is a caution about
                // what comes next, and the tile is the first thing that says so.
                Box(
                    modifier = Modifier
                        .size(HERO_TILE)
                        .clip(OdoTheme.shapes.field)
                        .background(OdoTheme.colors.warning.copy(alpha = TINT_ALPHA)),
                    contentAlignment = Alignment.Center,
                ) {
                    OdoIcon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = OdoTheme.colors.warning,
                        size = OdoTheme.iconSizes.large,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                    OdoText(
                        text = eyebrow,
                        style = OdoTheme.typography.caption,
                        color = OdoTheme.colors.warning,
                    )
                    OdoText(text = title, style = OdoTheme.typography.title)
                    OdoText(
                        text = body,
                        style = OdoTheme.typography.body,
                        color = OdoTheme.colors.textDim,
                    )
                }
            }

            // The single action, pulled out of the body so it survives being skim-read.
            OdoCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OdoIcon(
                        imageVector = IcCheck,
                        contentDescription = null,
                        tint = OdoTheme.colors.success,
                        size = OdoTheme.iconSizes.small,
                    )
                    OdoText(text = instruction, style = OdoTheme.typography.bodySmall)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                OdoText(
                    text = previewLabel,
                    style = OdoTheme.typography.caption,
                    color = OdoTheme.colors.textMuted,
                )
                SystemScreenPicture(header = previewHeader, rows = previewRows)
                OdoText(
                    text = previewNote,
                    style = OdoTheme.typography.caption,
                    color = OdoTheme.colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun HandoffButtons(
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = OdoTheme.spacing.screenEdge,
                vertical = OdoTheme.spacing.lg,
            ),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
    ) {
        OdoButton(text = confirmLabel, onClick = onConfirm, modifier = Modifier.fillMaxWidth())
        OdoButton(
            text = dismissLabel,
            onClick = onDismiss,
            variant = OdoButtonVariant.Tertiary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The drawing of the system page.
 *
 * A drawing rather than a screenshot on purpose. A screenshot is one manufacturer's build at
 * one version and goes stale silently; this follows the app's own theme, scales with the
 * owner's text size, and stays roughly true on every skin because it only claims what all of
 * them share — a list of apps, each with a switch.
 */
@Composable
private fun SystemScreenPicture(header: String, rows: List<OdoSystemToggleRow>) {
    OdoCard(
        modifier = Modifier.fillMaxWidth(),
        color = OdoTheme.colors.bg,
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OdoTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoIcon(
                imageVector = IcChevronLeft,
                contentDescription = null,
                tint = OdoTheme.colors.textDim,
                size = OdoTheme.iconSizes.small,
            )
            OdoText(text = header, style = OdoTheme.typography.label)
        }
        rows.forEach { row ->
            OdoDivider()
            PictureRow(row)
        }
    }
}

@Composable
private fun PictureRow(row: OdoSystemToggleRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (row.highlighted) {
                    OdoTheme.colors.accent.copy(alpha = TINT_ALPHA)
                } else {
                    OdoTheme.colors.bg
                },
            )
            .padding(OdoTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(APP_TILE)
                .clip(OdoTheme.shapes.small)
                .background(
                    if (row.highlighted) {
                        OdoTheme.colors.accent
                    } else {
                        OdoTheme.colors.surfaceRaised
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            OdoText(
                text = row.initial,
                style = OdoTheme.typography.label,
                color = if (row.highlighted) {
                    OdoTheme.colors.onAccent
                } else {
                    OdoTheme.colors.textMuted
                },
            )
        }
        OdoText(
            text = row.label,
            style = OdoTheme.typography.bodySmall,
            color = if (row.highlighted) OdoTheme.colors.text else OdoTheme.colors.textDim,
            modifier = Modifier.weight(1f),
        )
        FakeSwitch(on = row.on)
    }
}

/**
 * A switch that cannot be tapped.
 *
 * [OdoSwitch] would be wrong here twice over: it is a control, so it would offer a tap that
 * does nothing, and it would announce itself to a screen reader as something toggleable on a
 * screen where nothing is. This is a shape, and the row's text carries the meaning.
 */
@Composable
private fun FakeSwitch(on: Boolean) {
    Box(
        modifier = Modifier
            .size(width = SWITCH_WIDTH, height = SWITCH_HEIGHT)
            .clip(OdoTheme.shapes.pill)
            .background(if (on) OdoTheme.colors.text else OdoTheme.colors.surfaceRaised),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = SWITCH_INSET)
                .size(SWITCH_KNOB)
                .clip(OdoTheme.shapes.pill)
                .background(if (on) OdoTheme.colors.bg else OdoTheme.colors.textMuted),
        )
    }
}

private val HERO_TILE = 72.dp
private val APP_TILE = 32.dp
private val SWITCH_WIDTH = 40.dp
private val SWITCH_HEIGHT = 24.dp
private val SWITCH_KNOB = 16.dp
private val SWITCH_INSET = 4.dp
private const val TINT_ALPHA = 0.14f

@OdoThemePreviews
@Composable
private fun OdoSystemHandoffPreview() = OdoPreview(padded = false) {
    OdoSystemHandoff(
        screenTitle = "Notification access",
        onBack = {},
        backContentDescription = "Back",
        eyebrow = "BEFORE YOU TAP",
        title = "The next screen is your phone's",
        body = "Android has a single switch for notification access, so its warning lists " +
            "everything any app could do with it. That text is not describing Odo.",
        instruction = "Find Odo in the list, turn it on, then confirm.",
        previewLabel = "WHAT YOU'LL SEE",
        previewHeader = "Notification access",
        previewRows = listOf(
            OdoSystemToggleRow(initial = "W", label = "WhatsApp", on = true),
            OdoSystemToggleRow(initial = "O", label = "Odo", on = false, highlighted = true),
            OdoSystemToggleRow(initial = "S", label = "Swiggy", on = false),
        ),
        previewNote = "Find Odo in the list and turn its switch on.",
        confirmLabel = "Open notification access",
        onConfirm = {},
        dismissLabel = "Not now",
        onDismiss = {},
    )
}

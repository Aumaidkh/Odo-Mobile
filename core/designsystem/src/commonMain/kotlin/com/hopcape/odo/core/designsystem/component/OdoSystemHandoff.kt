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
import androidx.compose.foundation.shape.CircleShape
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
 * What kind of control a drawn system row carries.
 *
 * The two shapes Android uses for the two shapes of question. A list of apps that are each
 * independently on or off gets switches; a single choice between mutually exclusive options —
 * how much location an app may have, say — gets radios. Drawing the wrong one makes the picture
 * useless, because the owner is matching it against the real screen.
 */
enum class OdoSystemRowControl { Switch, Radio }

/**
 * One row of the mocked-up system screen.
 *
 * @param label the row's text, exactly as the system screen words it.
 * @param on whether the switch reads on, or the radio is the selected one.
 * @param initial the letter drawn in place of an app's icon, on rows that stand for an app. A
 *   real icon cannot be used: these are other people's apps and this is an illustration, not a
 *   listing. Null on a row that is an option rather than an app.
 * @param highlighted the row the owner is being sent to find. At most one row should set it —
 *   the whole picture exists to say "this is the line you are looking for".
 */
@Immutable
data class OdoSystemRow(
    val label: String,
    val on: Boolean,
    val initial: String? = null,
    val control: OdoSystemRowControl = OdoSystemRowControl.Switch,
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
 * @param instruction the one thing to do on the system page, stated as a single sentence. Null
 *   when the drawing says it on its own and a card would only repeat it.
 * @param previewRows the mocked-up list. One of them should be [OdoSystemRow.highlighted].
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
    instruction: String?,
    previewLabel: String,
    previewHeader: String,
    previewRows: List<OdoSystemRow>,
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
            if (instruction != null) {
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
private fun SystemScreenPicture(header: String, rows: List<OdoSystemRow>) {
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
private fun PictureRow(row: OdoSystemRow) {
    val radio = row.control == OdoSystemRowControl.Radio
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
        // A radio sits at the head of its row on the real screen, where an app's icon would be
        // on a switch list. Drawing it anywhere else would stop the picture matching.
        if (radio) FakeRadio(on = row.on)
        if (row.initial != null) AppTile(initial = row.initial, highlighted = row.highlighted)
        OdoText(
            text = row.label,
            style = OdoTheme.typography.bodySmall,
            color = if (row.highlighted) OdoTheme.colors.text else OdoTheme.colors.textDim,
            modifier = Modifier.weight(1f),
        )
        if (!radio) FakeSwitch(on = row.on)
    }
}

@Composable
private fun AppTile(initial: String, highlighted: Boolean) {
    Box(
        modifier = Modifier
            .size(APP_TILE)
            .clip(OdoTheme.shapes.small)
            .background(
                if (highlighted) OdoTheme.colors.accent else OdoTheme.colors.surfaceRaised,
            ),
        contentAlignment = Alignment.Center,
    ) {
        OdoText(
            text = initial,
            style = OdoTheme.typography.label,
            color = if (highlighted) OdoTheme.colors.onAccent else OdoTheme.colors.textMuted,
        )
    }
}

/**
 * A radio that cannot be tapped, for the same reason [FakeSwitch] cannot.
 *
 * Drawn as a ring with a filled centre rather than reusing [OdoRadioButton], which is a control:
 * it would offer a tap that does nothing and announce itself as selectable on a screen where
 * nothing is.
 */
@Composable
private fun FakeRadio(on: Boolean) {
    Box(
        modifier = Modifier
            .size(RADIO_SIZE)
            .clip(CircleShape)
            .background(if (on) OdoTheme.colors.text else OdoTheme.colors.surfaceRaised)
            .padding(RADIO_RING),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(OdoTheme.colors.bg),
            contentAlignment = Alignment.Center,
        ) {
            if (on) {
                Box(
                    modifier = Modifier
                        .size(RADIO_DOT)
                        .clip(CircleShape)
                        .background(OdoTheme.colors.text),
                )
            }
        }
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
private val RADIO_SIZE = 22.dp
private val RADIO_RING = 2.dp
private val RADIO_DOT = 10.dp
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
            OdoSystemRow(label = "WhatsApp", on = true, initial = "W"),
            OdoSystemRow(label = "Odo", on = false, initial = "O", highlighted = true),
            OdoSystemRow(label = "Swiggy", on = false, initial = "S"),
        ),
        previewNote = "Find Odo in the list and turn its switch on.",
        confirmLabel = "Open notification access",
        onConfirm = {},
        dismissLabel = "Not now",
        onDismiss = {},
    )
}

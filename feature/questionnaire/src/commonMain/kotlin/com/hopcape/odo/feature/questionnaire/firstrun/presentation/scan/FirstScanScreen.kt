package com.hopcape.odo.feature.questionnaire.firstrun.presentation.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCamera
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.OnboardingEvent
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.components.OnboardingStepScaffold
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.components.StepHeadline
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.OnboardingStep
import com.hopcape.odo.feature.questionnaire.resources.Res
import com.hopcape.odo.feature.questionnaire.resources.onb_scan_cta
import com.hopcape.odo.feature.questionnaire.resources.onb_scan_later
import com.hopcape.odo.feature.questionnaire.resources.onb_scan_permission
import com.hopcape.odo.feature.questionnaire.resources.onb_scan_subtitle
import com.hopcape.odo.feature.questionnaire.resources.onb_scan_title
import com.hopcape.odo.feature.questionnaire.resources.onb_skip
import org.jetbrains.compose.resources.stringResource

/**
 * Step 4 — the last screen of setup is the app's first real moment: scan one old bill and
 * find out whether the last garage visit was fair. It is skippable three ways (Skip, "I'll
 * do this later", and simply going Home) because a paywall-feeling first-run is worse than
 * a delayed first scan.
 *
 * Camera permission is *named* before it is asked (the note under the frame), so the system
 * dialog arrives explained rather than out of nowhere.
 *
 * Stateless: forwards [OnboardingEvent]s; the capture flow belongs to `:feature:billscanner`.
 */
@Composable
internal fun FirstScanScreen(
    onEvent: (OnboardingEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingStepScaffold(
        step = OnboardingStep.FIRST_SCAN.position,
        primaryLabel = stringResource(Res.string.onb_scan_cta),
        onPrimary = { onEvent(OnboardingEvent.Scan.ScanClicked) },
        modifier = modifier,
        primaryLeadingIcon = {
            OdoIcon(
                IcCamera,
                contentDescription = null,
                size = OdoTheme.iconSizes.medium,
            )
        },
        skipLabel = stringResource(Res.string.onb_skip),
        onSkip = { onEvent(OnboardingEvent.Scan.SkipClicked) },
        footer = {
            OdoText(
                text = stringResource(Res.string.onb_scan_later),
                style = OdoTheme.typography.label,
                color = OdoTheme.colors.textDim,
                modifier = Modifier
                    .clip(OdoTheme.shapes.pill)
                    .clickable(role = Role.Button) { onEvent(OnboardingEvent.Scan.SkipClicked) }
                    .padding(horizontal = OdoTheme.spacing.md, vertical = OdoTheme.spacing.sm),
            )
        },
    ) {
        StepHeadline(
            title = stringResource(Res.string.onb_scan_title),
            subtitle = stringResource(Res.string.onb_scan_subtitle),
            centered = true,
        )
        ScanFrame()
        PermissionNote()
    }
}

/**
 * The viewfinder, drawn rather than photographed: accent corner brackets around a striped
 * plate with a receipt in the middle. It promises what the next tap does — frame a bill —
 * without shipping a screenshot that would age with the camera UI.
 */
@Composable
private fun ScanFrame() {
    val colors = OdoTheme.colors
    val stripe = colors.text.copy(alpha = 0.03f)
    val plate = colors.surface
    val bracket = colors.accent
    val glyph = colors.textMuted

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(FrameHeight)
            .padding(horizontal = OdoTheme.spacing.xxl),
    ) {
        val radius = CornerRadius(FrameCorner.toPx(), FrameCorner.toPx())
        val rounded = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, radius))
        }

        drawRoundRect(color = plate, size = size, cornerRadius = radius)
        // Diagonal hatch, clipped to the plate — texture, not pattern; it should read as
        // "surface" at a glance and disappear on second look.
        clipPath(rounded) {
            val step = StripeGap.toPx()
            var x = -size.height
            while (x < size.width + size.height) {
                drawLine(
                    color = stripe,
                    start = Offset(x, size.height),
                    end = Offset(x + size.height, 0f),
                    strokeWidth = StripeWidth.toPx(),
                )
                x += step
            }
        }

        val inset = BracketInset.toPx()
        val len = BracketLength.toPx()
        val corner = BracketCorner.toPx()
        val stroke = BracketStroke.toPx()
        cornerBracket(inset, inset, 1f, 1f, len, corner, bracket, stroke)
        cornerBracket(size.width - inset, inset, -1f, 1f, len, corner, bracket, stroke)
        cornerBracket(inset, size.height - inset, 1f, -1f, len, corner, bracket, stroke)
        cornerBracket(size.width - inset, size.height - inset, -1f, -1f, len, corner, bracket, stroke)

        receipt(center = Offset(size.width / 2f, size.height / 2f), color = glyph)
    }
}

/**
 * One L-shaped corner mark. [dx]/[dy] are ±1 and point the arms inward, so all four
 * corners come from the same construction instead of four hand-placed paths.
 */
private fun DrawScope.cornerBracket(
    x: Float,
    y: Float,
    dx: Float,
    dy: Float,
    length: Float,
    corner: Float,
    color: Color,
    stroke: Float,
) {
    val path = Path().apply {
        moveTo(x, y + dy * length)
        lineTo(x, y + dy * corner)
        quadraticTo(x, y, x + dx * corner, y)
        lineTo(x + dx * length, y)
    }
    drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
}

/** A bill: a torn-edged slip with two lines of print. Outline only — it is a hint, not art. */
private fun DrawScope.receipt(center: Offset, color: Color) {
    val w = ReceiptWidth.toPx()
    val h = ReceiptHeight.toPx()
    val left = center.x - w / 2f
    val right = center.x + w / 2f
    val top = center.y - h / 2f
    val bottom = center.y + h / 2f
    val stroke = ReceiptStroke.toPx()
    val teeth = 4
    val toothWidth = w / teeth
    val toothHeight = h * 0.07f

    val body = Path().apply {
        moveTo(left, bottom)
        lineTo(left, top)
        lineTo(right, top)
        lineTo(right, bottom)
        // Torn bottom edge, right to left.
        for (i in 0 until teeth) {
            val x = right - i * toothWidth
            lineTo(x - toothWidth / 2f, bottom - toothHeight)
            lineTo(x - toothWidth, bottom)
        }
        close()
    }
    drawPath(body, color, style = Stroke(width = stroke, cap = StrokeCap.Round))

    val lineInset = w * 0.22f
    drawLine(
        color = color,
        start = Offset(left + lineInset, top + h * 0.30f),
        end = Offset(right - lineInset, top + h * 0.30f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = Offset(left + lineInset, top + h * 0.46f),
        end = Offset(right - lineInset * 1.8f, top + h * 0.46f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
}

/** "We ask for the camera here, and only for this." Said before the OS asks. */
@Composable
private fun PermissionNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OdoTheme.shapes.field)
            .background(OdoTheme.colors.surface)
            .border(1.dp, OdoTheme.colors.border, OdoTheme.shapes.field)
            .padding(OdoTheme.spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoIcon(
            IcCamera,
            contentDescription = null,
            tint = OdoTheme.colors.textDim,
            size = OdoTheme.iconSizes.medium,
        )
        OdoText(
            text = stringResource(Res.string.onb_scan_permission),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
        )
    }
}

private val FrameHeight = 240.dp
private val FrameCorner = 22.dp
private val StripeGap = 26.dp
private val StripeWidth = 10.dp
private val BracketInset = 18.dp
private val BracketLength = 34.dp
private val BracketCorner = 12.dp
private val BracketStroke = 3.dp
private val ReceiptWidth = 40.dp
private val ReceiptHeight = 52.dp
private val ReceiptStroke = 2.dp

@OdoThemePreviews
@Composable
private fun FirstScanScreenPreview() = OdoPreview(padded = false) {
    FirstScanScreen(onEvent = {})
}

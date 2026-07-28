package com.hopcape.odo.feature.onboarding.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoProgressBar
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcArrowLeft
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.onboarding.presentation.OnboardingStep
import com.hopcape.odo.feature.onboarding.resources.Res
import com.hopcape.odo.feature.onboarding.resources.onb_cd_back
import com.hopcape.odo.feature.onboarding.resources.onb_step_counter
import org.jetbrains.compose.resources.stringResource

/**
 * The frame every setup step shares: a header (back · progress · step counter, or a
 * Skip on the last step), a scrolling body, and a pinned primary action.
 *
 * Keeping it in one place is what makes the flow feel like one screen changing its mind
 * rather than four screens in a row — the header and CTA never move between steps.
 *
 * @param step 1-based position used for the counter and the progress fill.
 * @param onBack `null` hides the back button (the last step has no way back).
 * @param skipLabel when non-null (with [onSkip]), replaces the counter with a Skip action.
 * @param footer optional tertiary action under the CTA ("I'll do this later").
 */
@Composable
internal fun OnboardingStepScaffold(
    step: Int,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    totalSteps: Int = OnboardingStep.TOTAL,
    primaryEnabled: Boolean = true,
    primaryLeadingIcon: (@Composable () -> Unit)? = null,
    skipLabel: String? = null,
    onSkip: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(OdoTheme.colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        StepHeader(
            step = step,
            totalSteps = totalSteps,
            onBack = onBack,
            skipLabel = skipLabel,
            onSkip = onSkip,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OdoTheme.spacing.screenEdge)
                .padding(top = OdoTheme.spacing.lg, bottom = OdoTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
            content = content,
        )
        Column(
            modifier = Modifier
                .padding(horizontal = OdoTheme.spacing.screenEdge)
                .padding(bottom = OdoTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OdoButton(
                text = primaryLabel,
                onClick = onPrimary,
                modifier = Modifier.fillMaxWidth().accentGlow(enabled = primaryEnabled),
                enabled = primaryEnabled,
                leadingIcon = primaryLeadingIcon,
            )
            footer?.invoke()
        }
    }
}

/** Back · progress · counter. The progress bar is the constant: it only ever grows. */
@Composable
private fun StepHeader(
    step: Int,
    totalSteps: Int,
    onBack: (() -> Unit)?,
    skipLabel: String?,
    onSkip: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OdoTheme.spacing.screenEdge, vertical = OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        if (onBack != null) {
            BackButton(onBack)
        }
        OdoProgressBar(
            progress = step.toFloat() / totalSteps,
            modifier = Modifier.weight(1f),
            height = 5.dp,
        )
        if (skipLabel != null && onSkip != null) {
            OdoText(
                text = skipLabel,
                style = OdoTheme.typography.heading.copy(fontSize = 16.sp),
                color = OdoTheme.colors.text,
                modifier = Modifier
                    .clip(OdoTheme.shapes.pill)
                    .clickable(role = Role.Button, onClick = onSkip)
                    .padding(horizontal = OdoTheme.spacing.sm, vertical = OdoTheme.spacing.xs),
            )
        } else {
            OdoText(
                text = stringResource(Res.string.onb_step_counter, step, totalSteps),
                style = OdoTheme.typography.label,
                color = OdoTheme.colors.textDim,
            )
        }
    }
}

/** The circular back affordance — a soft surface disc, borderless, not a bare arrow. */
@Composable
private fun BackButton(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .size(OdoTheme.spacing.minTouchTarget)
            .clip(CircleShape)
            .background(OdoTheme.colors.surface)
            .clickable(role = Role.Button, onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        OdoIcon(
            IcArrowLeft,
            contentDescription = stringResource(Res.string.onb_cd_back),
            tint = OdoTheme.colors.text,
            size = OdoTheme.iconSizes.medium,
        )
    }
}

/**
 * The accent bloom under a primary CTA — the same treatment the paywall's CTA uses, so
 * "this is the way forward" reads identically across the app. Dropped when disabled,
 * where a glow would promise something the button won't do.
 */
@Composable
internal fun Modifier.accentGlow(enabled: Boolean = true, elevation: Dp = 18.dp): Modifier =
    if (!enabled) {
        this
    } else {
        shadow(
            elevation = elevation,
            shape = OdoTheme.shapes.pill,
            clip = false,
            ambientColor = OdoTheme.colors.accent,
            spotColor = OdoTheme.colors.accent,
        )
    }

/**
 * A step's title + subtitle block. Steps 2 and 3 left-align it under the header; the
 * first-scan step centres it, because there is no form beneath to anchor the eye.
 */
@Composable
internal fun StepHeadline(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        OdoText(
            text = title,
            style = OdoTheme.typography.title.copy(fontSize = 28.sp, lineHeight = 34.sp),
            color = OdoTheme.colors.text,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        OdoText(
            text = subtitle,
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
    }
}

/**
 * Fills a `%1$d` count template outside composition. The design system's pickers take a
 * plain `(Int) -> String` for their match-count eyebrow — that lambda is called during
 * layout of the sheet, where `stringResource` can't be, so the template is read once in
 * composition and formatted here.
 */
internal fun String.withCount(count: Int): String = replace("%1\$d", count.toString())

/** A tracked-caps field eyebrow ("MAKE", "FUEL") for the manual-details grid. */
@Composable
internal fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    OdoText(
        text = text,
        style = OdoTheme.typography.caption,
        color = OdoTheme.colors.textMuted,
        modifier = modifier,
    )
}

/**
 * The rounded accent tile that heads a value row, a goal card, or the matched-car card.
 * One helper so every glyph in the flow sits in the same 44dp square with the same wash.
 */
@Composable
internal fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = OdoTheme.colors.accent,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(OdoTheme.shapes.small)
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        OdoIcon(icon, contentDescription = contentDescription, tint = tint, size = OdoTheme.iconSizes.medium)
    }
}

/**
 * An inline "prompt + action" line — the escape hatch each car step offers to the other
 * ("Not your car? **Enter details manually**"). The whole row is the target, so the tap
 * area isn't the width of two words.
 *
 * @param leadingIcon optional glyph before the prompt (the auto-fill hint carries one).
 * @param boxed draws it as a bordered surface row rather than bare text.
 */
@Composable
internal fun InlineLinkRow(
    prompt: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    boxed: Boolean = false,
) {
    val base = modifier
        .fillMaxWidth()
        .clip(OdoTheme.shapes.field)
    val decorated = if (boxed) {
        base
            .background(OdoTheme.colors.surface)
            .border(1.dp, OdoTheme.colors.border, OdoTheme.shapes.field)
    } else {
        base
    }
    Row(
        modifier = decorated
            .clickable(role = Role.Button, onClick = onClick)
            .padding(
                horizontal = if (boxed) OdoTheme.spacing.lg else OdoTheme.spacing.xs,
                vertical = if (boxed) OdoTheme.spacing.md else OdoTheme.spacing.xs,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
    ) {
        if (leadingIcon != null) {
            OdoIcon(
                leadingIcon,
                contentDescription = null,
                tint = OdoTheme.colors.accent,
                size = OdoTheme.iconSizes.medium,
            )
        }
        OdoText(prompt, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
        OdoText(
            text = action,
            style = OdoTheme.typography.label.copy(fontWeight = FontWeight.Bold),
            color = OdoTheme.colors.accent,
        )
    }
}

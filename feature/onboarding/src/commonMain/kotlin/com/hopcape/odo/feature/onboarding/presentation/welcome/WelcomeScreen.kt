package com.hopcape.odo.feature.onboarding.presentation.welcome

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcBarChart
import com.hopcape.odo.core.designsystem.icons.IcCamera
import com.hopcape.odo.core.designsystem.icons.IcRupee
import com.hopcape.odo.core.designsystem.icons.IcTagFilled
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.onboarding.presentation.components.IconTile
import com.hopcape.odo.feature.onboarding.presentation.components.accentGlow
import com.hopcape.odo.feature.onboarding.resources.Res
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_cta
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_legal
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_legal_privacy
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_legal_terms
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_mark
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_mark_reading
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_subtitle
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_title
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_value_bill
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_value_cost
import com.hopcape.odo.feature.onboarding.resources.onb_welcome_value_resale
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Step 1 — the value pitch and the only sign-in door: the odometer mark, what Odo is for
 * in one line, three concrete promises, and "Continue with mobile".
 *
 * It asks for nothing. The mock deliberately shows no phone field here — the number is
 * collected by the auth flow this hands off to, so the first screen stays a pitch.
 *
 * Stateless: renders nothing but constants and forwards [WelcomeEvent]s.
 */
@Composable
internal fun WelcomeScreen(
    onEvent: (WelcomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(modifier.fillMaxSize().background(OdoTheme.colors.bg)) {
        // A dark-teal bloom bleeding from the very top, behind the status bar — the same
        // "there's something warm here" cue the paywall uses, at half the strength.
        Box(
            Modifier
                .fillMaxWidth()
                .height(GlowHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(OdoTheme.colors.accent.copy(alpha = 0.10f), Color.Transparent),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = OdoTheme.spacing.screenEdge),
        ) {
            // The pitch sits low: empty space above it is what makes the mark read as a
            // logo rather than a header.
            Spacer(Modifier.weight(1f))
            Entrance(0, visible) { OdometerMark() }
            Spacer(Modifier.height(OdoTheme.spacing.xl))
            Entrance(1, visible) {
                Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
                    OdoText(
                        text = stringResource(Res.string.onb_welcome_title),
                        style = OdoTheme.typography.display.copy(fontSize = 32.sp, lineHeight = 38.sp),
                        color = OdoTheme.colors.text,
                    )
                    OdoText(
                        text = stringResource(Res.string.onb_welcome_subtitle),
                        style = OdoTheme.typography.body,
                        color = OdoTheme.colors.textDim,
                    )
                }
            }
            Spacer(Modifier.height(OdoTheme.spacing.xl))
            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
                VALUE_PROPS.forEachIndexed { index, prop ->
                    Entrance(2 + index, visible) { ValueRow(prop) }
                }
            }
            Spacer(Modifier.weight(0.9f))
            Entrance(5, visible) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    OdoButton(
                        text = stringResource(Res.string.onb_welcome_cta),
                        onClick = { onEvent(WelcomeEvent.ContinueClicked) },
                        modifier = Modifier.fillMaxWidth().accentGlow(),
                    )
                    LegalFooter(
                        onTerms = { onEvent(WelcomeEvent.TermsClicked) },
                        onPrivacy = { onEvent(WelcomeEvent.PrivacyClicked) },
                    )
                }
            }
            Spacer(Modifier.height(OdoTheme.spacing.lg))
        }
    }
}

/* ------------------------------ Brand mark ------------------------------ */

/**
 * The Odo mark: a gauge ring around the wordmark and a stand-in reading. The ring's gap
 * sits at the bottom, so it reads as a dial sweeping — the odometer is the product.
 */
@Composable
private fun OdometerMark() {
    val accent = OdoTheme.colors.accent
    Box(Modifier.size(MarkSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            // Track first, then the live sweep over it — same construction as the health dial.
            drawArc(
                color = accent.copy(alpha = 0.18f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = accent,
                startAngle = 128f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            OdoText(
                text = stringResource(Res.string.onb_welcome_mark),
                style = OdoTheme.typography.heading.copy(fontSize = 20.sp, letterSpacing = 0.5.sp),
                color = OdoTheme.colors.text,
            )
            OdoText(
                text = stringResource(Res.string.onb_welcome_mark_reading),
                style = OdoTheme.typography.caption.copy(fontSize = 9.sp),
                color = accent,
            )
        }
    }
}

/* ------------------------------ Value props ------------------------------ */

private data class ValueProp(val icon: ImageVector, val text: StringResource)

private val VALUE_PROPS = listOf(
    ValueProp(IcCamera, Res.string.onb_welcome_value_bill),
    ValueProp(IcRupee, Res.string.onb_welcome_value_cost),
    ValueProp(IcTagFilled, Res.string.onb_welcome_value_resale),
)

@Composable
private fun ValueRow(prop: ValueProp) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(prop.icon)
        OdoText(
            text = stringResource(prop.text),
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.text,
        )
    }
}

/* ------------------------------ Legal ------------------------------ */

/**
 * "By continuing you agree to our Terms and Privacy Policy." — one localisable sentence
 * with the two link labels substituted in, so translations keep the grammar and the links
 * stay tappable wherever they land in the sentence.
 */
@Composable
private fun LegalFooter(onTerms: () -> Unit, onPrivacy: () -> Unit) {
    val terms = stringResource(Res.string.onb_welcome_legal_terms)
    val privacy = stringResource(Res.string.onb_welcome_legal_privacy)
    val sentence = stringResource(Res.string.onb_welcome_legal, terms, privacy)
    val linkStyle = TextLinkStyles(
        style = SpanStyle(color = OdoTheme.colors.text, fontWeight = FontWeight.Bold),
    )

    val text = buildAnnotatedString {
        var cursor = 0
        // Ordered by where each label actually lands, so a translation that reverses them
        // still renders in reading order.
        val links = listOf(terms to onTerms, privacy to onPrivacy)
            .mapNotNull { (label, action) ->
                sentence.indexOf(label).takeIf { it >= 0 }?.let { Triple(it, label, action) }
            }
            .sortedBy { it.first }
        links.forEach { (start, label, action) ->
            if (start > cursor) append(sentence.substring(cursor, start))
            withLink(LinkAnnotation.Clickable(tag = label, styles = linkStyle) { action() }) {
                append(label)
            }
            cursor = start + label.length
        }
        if (cursor < sentence.length) append(sentence.substring(cursor))
    }

    OdoText(
        text = text,
        style = OdoTheme.typography.bodySmall.copy(fontSize = 12.sp),
        color = OdoTheme.colors.textMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/* ------------------------------ Motion ------------------------------ */

/** Staggered rise-and-fade, matching the paywall's entrance so first-run feels of a piece. */
@Composable
private fun Entrance(index: Int, visible: Boolean, content: @Composable () -> Unit) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 480, delayMillis = 80 * index, easing = FastOutSlowInEasing),
        label = "welcomeEntrance$index",
    )
    Box(
        Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 24.dp.toPx()
        },
    ) { content() }
}

private val MarkSize = 84.dp
private val GlowHeight = 420.dp

@OdoThemePreviews
@Composable
private fun WelcomeScreenPreview() = OdoPreview(padded = false) {
    WelcomeScreen(onEvent = {})
}

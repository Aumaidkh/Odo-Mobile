package com.hopcape.odo.feature.billscanner.presentation.scan

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcClose
import com.hopcape.odo.core.designsystem.icons.IcImage
import com.hopcape.odo.core.designsystem.icons.IcList
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.billscanner.resources.Res
import com.hopcape.odo.feature.billscanner.resources.bs_cd_close
import com.hopcape.odo.feature.billscanner.resources.bs_cd_gallery
import com.hopcape.odo.feature.billscanner.resources.bs_scan_align
import com.hopcape.odo.feature.billscanner.resources.bs_scan_detecting
import com.hopcape.odo.feature.billscanner.resources.bs_scan_manual
import com.hopcape.odo.feature.billscanner.resources.bs_scan_quota
import com.hopcape.odo.feature.billscanner.resources.bs_scan_title
import org.jetbrains.compose.resources.stringResource

/**
 * The "Scan bill" camera screen — the entry point to the make-or-break AI Bill
 * Scanner. Presents the viewfinder (framed by animated edge brackets + a sweeping
 * scan line), the free-scan quota, and the three capture affordances: pick from
 * gallery, shutter, or fall back to manual entry.
 *
 * State-free by design: it renders [state] and forwards intents. The real camera
 * preview + edge detection replace the placeholder viewfinder when the CameraX
 * `actual` and `ai-bill-scan` pipeline land (M2).
 */
@Composable
internal fun BillScanScreen(
    state: BillScanUiState,
    onClose: () -> Unit,
    onCapture: () -> Unit,
    onPickGallery: () -> Unit,
    onManual: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        topBar = { ScanTopBar(state = state, onClose = onClose) },
        bottomBar = { ScanControls(onPickGallery = onPickGallery, onCapture = onCapture, onManual = onManual) },
    ) { padding ->
        Viewfinder(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(vertical = OdoTheme.spacing.md),
        )
    }
}

@Composable
private fun ScanTopBar(state: BillScanUiState, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = OdoTheme.spacing.screenEdge, vertical = OdoTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(OdoTheme.colors.surfaceRaised)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            OdoIcon(
                IcClose,
                contentDescription = stringResource(Res.string.bs_cd_close),
                tint = OdoTheme.colors.text,
                size = OdoTheme.iconSizes.medium,
            )
        }
        OdoText(stringResource(Res.string.bs_scan_title), style = OdoTheme.typography.heading)
        Spacer(Modifier.weight(1f))
        OdoBadge(
            text = stringResource(Res.string.bs_scan_quota, state.freeRemaining, state.freeTotal),
            tone = OdoBadgeTone.Accent,
        )
    }
}

/**
 * The framed capture area: a faint bill silhouette (placeholder for the camera
 * preview), animated accent corner brackets + a sweeping scan line, and the
 * "align / auto-detecting" guidance pinned to the bottom.
 */
@Composable
private fun Viewfinder(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "viewfinder")
    val scan by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scanLine",
    )
    val accent = OdoTheme.colors.accent
    Box(
        modifier
            .clip(OdoTheme.shapes.device)
            .border(1.dp, OdoTheme.colors.border, OdoTheme.shapes.device),
        contentAlignment = Alignment.Center,
    ) {
        BillSilhouette()
        Canvas(Modifier.fillMaxSize().padding(20.dp)) {
            val arm = 30.dp.toPx()
            val stroke = 3.dp.toPx()
            val w = size.width
            val h = size.height
            fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(accent, Offset(x, y), Offset(x + dx, y), stroke, StrokeCap.Round)
                drawLine(accent, Offset(x, y), Offset(x, y + dy), stroke, StrokeCap.Round)
            }
            corner(0f, 0f, arm, arm)                 // top-left
            corner(w, 0f, -arm, arm)                 // top-right
            corner(0f, h, arm, -arm)                 // bottom-left
            corner(w, h, -arm, -arm)                 // bottom-right

            val y = h * scan
            drawLine(
                color = accent.copy(alpha = 0.85f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = OdoTheme.spacing.lg, vertical = OdoTheme.spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        ) {
            OdoText(
                stringResource(Res.string.bs_scan_align),
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.text,
                textAlign = TextAlign.Center,
            )
            DetectingLabel(transition)
        }
    }
}

/** A faint document placeholder standing in for the live camera preview. */
@Composable
private fun BillSilhouette() {
    Column(
        modifier = Modifier
            .fillMaxWidth(0.62f)
            .aspectRatio(0.72f)
            .clip(OdoTheme.shapes.card)
            .background(OdoTheme.colors.surface.copy(alpha = 0.35f))
            .padding(OdoTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
    ) {
        SkeletonBar(0.7f)
        SkeletonBar(0.45f)
        Spacer(Modifier.height(OdoTheme.spacing.sm))
        repeat(4) { SkeletonBar(1f) }
        Spacer(Modifier.weight(1f))
        SkeletonBar(0.55f)
    }
}

@Composable
private fun SkeletonBar(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth(fraction)
            .height(8.dp)
            .clip(OdoTheme.shapes.pill)
            .background(OdoTheme.colors.surfaceRaised.copy(alpha = 0.6f)),
    )
}

/** The pulsing "● AUTO-DETECTING EDGES" status line. */
@Composable
private fun DetectingLabel(transition: InfiniteTransition) {
    val dotAlpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "detectDot",
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(OdoTheme.colors.accent.copy(alpha = dotAlpha)),
        )
        OdoText(
            stringResource(Res.string.bs_scan_detecting),
            style = OdoTheme.typography.caption,
            color = OdoTheme.colors.accent,
        )
    }
}

@Composable
private fun ScanControls(
    onPickGallery: () -> Unit,
    onCapture: () -> Unit,
    onManual: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = OdoTheme.spacing.xl, vertical = OdoTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(OdoTheme.shapes.field)
                    .background(OdoTheme.colors.surfaceRaised)
                    .clickable(onClick = onPickGallery),
                contentAlignment = Alignment.Center,
            ) {
                OdoIcon(
                    IcImage,
                    contentDescription = stringResource(Res.string.bs_cd_gallery),
                    tint = OdoTheme.colors.text,
                    size = OdoTheme.iconSizes.medium,
                )
            }
        }

        ShutterButton(onCapture)

        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Column(
                modifier = Modifier
                    .clip(OdoTheme.shapes.field)
                    .clickable(onClick = onManual)
                    .padding(horizontal = OdoTheme.spacing.sm, vertical = OdoTheme.spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
            ) {
                OdoIcon(IcList, contentDescription = null, tint = OdoTheme.colors.text, size = OdoTheme.iconSizes.medium)
                OdoText(stringResource(Res.string.bs_scan_manual), style = OdoTheme.typography.caption, color = OdoTheme.colors.textDim)
            }
        }
    }
}

/** The large accent capture button — an orange disc with an inset ring. */
@Composable
private fun ShutterButton(onCapture: () -> Unit) {
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(OdoTheme.colors.accent)
            .clickable(onClick = onCapture),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(60.dp)
                .clip(CircleShape)
                .border(3.dp, OdoTheme.colors.bg, CircleShape),
        )
    }
}

@OdoThemePreviews
@Composable
private fun BillScanScreenPreview() = OdoPreview(padded = false) {
    BillScanScreen(
        state = sampleBillScanState(),
        onClose = {},
        onCapture = {},
        onPickGallery = {},
        onManual = {},
    )
}

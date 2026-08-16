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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import com.hopcape.odo.core.designsystem.text.asString
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.core.designsystem.component.OdoChip
import com.hopcape.odo.core.designsystem.component.OdoCircularIconButton
import com.hopcape.odo.core.designsystem.component.OdoCircularIconButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoQuadMarkers
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcClose
import com.hopcape.odo.core.designsystem.icons.IcImage
import com.hopcape.odo.core.designsystem.icons.IcLightningFilled
import com.hopcape.odo.core.designsystem.icons.IcList
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.navigation.ScanTarget
import com.hopcape.odo.core.platform.camera.CameraEvent
import com.hopcape.odo.core.platform.camera.CameraFailure
import com.hopcape.odo.core.platform.camera.OdoCameraPreview
import com.hopcape.odo.core.platform.camera.OdoCameraState
import com.hopcape.odo.core.platform.camera.rememberOdoCameraState
import com.hopcape.odo.feature.billscanner.resources.Res
import com.hopcape.odo.feature.billscanner.resources.bs_camera_capture_failed
import com.hopcape.odo.feature.billscanner.resources.bs_camera_unavailable
import com.hopcape.odo.feature.billscanner.resources.bs_cd_close
import com.hopcape.odo.feature.billscanner.resources.bs_scan_edges_locked
import com.hopcape.odo.feature.billscanner.resources.bs_scan_edges_pinned
import com.hopcape.odo.feature.billscanner.resources.bs_cd_gallery
import com.hopcape.odo.feature.billscanner.resources.bs_cd_torch_off
import com.hopcape.odo.feature.billscanner.resources.bs_cd_torch_on
import com.hopcape.odo.feature.billscanner.resources.bs_mode_bill
import com.hopcape.odo.feature.billscanner.resources.bs_mode_document
import com.hopcape.odo.feature.billscanner.resources.bs_mode_pump
import com.hopcape.odo.feature.billscanner.resources.bs_scan_align
import com.hopcape.odo.feature.billscanner.resources.bs_scan_align_document
import com.hopcape.odo.feature.billscanner.resources.bs_scan_align_pump
import com.hopcape.odo.feature.billscanner.resources.bs_scan_detecting
import com.hopcape.odo.feature.billscanner.resources.bs_scan_manual
import com.hopcape.odo.feature.billscanner.resources.bs_scan_quota
import com.hopcape.odo.feature.billscanner.resources.bs_scan_title
import com.hopcape.odo.feature.billscanner.resources.bs_scan_title_document
import com.hopcape.odo.feature.billscanner.resources.bs_scan_title_pump
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The "Scan bill" camera screen — the entry point to the make-or-break AI Bill
 * Scanner. Presents the viewfinder (a live camera preview framed by animated edge
 * brackets + a sweeping scan line), the free-scan quota, and the three capture
 * affordances: pick from gallery, shutter, or fall back to manual entry.
 *
 * State-free by design: it renders [state] and forwards intents. The camera itself is
 * driven through [cameraState], so the screen never touches a platform camera type.
 *
 * It is only ever shown with the camera allowed — the rationale stands in front of it until
 * then. The permission checks that remain are there so a grant withdrawn while the app was
 * away leaves a still frame rather than a preview bound to a camera this process can no longer
 * open. AI edge detection replaces the static brackets when the `ai-bill-scan` pipeline lands
 * (M2).
 */
@Composable
internal fun BillScanScreen(
    state: BillScanUiState,
    cameraState: OdoCameraState,
    onCameraEvent: (CameraEvent) -> Unit,
    onClose: () -> Unit,
    onCapture: () -> Unit,
    onPickGallery: () -> Unit,
    onManual: () -> Unit,
    onTargetSelected: (ScanTarget) -> Unit,
    onToggleEdgeLock: () -> Unit,
    onQuotaTapped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        topBar = { ScanTopBar(state = state, cameraState = cameraState, onClose = onClose, onQuotaTapped = onQuotaTapped) },
        bottomBar = {
            Column {
                state.failure?.let { message ->
                    OdoText(
                        message.asString(),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.danger,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = OdoTheme.spacing.xl, vertical = OdoTheme.spacing.xs),
                    )
                }
                TargetChips(selected = state.target, onSelect = onTargetSelected)
                ScanControls(
                    showShutter = true,
                    // Manual entry is a bill-form fallback; a document has no hand-typed
                    // equivalent to fall back to, and a pump display has its own.
                    showManual = state.target == ScanTarget.Bill,
                    onPickGallery = onPickGallery,
                    onCapture = onCapture,
                    onManual = onManual,
                )
            }
        },
    ) { padding ->
        Viewfinder(
            state = state,
            cameraState = cameraState,
            onCameraEvent = onCameraEvent,
            onToggleEdgeLock = onToggleEdgeLock,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(vertical = OdoTheme.spacing.md),
        )
    }
}

/**
 * The Bill · Document · Pay QR selector.
 *
 * The owner states what they came for when they open the scanner, but they are holding a
 * phone in a workshop and change their mind — switching here costs one tap instead of backing
 * out of the whole flow and starting again.
 */
@Composable
private fun TargetChips(selected: ScanTarget, onSelect: (ScanTarget) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OdoTheme.spacing.screenEdge),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm, Alignment.CenterHorizontally),
    ) {
        scanTargets.forEach { target ->
            OdoChip(
                label = stringResource(target.labelResource()),
                onClick = { onSelect(target) },
                selected = target == selected,
            )
        }
    }
}

/** The chip label for each target. Kept next to the chips so a new target cannot ship unlabelled. */
private fun ScanTarget.labelResource(): StringResource = when (this) {
    ScanTarget.Bill -> Res.string.bs_mode_bill
    ScanTarget.Document -> Res.string.bs_mode_document
    ScanTarget.PumpDisplay -> Res.string.bs_mode_pump
}

/** The screen title, which names what is being scanned. */
private fun ScanTarget.titleResource(): StringResource = when (this) {
    ScanTarget.Bill -> Res.string.bs_scan_title
    ScanTarget.Document -> Res.string.bs_scan_title_document
    ScanTarget.PumpDisplay -> Res.string.bs_scan_title_pump
}

/** The guidance under the frame. */
private fun ScanTarget.guidanceResource(): StringResource = when (this) {
    ScanTarget.Bill -> Res.string.bs_scan_align
    ScanTarget.Document -> Res.string.bs_scan_align_document
    ScanTarget.PumpDisplay -> Res.string.bs_scan_align_pump
}

@Composable
private fun ScanTopBar(
    state: BillScanUiState,
    cameraState: OdoCameraState,
    onClose: () -> Unit,
    onQuotaTapped: () -> Unit,
) {
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
        OdoText(stringResource(state.target.titleResource()), style = OdoTheme.typography.heading)
        Spacer(Modifier.weight(1f))
        // A workshop counter is often badly lit and a thermal bill is low-contrast to begin
        // with, so the torch is part of the scanner rather than a setting somewhere.
        if (state.cameraGranted) {
            OdoCircularIconButton(
                imageVector = IcLightningFilled,
                contentDescription = if (cameraState.isTorchOn) {
                    stringResource(Res.string.bs_cd_torch_off)
                } else {
                    stringResource(Res.string.bs_cd_torch_on)
                },
                onClick = cameraState::toggleTorch,
                variant = if (cameraState.isTorchOn) {
                    OdoCircularIconButtonVariant.Outlined
                } else {
                    OdoCircularIconButtonVariant.Raised
                },
            )
        }
        // Hidden rather than shown as "0 of 0" while the allowance is still being read, and
        // hidden in QR mode, which spends no scan at all.
        if (state.showQuota) {
            OdoBadge(
                text = stringResource(Res.string.bs_scan_quota, state.freeRemaining, state.freeTotal),
                tone = OdoBadgeTone.Accent,
                modifier = Modifier
                    .clip(OdoTheme.shapes.pill)
                    .clickable(onClick = onQuotaTapped),
            )
        }
    }
}

/**
 * The framed capture area: the live camera preview, animated accent corner brackets + a
 * sweeping scan line, and the "align / auto-detecting" guidance pinned to the bottom.
 *
 * The bill silhouette stays underneath the preview rather than being replaced by it. It is
 * what fills the frame in the moment between the screen appearing and the camera producing
 * its first frame, which on a cold start is long enough to look broken.
 */
@Composable
private fun Viewfinder(
    state: BillScanUiState,
    cameraState: OdoCameraState,
    onCameraEvent: (CameraEvent) -> Unit,
    onToggleEdgeLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "viewfinder")
    val scan by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scanLine",
    )
    val accent = OdoTheme.colors.accent
    // The viewfinder's pixel size — the frame-to-viewport mapping for the edge markers
    // needs it, because the preview centre-crops the camera frame to fill this box.
    var viewfinderSize by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier
            .onSizeChanged { viewfinderSize = it }
            .clip(OdoTheme.shapes.device)
            .border(1.dp, OdoTheme.colors.border, OdoTheme.shapes.device)
            // Tap to pin the detected outline (and tap again to release). No ripple: a
            // flash over a live camera preview reads as a capture, which this is not.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleEdgeLock,
            ),
        contentAlignment = Alignment.Center,
    ) {
        BillSilhouette()
        if (state.cameraGranted) {
            OdoCameraPreview(
                state = cameraState,
                onEvent = onCameraEvent,
                modifier = Modifier.fillMaxSize(),
                analysis = state.frameAnalysis,
            )
        }
        // The detected paper outline, mapped from camera-frame space onto this viewport
        // (the preview centre-crops to fill, and the mapping accounts for that —
        // including the not-yet-measured viewport, for which it answers no corners).
        OdoQuadMarkers(
            corners = state.detectedQuad
                ?.cornersInViewport(viewfinderSize.width.toFloat(), viewfinderSize.height.toFloat()),
            modifier = Modifier.fillMaxSize(),
            locked = state.edgesLocked,
        )
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
            if (state.cameraGranted) {
                val failure = state.cameraFailure
                if (failure == null) {
                    OdoText(
                        stringResource(
                            // The outline is on screen — tell the owner the next move,
                            // which changes once they pin it.
                            when {
                                state.edgesLocked -> Res.string.bs_scan_edges_pinned
                                state.detectedQuad != null -> Res.string.bs_scan_edges_locked
                                else -> state.target.guidanceResource()
                            },
                        ),
                        style = OdoTheme.typography.body,
                        color = OdoTheme.colors.text,
                        textAlign = TextAlign.Center,
                    )
                    DetectingLabel(transition)
                } else {
                    OdoText(
                        text = when (failure) {
                            CameraFailure.Unavailable -> stringResource(Res.string.bs_camera_unavailable)
                            CameraFailure.CaptureFailed -> stringResource(Res.string.bs_camera_capture_failed)
                        },
                        style = OdoTheme.typography.body,
                        color = OdoTheme.colors.danger,
                        textAlign = TextAlign.Center,
                    )
                }
            }
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
    showShutter: Boolean,
    showManual: Boolean,
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

        if (showShutter) {
            ShutterButton(onCapture)
        } else {
            // Keeps the gallery and manual affordances where they were: a control row that
            // reflows when the mode changes reads as a different screen.
            Spacer(Modifier.size(SHUTTER_SIZE))
        }

        // The weighted box stays even without the affordance, so the shutter keeps its
        // centre when the mode changes instead of the row reflowing.
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (showManual) {
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
}

/** The large accent capture button — an orange disc with an inset ring. */
@Composable
private fun ShutterButton(onCapture: () -> Unit) {
    Box(
        Modifier
            .size(SHUTTER_SIZE)
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

/** The shutter's diameter, shared with the spacer that stands in for it in QR mode. */
private val SHUTTER_SIZE = 72.dp

// Granted, which is the only way this screen is reached — but a preview host has no camera to
// bind, so what renders here is the frame and its chrome around the silhouette.
@OdoThemePreviews
@Composable
private fun BillScanScreenPreview() = OdoPreview(padded = false) {
    BillScanScreen(
        state = sampleBillScanState(),
        cameraState = rememberOdoCameraState(),
        onCameraEvent = {},
        onClose = {},
        onCapture = {},
        onPickGallery = {},
        onManual = {},
        onTargetSelected = {},
        onToggleEdgeLock = {},
        onQuotaTapped = {},
    )
}

package com.hopcape.odo.web.admin.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Lifts a surface off the page.
 *
 * **Two shadows, not one.** A single blur at a single alpha is what an untouched
 * `Modifier.shadow` looks like, and it reads as flat however far it is pushed: at
 * low elevation it is a grey smudge, at high elevation a grey cloud. Depth reads
 * when two layers disagree — a small, relatively opaque one under the edge that
 * says the surface is *touching*, and a wide, very light one that says there is
 * room around it. That pairing is the whole of the effect.
 *
 * Chaining two `shadow` modifiers is what draws them: each one renders its own blur
 * behind everything after it in the chain, so the ambient layer lands first and the
 * contact layer sits on top of it. Both use the same [shape], so the pair stays
 * registered as the corner radius changes.
 *
 * `clip = false` on both, deliberately. Clipping to the shape here would also clip
 * the border drawn later in the chain, which leaves the outline hairline-thin
 * exactly on the corners where it is most visible.
 *
 * @param level how far off the page — see [AdminElevation].
 */
@Composable
fun Modifier.raised(level: Elevation, shape: Shape): Modifier {
    val palette = LocalAdminPalette.current
    return this
        .shadow(
            elevation = level.ambient,
            shape = shape,
            clip = false,
            ambientColor = palette.shadowAmbient,
            spotColor = palette.shadowAmbient,
        )
        .shadow(
            elevation = level.key,
            shape = shape,
            clip = false,
            ambientColor = palette.shadowKey,
            spotColor = palette.shadowKey,
        )
}

/**
 * The outline of a raised surface.
 *
 * A gradient rather than a flat colour, and only in the dark theme: a #0A0A0A card
 * on a #000000 page casts no visible shadow at any elevation, so the thing that
 * makes it read as raised is a lighter edge along the *top* — the same cue a real
 * surface gives by catching light from above. The gradient fades back to the plain
 * border within the first few pixels, so the sides and bottom are unchanged.
 *
 * In the light theme the shadow is doing that job, and a highlight on white would
 * read as a seam rather than as a lit edge — so this is the flat border there, and
 * the whole thing collapses to what it was.
 */
@Composable
fun raisedBorder(): Brush {
    val palette = LocalAdminPalette.current
    if (!palette.isDark) return SolidColor(palette.border)

    // A fixed run in pixels, not a fraction of the panel.
    //
    // `verticalGradient(vararg stops)` spans the whole height, so a 0.08 stop is
    // four pixels on a metric card and fifty on a full-page table — the tall one
    // stops looking lit from above and starts looking lit from inside. Pinning
    // startY/endY keeps the fade the same few pixels whatever the panel is.
    val fade = with(LocalDensity.current) { HIGHLIGHT_FADE.toPx() }
    return Brush.verticalGradient(
        colors = listOf(
            // compositeOver flattens the translucent highlight onto the border: a
            // gradient stop has to be opaque, or the page shows through the outline
            // instead of the outline being lightened.
            palette.edgeHighlight.compositeOver(palette.border),
            palette.border,
        ),
        startY = 0f,
        endY = fade,
    )
}

/** How far the lit edge runs before it is the ordinary border again. */
private val HIGHLIGHT_FADE = 6.dp

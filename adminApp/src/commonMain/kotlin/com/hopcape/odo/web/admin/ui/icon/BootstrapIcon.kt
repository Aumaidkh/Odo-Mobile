package com.hopcape.odo.web.admin.ui.icon

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser

/**
 * Draws one [BootstrapIcon], tinted, scaled to whatever [modifier] sizes it.
 *
 * The parse is `remember`ed on the icon: `PathParser` walks a few hundred characters
 * and allocates a `Path`, and the rail redraws these on every hover.
 *
 * Scaled from Bootstrap's 16x16 viewBox by the smaller dimension, so a non-square
 * modifier letterboxes rather than stretching — an icon squashed to fit is worse
 * than one with space around it.
 */
@Composable
fun BootstrapIcon(icon: BootstrapIcon, tint: Color, modifier: Modifier = Modifier) {
    val paths = remember(icon) {
        icon.paths.map { spec ->
            PathParser().parsePathString(spec.data).toPath().apply {
                fillType = if (spec.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero
            }
        }
    }
    Canvas(modifier) {
        val factor = size.minDimension / VIEWBOX
        scale(scaleX = factor, scaleY = factor, pivot = Offset.Zero) {
            paths.forEach { path -> drawPath(path, color = tint) }
        }
    }
}

/** Bootstrap authors every icon in this box. */
private const val VIEWBOX = 16f

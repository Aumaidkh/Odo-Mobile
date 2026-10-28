package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import kotlin.math.cos
import kotlin.math.sin

/** One coloured stretch of an [OdoTickGauge], as fractions of the dial from 0 to 1. */
data class OdoGaugeRange(val start: Float, val end: Float, val color: Color)

/**
 * The splash's dial: 37 ticks over 270°, every fourth one long. Ticks inside a [ranges]
 * entry take its colour, the rest take [tickColor]; [needle] draws a needle at that fraction.
 */
@Composable
fun OdoTickGauge(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    tickColor: Color = OdoTheme.colors.text,
    ranges: List<OdoGaugeRange> = emptyList(),
    needle: Float? = null,
    needleColor: Color = OdoTheme.colors.text,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2
            val c = center
            val unit = r / SPLASH_RADIUS
            for (i in 0 until TICKS) {
                val f = i / (TICKS - 1f)
                val long = i % 4 == 0
                val color = ranges.lastOrNull { f >= it.start - EPS && f <= it.end + EPS }?.color ?: tickColor
                val inner = (if (long) LONG_INNER else SHORT_INNER) * unit
                drawLine(
                    color = color,
                    start = c.along(f, inner),
                    end = c.along(f, r),
                    strokeWidth = (if (long) 2.5f else 1.5f) * unit,
                    cap = StrokeCap.Round,
                )
            }
            if (needle != null) {
                drawLine(needleColor, c, c.along(needle.coerceIn(0f, 1f), NEEDLE * unit), 2.5f * unit, StrokeCap.Round)
                drawCircle(needleColor, radius = 4f * unit, center = c)
            }
        }
        content()
    }
}

/** The point [radius] out from here, at gauge fraction [f]; 0 is bottom-left, 1 bottom-right. */
private fun Offset.along(f: Float, radius: Float): Offset {
    val a = (START_DEG + SWEEP_DEG * f) * (kotlin.math.PI / 180).toFloat()
    return Offset(x + radius * sin(a), y - radius * cos(a))
}

// Geometry from the splash drawable, in its 90-unit radius.
private const val TICKS = 37
private const val START_DEG = -135f
private const val SWEEP_DEG = 270f
private const val SPLASH_RADIUS = 90f
private const val LONG_INNER = 75.6f
private const val SHORT_INNER = 81f
private const val NEEDLE = 70f
private const val EPS = 0.0001f

@OdoThemePreviews
@Composable
private fun OdoTickGaugePreview() = OdoPreview {
    OdoTickGauge(
        ranges = listOf(
            OdoGaugeRange(0.55f, 0.75f, OdoTheme.colors.text),
            OdoGaugeRange(0.75f, 0.82f, OdoTheme.colors.success),
        ),
        tickColor = OdoTheme.colors.border,
        needle = 0.65f,
    )
}

package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// A currency "$" — an S-curve through a vertical bar. Stroked for a clean glyph.
val IcCurrencyDollar: ImageVector
    get() {
        if (_IcCurrencyDollar != null) return _IcCurrencyDollar!!
        _IcCurrencyDollar = ImageVector.Builder(
            name = "IcCurrencyDollar", defaultWidth = 16.dp, defaultHeight = 16.dp,
            viewportWidth = 16f, viewportHeight = 16f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8f, 1.5f)
                lineTo(8f, 14.5f)
                moveTo(11f, 4.3f)
                curveTo(11f, 2.7f, 5f, 2.5f, 5f, 5.6f)
                curveTo(5f, 8.2f, 11f, 7.9f, 11f, 10.6f)
                curveTo(11f, 13.6f, 5f, 13.4f, 5f, 11.6f)
            }
        }.build()
        return _IcCurrencyDollar!!
    }

@Suppress("ObjectPropertyName")
private var _IcCurrencyDollar: ImageVector? = null

package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Two linked rings — the twin-cylinder mark used for CNG, and a general "paired /
// endless" glyph.
val IcInfinity: ImageVector
    get() {
        if (_IcInfinity != null) return _IcInfinity!!
        _IcInfinity = ImageVector.Builder(
            name = "IcInfinity", defaultWidth = 16.dp, defaultHeight = 16.dp,
            viewportWidth = 16f, viewportHeight = 16f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.3f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3.1f, 8f)
                arcToRelative(2.5f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 5f, 0f)
                arcToRelative(2.5f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, -5f, 0f)
                close()
                moveTo(7.9f, 8f)
                arcToRelative(2.5f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 5f, 0f)
                arcToRelative(2.5f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, -5f, 0f)
                close()
            }
        }.build()
        return _IcInfinity!!
    }

@Suppress("ObjectPropertyName")
private var _IcInfinity: ImageVector? = null

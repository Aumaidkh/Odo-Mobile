package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// A lowercase information "i" — a dot above a short stem.
val IcInfo: ImageVector
    get() {
        if (_IcInfo != null) return _IcInfo!!
        _IcInfo = ImageVector.Builder(
            name = "IcInfo", defaultWidth = 16.dp, defaultHeight = 16.dp,
            viewportWidth = 16f, viewportHeight = 16f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(7f, 4f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = true, isPositiveArc = true, 2f, 0f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = true, isPositiveArc = true, -2f, 0f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(8f, 7f)
                lineTo(8f, 12.5f)
            }
        }.build()
        return _IcInfo!!
    }

@Suppress("ObjectPropertyName")
private var _IcInfo: ImageVector? = null

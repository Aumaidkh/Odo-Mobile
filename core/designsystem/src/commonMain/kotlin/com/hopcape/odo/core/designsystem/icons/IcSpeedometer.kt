package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// An odometer / speedometer gauge — a ring with a needle and a centre hub.
val IcSpeedometer: ImageVector
    get() {
        if (_IcSpeedometer != null) return _IcSpeedometer!!
        _IcSpeedometer = ImageVector.Builder(
            name = "IcSpeedometer", defaultWidth = 16.dp, defaultHeight = 16.dp,
            viewportWidth = 16f, viewportHeight = 16f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.3f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(2f, 8f)
                arcToRelative(6f, 6f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 0f)
                arcToRelative(6f, 6f, 0f, isMoreThanHalf = true, isPositiveArc = true, -12f, 0f)
                close()
                moveTo(8f, 8f)
                lineTo(11.2f, 5.4f)
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(6.9f, 8f)
                arcToRelative(1.1f, 1.1f, 0f, isMoreThanHalf = true, isPositiveArc = true, 2.2f, 0f)
                arcToRelative(1.1f, 1.1f, 0f, isMoreThanHalf = true, isPositiveArc = true, -2.2f, 0f)
                close()
            }
        }.build()
        return _IcSpeedometer!!
    }

@Suppress("ObjectPropertyName")
private var _IcSpeedometer: ImageVector? = null

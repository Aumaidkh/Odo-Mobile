package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// A car in side profile — cabin, bonnet, and two wheels. Non-zero fill so the
// wheels stay solid where they overlap the body.
val IcCar: ImageVector
    get() {
        if (_IcCar != null) return _IcCar!!
        _IcCar = ImageVector.Builder(
            name = "IcCar", defaultWidth = 16.dp, defaultHeight = 16.dp,
            viewportWidth = 16f, viewportHeight = 16f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Body: raked windscreen up to the roof, back down over the boot,
                // then a flat sill.
                moveTo(1.3f, 9.6f)
                curveTo(1.3f, 8.6f, 1.9f, 8.1f, 2.7f, 7.9f)
                lineTo(4.4f, 4.8f)
                curveTo(4.7f, 4.2f, 5.3f, 3.9f, 6.0f, 3.9f)
                lineTo(10.0f, 3.9f)
                curveTo(10.7f, 3.9f, 11.3f, 4.2f, 11.6f, 4.8f)
                lineTo(13.3f, 7.9f)
                curveTo(14.1f, 8.1f, 14.7f, 8.6f, 14.7f, 9.6f)
                lineTo(14.7f, 11.1f)
                lineTo(1.3f, 11.1f)
                close()
                // Front wheel.
                moveTo(3.2f, 11.1f)
                arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 3.0f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, -3.0f, 0f)
                close()
                // Rear wheel.
                moveTo(9.8f, 11.1f)
                arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 3.0f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, -3.0f, 0f)
                close()
            }
        }.build()
        return _IcCar!!
    }

@Suppress("ObjectPropertyName")
private var _IcCar: ImageVector? = null

package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IcMagnifier: ImageVector
    get() {
        if (_IcMagnifier != null) {
            return _IcMagnifier!!
        }
        _IcMagnifier = ImageVector.Builder(
            name = "IcMagnifier",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(11.742f, 10.344f)
                arcToRelative(6.5f, 6.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, -1.397f, 1.398f)
                horizontalLineToRelative(-0.001f)
                quadToRelative(0.044f, 0.06f, 0.098f, 0.115f)
                lineToRelative(3.85f, 3.85f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.415f, -1.414f)
                lineToRelative(-3.85f, -3.85f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.115f, -0.1f)
                close()
                moveTo(12f, 6.5f)
                arcToRelative(5.5f, 5.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, -11f, 0f)
                arcToRelative(5.5f, 5.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 11f, 0f)
            }
        }.build()

        return _IcMagnifier!!
    }

@Suppress("ObjectPropertyName")
private var _IcMagnifier: ImageVector? = null

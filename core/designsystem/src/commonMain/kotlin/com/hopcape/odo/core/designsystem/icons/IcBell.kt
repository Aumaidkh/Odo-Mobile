package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IcBell: ImageVector
    get() {
        if (_IcBell != null) {
            return _IcBell!!
        }
        _IcBell = ImageVector.Builder(
            name = "IcBell",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 16f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, -2f)
                horizontalLineTo(6f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, 2f)
                moveToRelative(0.995f, -14.901f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = true, isPositiveArc = false, -1.99f, 0f)
                arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 3f, 6f)
                curveToRelative(0f, 1.098f, -0.5f, 6f, -2f, 7f)
                horizontalLineToRelative(14f)
                curveToRelative(-1.5f, -1f, -2f, -5.902f, -2f, -7f)
                curveToRelative(0f, -2.42f, -1.72f, -4.44f, -4.005f, -4.901f)
                close()
            }
        }.build()

        return _IcBell!!
    }

@Suppress("ObjectPropertyName")
private var _IcBell: ImageVector? = null

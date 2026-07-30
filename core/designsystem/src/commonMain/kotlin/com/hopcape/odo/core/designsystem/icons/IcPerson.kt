package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IcPerson: ImageVector
    get() {
        if (_IcPerson != null) {
            return _IcPerson!!
        }
        _IcPerson = ImageVector.Builder(
            name = "IcPerson",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 8f)
                arcToRelative(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = false, 0f, -6f)
                arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 6f)
                moveToRelative(2f, -3f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, -4f, 0f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4f, 0f)
                moveToRelative(4f, 8f)
                curveToRelative(0f, 1f, -1f, 1f, -1f, 1f)
                horizontalLineTo(3f)
                reflectiveCurveToRelative(-1f, 0f, -1f, -1f)
                reflectiveCurveToRelative(1f, -4f, 6f, -4f)
                reflectiveCurveToRelative(6f, 3f, 6f, 4f)
                moveToRelative(-1f, -0.004f)
                curveToRelative(-0.001f, -0.246f, -0.154f, -0.986f, -0.832f, -1.664f)
                curveTo(11.516f, 10.68f, 10.289f, 10f, 8f, 10f)
                reflectiveCurveToRelative(-3.516f, 0.68f, -4.168f, 1.332f)
                curveToRelative(-0.678f, 0.678f, -0.83f, 1.418f, -0.832f, 1.664f)
                close()
            }
        }.build()

        return _IcPerson!!
    }

@Suppress("ObjectPropertyName")
private var _IcPerson: ImageVector? = null

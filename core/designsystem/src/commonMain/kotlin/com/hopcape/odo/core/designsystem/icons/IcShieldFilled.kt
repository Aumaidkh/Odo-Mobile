package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IcShieldFilled: ImageVector
    get() {
        if (_IcShieldFilled != null) {
            return _IcShieldFilled!!
        }
        _IcShieldFilled = ImageVector.Builder(
            name = "IcShieldFilled",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5.072f, 0.56f)
                curveTo(6.157f, 0.265f, 7.31f, 0f, 8f, 0f)
                reflectiveCurveToRelative(1.843f, 0.265f, 2.928f, 0.56f)
                curveToRelative(1.11f, 0.3f, 2.229f, 0.655f, 2.887f, 0.87f)
                arcToRelative(1.54f, 1.54f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.044f, 1.262f)
                curveToRelative(0.596f, 4.477f, -0.787f, 7.795f, -2.465f, 9.99f)
                arcToRelative(11.8f, 11.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.517f, 2.453f)
                arcToRelative(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.048f, 0.625f)
                curveToRelative(-0.28f, 0.132f, -0.581f, 0.24f, -0.829f, 0.24f)
                reflectiveCurveToRelative(-0.548f, -0.108f, -0.829f, -0.24f)
                arcToRelative(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.048f, -0.625f)
                arcToRelative(11.8f, 11.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.517f, -2.453f)
                curveTo(1.928f, 10.487f, 0.545f, 7.169f, 1.141f, 2.692f)
                arcTo(1.54f, 1.54f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.185f, 1.43f)
                arcTo(63f, 63f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5.072f, 0.56f)
            }
        }.build()

        return _IcShieldFilled!!
    }

@Suppress("ObjectPropertyName")
private var _IcShieldFilled: ImageVector? = null

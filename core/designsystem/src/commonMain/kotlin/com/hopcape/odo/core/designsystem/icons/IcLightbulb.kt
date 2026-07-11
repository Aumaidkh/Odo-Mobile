package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IcLightbulb: ImageVector
    get() {
        if (_IcLightbulb != null) {
            return _IcLightbulb!!
        }
        _IcLightbulb = ImageVector.Builder(
            name = "IcLightbulb",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(2f, 6f)
                arcToRelative(6f, 6f, 0f, isMoreThanHalf = true, isPositiveArc = true, 10.174f, 4.31f)
                curveToRelative(-0.203f, 0.196f, -0.359f, 0.4f, -0.453f, 0.619f)
                lineToRelative(-0.762f, 1.769f)
                arcTo(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 10.5f, 13f)
                horizontalLineToRelative(-5f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.46f, -0.302f)
                lineToRelative(-0.761f, -1.77f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.453f, -0.618f)
                arcTo(5.98f, 5.98f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 6f)
                close()
                moveToRelative(3f, 8.5f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.5f, -0.5f)
                horizontalLineToRelative(5f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 1f)
                lineToRelative(-0.224f, 0.447f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.894f, 0.553f)
                horizontalLineTo(6.618f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.894f, -0.553f)
                lineTo(5.5f, 15f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.5f, -0.5f)
                close()
            }
        }.build()

        return _IcLightbulb!!
    }

@Suppress("ObjectPropertyName")
private var _IcLightbulb: ImageVector? = null

package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IcGarage: ImageVector
    get() {
        if (_IcGarage != null) {
            return _IcGarage!!
        }
        _IcGarage = ImageVector.Builder(
            name = "IcGarage",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(7.293f, 1.5f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.414f, 0f)
                lineTo(11f, 3.793f)
                verticalLineTo(2.5f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.5f, -0.5f)
                horizontalLineToRelative(1f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.5f, 0.5f)
                verticalLineToRelative(3.293f)
                lineToRelative(2.354f, 2.353f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.708f, 0.708f)
                lineTo(8f, 2.207f)
                lineToRelative(-5f, 5f)
                verticalLineTo(13.5f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.5f, 0.5f)
                horizontalLineToRelative(4f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 1f)
                horizontalLineToRelative(-4f)
                arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 13.5f)
                verticalLineTo(8.207f)
                lineToRelative(-0.646f, 0.647f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, -0.708f, -0.708f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(11.886f, 9.46f)
                curveToRelative(0.18f, -0.613f, 1.048f, -0.613f, 1.229f, 0f)
                lineToRelative(0.043f, 0.148f)
                arcToRelative(0.64f, 0.64f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.921f, 0.382f)
                lineToRelative(0.136f, -0.074f)
                curveToRelative(0.561f, -0.306f, 1.175f, 0.308f, 0.87f, 0.869f)
                lineToRelative(-0.075f, 0.136f)
                arcToRelative(0.64f, 0.64f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.382f, 0.92f)
                lineToRelative(0.149f, 0.045f)
                curveToRelative(0.612f, 0.18f, 0.612f, 1.048f, 0f, 1.229f)
                lineToRelative(-0.15f, 0.043f)
                arcToRelative(0.64f, 0.64f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.38f, 0.921f)
                lineToRelative(0.074f, 0.136f)
                curveToRelative(0.305f, 0.561f, -0.309f, 1.175f, -0.87f, 0.87f)
                lineToRelative(-0.136f, -0.075f)
                arcToRelative(0.64f, 0.64f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.92f, 0.382f)
                lineToRelative(-0.045f, 0.149f)
                curveToRelative(-0.18f, 0.612f, -1.048f, 0.612f, -1.229f, 0f)
                lineToRelative(-0.043f, -0.15f)
                arcToRelative(0.64f, 0.64f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.921f, -0.38f)
                lineToRelative(-0.136f, 0.074f)
                curveToRelative(-0.561f, 0.305f, -1.175f, -0.309f, -0.87f, -0.87f)
                lineToRelative(0.075f, -0.136f)
                arcToRelative(0.64f, 0.64f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.382f, -0.92f)
                lineToRelative(-0.148f, -0.044f)
                curveToRelative(-0.613f, -0.181f, -0.613f, -1.049f, 0f, -1.23f)
                lineToRelative(0.148f, -0.043f)
                arcToRelative(0.64f, 0.64f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.382f, -0.921f)
                lineToRelative(-0.074f, -0.136f)
                curveToRelative(-0.306f, -0.561f, 0.308f, -1.175f, 0.869f, -0.87f)
                lineToRelative(0.136f, 0.075f)
                arcToRelative(0.64f, 0.64f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.92f, -0.382f)
                close()
                moveTo(14f, 12.5f)
                arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, -3f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 3f, 0f)
            }
        }.build()

        return _IcGarage!!
    }

@Suppress("ObjectPropertyName")
private var _IcGarage: ImageVector? = null


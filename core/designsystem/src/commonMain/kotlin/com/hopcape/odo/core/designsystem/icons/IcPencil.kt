package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IcPencil: ImageVector
    get() {
        if (_IcPencil != null) {
            return _IcPencil!!
        }
        _IcPencil = ImageVector.Builder(
            name = "IcPencil",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12.146f, 0.146f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.708f, 0f)
                lineToRelative(3f, 3f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.708f)
                lineToRelative(-10f, 10f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.168f, 0.11f)
                lineToRelative(-5f, 2f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.65f, -0.65f)
                lineToRelative(2f, -5f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.11f, -0.168f)
                close()
                moveTo(11.207f, 2.5f)
                lineTo(13.5f, 4.793f)
                lineTo(14.793f, 3.5f)
                lineTo(12.5f, 1.207f)
                close()
                moveTo(12.793f, 5.5f)
                lineTo(10.5f, 3.207f)
                lineTo(4f, 9.707f)
                lineTo(4f, 10f)
                horizontalLineToRelative(0.5f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.5f, 0.5f)
                verticalLineToRelative(0.5f)
                horizontalLineToRelative(0.5f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.5f, 0.5f)
                verticalLineToRelative(0.5f)
                horizontalLineToRelative(0.293f)
                close()
                moveTo(3.032f, 10.675f)
                lineTo(2.926f, 10.781f)
                lineTo(1.398f, 14.602f)
                lineTo(5.219f, 13.074f)
                lineTo(5.325f, 12.968f)
                arcTo(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5f, 12.5f)
                lineTo(5f, 12f)
                horizontalLineToRelative(-0.5f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.5f, -0.5f)
                lineTo(4f, 11f)
                horizontalLineToRelative(-0.5f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.468f, -0.325f)
            }
        }.build()

        return _IcPencil!!
    }

@Suppress("ObjectPropertyName")
private var _IcPencil: ImageVector? = null

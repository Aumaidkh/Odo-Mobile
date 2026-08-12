package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IcTrash: ImageVector
    get() {
        if (_IcTrash != null) {
            return _IcTrash!!
        }
        _IcTrash = ImageVector.Builder(
            name = "IcTrash",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(6.5f, 1f)
                horizontalLineToRelative(3f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.5f, 0.5f)
                verticalLineToRelative(1f)
                horizontalLineTo(6f)
                verticalLineToRelative(-1f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.5f, -0.5f)
                moveTo(11f, 2.5f)
                verticalLineToRelative(-1f)
                arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 9.5f, 0f)
                horizontalLineToRelative(-3f)
                arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 5f, 1.5f)
                verticalLineToRelative(1f)
                horizontalLineTo(1.5f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 1f)
                horizontalLineToRelative(0.538f)
                lineToRelative(0.853f, 10.66f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4.885f, 16f)
                horizontalLineToRelative(6.23f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.994f, -1.84f)
                lineToRelative(0.853f, -10.66f)
                horizontalLineToRelative(0.538f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -1f)
                close()
                moveToRelative(1.958f, 1f)
                lineToRelative(-0.846f, 10.58f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.997f, 0.92f)
                horizontalLineToRelative(-6.23f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.997f, -0.92f)
                lineTo(3.042f, 3.5f)
                close()
            }
        }.build()

        return _IcTrash!!
    }

@Suppress("ObjectPropertyName")
private var _IcTrash: ImageVector? = null

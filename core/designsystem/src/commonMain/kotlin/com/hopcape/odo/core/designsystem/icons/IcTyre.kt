package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// A tyre / target — an outer ring around a centre dot.
val IcTyre: ImageVector
    get() {
        if (_IcTyre != null) return _IcTyre!!
        _IcTyre = ImageVector.Builder(
            name = "IcTyre", defaultWidth = 16.dp, defaultHeight = 16.dp,
            viewportWidth = 16f, viewportHeight = 16f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(1f, 8f)
                arcToRelative(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = true, 14f, 0f)
                arcToRelative(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = true, -14f, 0f)
                close()
                moveTo(3.5f, 8f)
                arcToRelative(4.5f, 4.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, 9f, 0f)
                arcToRelative(4.5f, 4.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, -9f, 0f)
                close()
                moveTo(6.5f, 8f)
                arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 3f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, -3f, 0f)
                close()
            }
        }.build()
        return _IcTyre!!
    }

@Suppress("ObjectPropertyName")
private var _IcTyre: ImageVector? = null

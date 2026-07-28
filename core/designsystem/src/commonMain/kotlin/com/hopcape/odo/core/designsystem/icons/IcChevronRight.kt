package com.hopcape.odo.core.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IcChevronRight: ImageVector
    get() {
        if (_IcChevronRight != null) {
            return _IcChevronRight!!
        }
        _IcChevronRight = ImageVector.Builder(
            name = "IcChevronRight",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd
            ) {
                moveTo(4.646f, 1.646f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.708f, 0f)
                lineToRelative(6f, 6f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.708f)
                lineToRelative(-6f, 6f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.708f, -0.708f)
                lineTo(10.293f, 8f)
                lineTo(4.646f, 2.354f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.708f)
            }
        }.build()

        return _IcChevronRight!!
    }

@Suppress("ObjectPropertyName")
private var _IcChevronRight: ImageVector? = null

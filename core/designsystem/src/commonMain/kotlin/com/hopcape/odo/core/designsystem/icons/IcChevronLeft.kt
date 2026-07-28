package com.hopcape.odo.core.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IcChevronLeft: ImageVector
    get() {
        if (_IcChevronLeft != null) {
            return _IcChevronLeft!!
        }
        _IcChevronLeft = ImageVector.Builder(
            name = "IcChevronLeft",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd
            ) {
                moveTo(11.354f, 1.646f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.708f)
                lineTo(5.707f, 8f)
                lineToRelative(5.647f, 5.646f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.708f, 0.708f)
                lineToRelative(-6f, -6f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.708f)
                lineToRelative(6f, -6f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.708f, 0f)
            }
        }.build()

        return _IcChevronLeft!!
    }

@Suppress("ObjectPropertyName")
private var _IcChevronLeft: ImageVector? = null

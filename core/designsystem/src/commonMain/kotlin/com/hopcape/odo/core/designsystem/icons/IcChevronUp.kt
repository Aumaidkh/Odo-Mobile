package com.hopcape.odo.core.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IcChevronUp: ImageVector
    get() {
        if (_IcChevronUp != null) {
            return _IcChevronUp!!
        }
        _IcChevronUp = ImageVector.Builder(
            name = "IcChevronUp",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd
            ) {
                moveTo(7.646f, 4.646f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.708f, 0f)
                lineToRelative(6f, 6f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.708f, 0.708f)
                lineTo(8f, 5.707f)
                lineToRelative(-5.646f, 5.647f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.708f, -0.708f)
                close()
            }
        }.build()

        return _IcChevronUp!!
    }

@Suppress("ObjectPropertyName")
private var _IcChevronUp: ImageVector? = null

package com.hopcape.odo.core.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IcArrowLeft: ImageVector
    get() {
        if (_IcArrowLeft != null) {
            return _IcArrowLeft!!
        }
        _IcArrowLeft = ImageVector.Builder(
            name = "IcArrowLeft",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd
            ) {
                moveTo(15f, 8f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.5f, -0.5f)
                horizontalLineTo(2.707f)
                lineToRelative(3.147f, -3.146f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, -0.708f, -0.708f)
                lineToRelative(-4f, 4f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.708f)
                lineToRelative(4f, 4f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.708f, -0.708f)
                lineTo(2.707f, 8.5f)
                horizontalLineTo(14.5f)
                arcTo(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 15f, 8f)
            }
        }.build()

        return _IcArrowLeft!!
    }

@Suppress("ObjectPropertyName")
private var _IcArrowLeft: ImageVector? = null

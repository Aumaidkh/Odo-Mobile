package com.hopcape.odo.core.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IcCheck: ImageVector
    get() {
        if (_IcCheck != null) {
            return _IcCheck!!
        }
        _IcCheck = ImageVector.Builder(
            name = "IcCheck",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(13.854f, 3.646f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.708f)
                lineToRelative(-7f, 7f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.708f, 0f)
                lineToRelative(-3.5f, -3.5f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 0.708f, -0.708f)
                lineTo(6.5f, 10.293f)
                lineToRelative(6.646f, -6.647f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.708f, 0f)
            }
        }.build()

        return _IcCheck!!
    }

@Suppress("ObjectPropertyName")
private var _IcCheck: ImageVector? = null

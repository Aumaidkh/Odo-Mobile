package com.hopcape.odo.core.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The counterpart of [IcCheck]: a row that is deliberately not ticked. */
val IcMinus: ImageVector
    get() {
        if (_IcMinus != null) {
            return _IcMinus!!
        }
        _IcMinus = ImageVector.Builder(
            name = "IcMinus",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 8f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.5f, -0.5f)
                horizontalLineToRelative(9f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 1f)
                horizontalLineToRelative(-9f)
                arcTo(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, 8f)
            }
        }.build()

        return _IcMinus!!
    }

@Suppress("ObjectPropertyName")
private var _IcMinus: ImageVector? = null

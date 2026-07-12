package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// A calendar — a rounded frame with a header bar (Bootstrap "calendar3").
val IcCalendar: ImageVector
    get() {
        if (_IcCalendar != null) return _IcCalendar!!
        _IcCalendar = ImageVector.Builder(
            name = "IcCalendar", defaultWidth = 16.dp, defaultHeight = 16.dp,
            viewportWidth = 16f, viewportHeight = 16f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(14f, 0f)
                horizontalLineTo(2f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2f, 2f)
                verticalLineToRelative(12f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, 2f)
                horizontalLineToRelative(12f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, -2f)
                verticalLineTo(2f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2f, -2f)
                moveTo(1f, 3.857f)
                curveTo(1f, 3.384f, 1.448f, 3f, 2f, 3f)
                horizontalLineToRelative(12f)
                curveToRelative(0.552f, 0f, 1f, 0.384f, 1f, 0.857f)
                verticalLineTo(14f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1f, 1f)
                horizontalLineTo(2f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1f, -1f)
                close()
            }
        }.build()
        return _IcCalendar!!
    }

@Suppress("ObjectPropertyName")
private var _IcCalendar: ImageVector? = null

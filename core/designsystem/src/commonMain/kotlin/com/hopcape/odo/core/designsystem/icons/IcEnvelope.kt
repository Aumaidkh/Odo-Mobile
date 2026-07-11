package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IcEnvelope: ImageVector
    get() {
        if (_IcEnvelope != null) {
            return _IcEnvelope!!
        }
        _IcEnvelope = ImageVector.Builder(
            name = "IcEnvelope",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(0f, 4f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, -2f)
                horizontalLineToRelative(12f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 2f)
                verticalLineToRelative(8f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 2f)
                lineTo(2f, 14f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, -2f)
                close()
                moveTo(2f, 3f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1f, 1f)
                verticalLineToRelative(0.217f)
                lineToRelative(7f, 4.2f)
                lineToRelative(7f, -4.2f)
                lineTo(15f, 4f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1f, -1f)
                close()
                moveTo(15f, 5.383f)
                lineTo(10.292f, 8.208f)
                lineTo(15f, 11.105f)
                close()
                moveTo(14.966f, 12.259f)
                lineTo(9.326f, 8.788f)
                lineTo(8f, 9.583f)
                lineToRelative(-1.326f, -0.795f)
                lineToRelative(-5.64f, 3.47f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, 13f)
                horizontalLineToRelative(12f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.966f, -0.741f)
                moveTo(1f, 11.105f)
                lineToRelative(4.708f, -2.897f)
                lineTo(1f, 5.383f)
                close()
            }
        }.build()

        return _IcEnvelope!!
    }

@Suppress("ObjectPropertyName")
private var _IcEnvelope: ImageVector? = null


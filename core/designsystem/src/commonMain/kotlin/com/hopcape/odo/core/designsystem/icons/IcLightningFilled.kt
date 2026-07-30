package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IcLightningFilled: ImageVector
    get() {
        if (_IcLightningFilled != null) {
            return _IcLightningFilled!!
        }
        _IcLightningFilled = ImageVector.Builder(
            name = "IcLightningFilled",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(11.251f, 0.068f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.227f, 0.58f)
                lineTo(9.677f, 6.5f)
                horizontalLineTo(13f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.364f, 0.843f)
                lineToRelative(-8f, 8.5f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.842f, -0.49f)
                lineTo(6.323f, 9.5f)
                horizontalLineTo(3f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.364f, -0.843f)
                lineToRelative(8f, -8.5f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.615f, -0.09f)
                close()
            }
        }.build()

        return _IcLightningFilled!!
    }

@Suppress("ObjectPropertyName")
private var _IcLightningFilled: ImageVector? = null

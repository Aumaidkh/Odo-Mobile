package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IcLink: ImageVector
    get() {
        if (_IcLink != null) {
            return _IcLink!!
        }
        _IcLink = ImageVector.Builder(
            name = "IcLink",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4.715f, 6.542f)
                lineTo(3.343f, 7.914f)
                arcToRelative(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = false, 4.243f, 4.243f)
                lineToRelative(1.828f, -1.829f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 8.586f, 5.5f)
                lineTo(8f, 6.086f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.154f, 0.199f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.861f, 3.337f)
                lineTo(6.88f, 11.45f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, -2.83f, -2.83f)
                lineToRelative(0.793f, -0.792f)
                arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.128f, -1.287f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(6.586f, 4.672f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 7.414f, 9.5f)
                lineToRelative(0.775f, -0.776f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.896f, -3.346f)
                lineTo(9.12f, 3.55f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 2.83f, 2.83f)
                lineToRelative(-0.793f, 0.792f)
                curveToRelative(0.112f, 0.42f, 0.155f, 0.855f, 0.128f, 1.287f)
                lineToRelative(1.372f, -1.372f)
                arcToRelative(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = false, -4.243f, -4.243f)
                close()
            }
        }.build()

        return _IcLink!!
    }

@Suppress("ObjectPropertyName")
private var _IcLink: ImageVector? = null


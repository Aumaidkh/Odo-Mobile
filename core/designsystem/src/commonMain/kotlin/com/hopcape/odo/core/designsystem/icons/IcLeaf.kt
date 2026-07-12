package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IcLeaf: ImageVector
    get() {
        if (_IcLeaf != null) return _IcLeaf!!
        _IcLeaf = ImageVector.Builder(
            name = "IcLeaf", defaultWidth = 16.dp, defaultHeight = 16.dp,
            viewportWidth = 16f, viewportHeight = 16f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 13f)
                curveTo(3f, 6f, 8f, 2f, 13f, 3f)
                curveTo(12f, 9f, 8f, 13f, 3f, 13f)
                close()
            }
        }.build()
        return _IcLeaf!!
    }

@Suppress("ObjectPropertyName")
private var _IcLeaf: ImageVector? = null

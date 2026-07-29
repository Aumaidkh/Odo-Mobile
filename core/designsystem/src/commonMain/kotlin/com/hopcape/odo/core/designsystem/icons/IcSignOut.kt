package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// A sign-out / log-out glyph — an open door bracket with an arrow leaving to the right.
val IcSignOut: ImageVector
    get() {
        if (_IcSignOut != null) return _IcSignOut!!
        _IcSignOut = ImageVector.Builder(
            name = "IcSignOut", defaultWidth = 16.dp, defaultHeight = 16.dp,
            viewportWidth = 16f, viewportHeight = 16f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.3f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                // Door bracket (left) — open on the right.
                moveTo(6.5f, 3.2f)
                lineTo(3.5f, 3.2f)
                lineTo(3.5f, 12.8f)
                lineTo(6.5f, 12.8f)
                // Arrow leaving to the right.
                moveTo(7f, 8f)
                lineTo(13f, 8f)
                moveTo(10.5f, 5.5f)
                lineTo(13f, 8f)
                lineTo(10.5f, 10.5f)
            }
        }.build()
        return _IcSignOut!!
    }

@Suppress("ObjectPropertyName")
private var _IcSignOut: ImageVector? = null

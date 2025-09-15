package dev.sajidali.guide

import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

@Composable
internal fun rememberTextPaint(
    color: Color = Color.Black,
    textSize: TextUnit = 12.sp,
    density: Density = Density(1f)
): Paint {
    return remember {
        Paint().apply {
            this.color = color.toArgb()
            this.textSize = with(density) { textSize.toPx() }
        }
    }
}

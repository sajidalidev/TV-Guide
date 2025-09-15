package dev.sajidali.guide

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.joda.time.LocalDateTime

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.joda.time.format.DateTimeFormat

@Composable
internal fun Timeline(
    state: ProgramGuideState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onTimeForNow: (LocalDateTime) -> Boolean
) {
    val timelineFormatter = remember { DateTimeFormat.shortTime() }
    val density = LocalDensity.current
    val textPaint = rememberTextPaint(
        color = MaterialTheme.colorScheme.onSurface,
        textSize = 12.sp,
        density = density
    )

    Canvas(
        modifier = modifier.padding(contentPadding)
    ) {
        val timelineIntervals = state.getTimelineIntervals()
        timelineIntervals.forEach { time ->
            val (x, y) = state.getTimelinePosition(time)
            drawIntoCanvas {
                it.nativeCanvas.drawText(
                    timelineFormatter.print(time),
                    x,
                    y,
                    textPaint
                )
            }
        }
    }

    NowLine(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        onTimeForNow = onTimeForNow
    )
}

@Composable
internal fun NowLine(
    state: ProgramGuideState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onTimeForNow: (LocalDateTime) -> Boolean,
    color: Color = Color.Red,
    width: Float = 4f
) {
    val now = LocalDateTime.now()
    if (onTimeForNow(now)) {
        Canvas(
            modifier = modifier
                .padding(top = contentPadding.calculateTopPadding() + state.timeBarHeight)
                .fillMaxHeight()
        ) {
            val (x, _) = state.getTimelinePosition(now)
            drawLine(
                color = color,
                startX = x,
                endX = x,
                startY = 0f,
                endY = size.height,
                strokeWidth = width
            )
        }
    }
}

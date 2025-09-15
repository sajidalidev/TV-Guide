package dev.sajidali.guide

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.sajidali.guide.data.ProgramGuideItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.joda.time.Duration
import org.joda.time.LocalDateTime
import kotlin.math.roundToInt

@Composable
internal fun rememberProgramGuideState(): ProgramGuideState {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    return remember {
        ProgramGuideState(
            coroutineScope = coroutineScope,
            density = density
        )
    }
}

internal class ProgramGuideState(
    private val coroutineScope: CoroutineScope,
    private val density: Density
) {
    val verticalScrollState = ScrollableState {
        coroutineScope.launch {
            verticalScrollOffset -= it
        }
        it
    }
    val horizontalScrollState = ScrollableState {
        coroutineScope.launch {
            horizontalScrollOffset -= it
        }
        it
    }

    var verticalScrollOffset = 0f
    var horizontalScrollOffset = 0f

    var layoutWidth = 0
    var layoutHeight = 0

    val timeBarHeight = with(density) { 48.dp.toPx() }
    val channelWidth = with(density) { 100.dp.toPx() }

    private var layoutInfo: ProgramGuideLayoutInfo? = null

    val visibleChannels: List<VisibleItem.Channel>
        get() = layoutInfo?.visibleChannels ?: emptyList()
    val visiblePrograms: List<VisibleItem.Program>
        get() = layoutInfo?.visiblePrograms ?: emptyList()

    fun update(
        constraints: Constraints,
        itemProvider: ProgramGuideItemProvider,
        contentPadding: PaddingValues
    ) {
        layoutInfo = getLayoutInfo(constraints, itemProvider, contentPadding)
        layoutWidth = getLayoutWidth(layoutInfo!!)
        layoutHeight = getLayoutHeight(layoutInfo!!)
    }

    fun getLayoutInfo(
        constraints: Constraints,
        itemProvider: ProgramGuideItemProvider,
        contentPadding: PaddingValues
    ): ProgramGuideLayoutInfo {
        return ProgramGuideLayoutInfo(
            constraints = constraints,
            contentPadding = contentPadding,
            itemProvider = itemProvider,
            verticalScrollOffset = verticalScrollOffset,
            horizontalScrollOffset = horizontalScrollOffset,
            density = density
        )
    }

    fun getLayoutWidth(layoutInfo: ProgramGuideLayoutInfo): Int {
        return layoutInfo.totalWidth.roundToInt()
    }

    fun getLayoutHeight(layoutInfo: ProgramGuideLayoutInfo): Int {
        return layoutInfo.totalHeight.roundToInt()
    }

    fun getTimelineIntervals(): List<LocalDateTime> {
        val now = LocalDateTime.now()
        val start = now.withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0)
        val intervals = mutableListOf<LocalDateTime>()
        for (i in 0..24) {
            intervals.add(start.plusHours(i))
        }
        return intervals
    }

    fun getTimelinePosition(time: LocalDateTime): Pair<Float, Float> {
        val x = (time.toDateTime().millis - horizontalScrollOffset) / 1000f
        return Pair(x, timeBarHeight)
    }

    suspend fun scrollToTime(time: LocalDateTime) {
        val x = (time.toDateTime().millis) / 1000f
        horizontalScrollState.scrollBy(x)
    }

    suspend fun scrollBy(x: Float, y: Float) {
        horizontalScrollState.scrollBy(x)
        verticalScrollState.scrollBy(y)
    }
}

internal data class ProgramGuideLayoutInfo(
    val constraints: Constraints,
    val contentPadding: PaddingValues,
    val itemProvider: ProgramGuideItemProvider,
    val verticalScrollOffset: Float,
    val horizontalScrollOffset: Float,
    val density: Density
) {
    val visibleChannels = mutableListOf<VisibleItem.Channel>()
    val visiblePrograms = mutableListOf<VisibleItem.Program>()
    val totalWidth: Float
    val totalHeight: Float

    init {
        val channelHeight = with(density) { 100.dp.toPx() }
        val channelWidth = with(density) { 100.dp.toPx() }
        val programHeight = channelHeight
        val timeBarHeight = with(density) { 48.dp.toPx() }
        val startMillis = LocalDateTime.now().withTime(0,0,0,0).toDateTime().millis

        var currentX = contentPadding.calculateLeftPadding(density).toPx()
        var currentY = contentPadding.calculateTopPadding(density).toPx() + timeBarHeight

        val visibleHeight = constraints.maxHeight - currentY
        val visibleWidth = constraints.maxWidth - currentX

        val firstVisibleChannel = (verticalScrollOffset / channelHeight).toInt()
        val lastVisibleChannel =
            ((verticalScrollOffset + visibleHeight) / channelHeight).toInt()

        for (channelIndex in firstVisibleChannel..lastVisibleChannel) {
            val channelItem = itemProvider.programGuideItems.find {
                it is ProgramGuideItem.Channel && it.channelIndex == channelIndex
            } as? ProgramGuideItem.Channel ?: continue

            val channelX = currentX
            val channelY = currentY + channelIndex * channelHeight - verticalScrollOffset
            visibleChannels.add(
                VisibleItem.Channel(
                    channel = channelItem,
                    x = channelX.roundToInt(),
                    y = channelY.roundToInt(),
                    constraints = Constraints.fixed(
                        channelWidth.roundToInt(),
                        channelHeight.roundToInt()
                    )
                )
            )

            val programs = itemProvider.programGuideItems.filter {
                it is ProgramGuideItem.Program && it.channelIndex == channelIndex
            }
            for (programItem in programs) {
                programItem as ProgramGuideItem.Program
                val programWidth =
                    Duration(programItem.start, programItem.end).standardMinutes * 20
                val programX =
                    currentX + channelWidth + (programItem.start - startMillis) / 1000 - horizontalScrollOffset
                val programY = channelY
                if (programX + programWidth > currentX + channelWidth && programX < visibleWidth) {
                    visiblePrograms.add(
                        VisibleItem.Program(
                            program = programItem,
                            x = programX.roundToInt(),
                            y = programY.roundToInt(),
                            constraints = Constraints.fixed(
                                programWidth.toInt(),
                                programHeight.roundToInt()
                            )
                        )
                    )
                }
            }
        }
        totalWidth = visibleWidth
        totalHeight = visibleHeight
    }
}

internal sealed class VisibleItem {
    data class Channel(
        val channel: ProgramGuideItem.Channel,
        val x: Int,
        val y: Int,
        val constraints: Constraints
    ) : VisibleItem()

    data class Program(
        val program: ProgramGuideItem.Program,
        val x: Int,
        val y: Int,
        val constraints: Constraints
    ) : VisibleItem()
}

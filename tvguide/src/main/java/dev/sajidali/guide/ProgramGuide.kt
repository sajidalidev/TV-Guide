package dev.sajidali.guide

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.sajidali.guide.data.DataProvider

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.material.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.sajidali.guide.data.DataProvider
import dev.sajidali.guide.data.ProgramGuideItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.joda.time.Duration
import org.joda.time.LocalDateTime
import kotlin.math.roundToInt

@Composable
internal fun ProgramGuide(
    dataProvider: DataProvider,
    modifier: Modifier = Modifier,
    state: ProgramGuideState = rememberProgramGuideState(),
    contentPadding: PaddingValues = PaddingValues(),
    onProgramClick: (ProgramGuideItem.Program) -> Unit = {},
    onProgramFocus: (ProgramGuideItem.Program) -> Unit = {},
    onChannelClick: (ProgramGuideItem.Channel) -> Unit = {},
    onChannelFocus: (ProgramGuideItem.Channel) -> Unit = {},
) {
    val itemProvider = remember(dataProvider) {
        ProgramGuideItemProvider(dataProvider)
    }
    val coroutineScope = rememberCoroutineScope()
    val onProgramClickState = remember { onProgramClick }
    val onProgramFocusState = remember { onProgramFocus }
    val onChannelClickState = remember { onChannelClick }
    val onChannelFocusState = remember { onChannelFocus }


    LazyLayout(
        modifier = modifier
            .scrollable(
                orientation = Orientation.Vertical,
                state = state.verticalScrollState
            )
            .scrollable(
                orientation = Orientation.Horizontal,
                state = state.horizontalScrollState
            )
            .focusGroup()
            .onKeyEvent {
                if (it.key == Key.DirectionRight) {
                    coroutineScope.launch {
                        state.scrollBy(200f, 0f)
                    }
                }
                true
            },
        itemProvider = itemProvider
    ) { constraints ->
        state.update(
            constraints = constraints,
            itemProvider = itemProvider,
            contentPadding = contentPadding
        )
        val layoutWidth = state.layoutWidth
        val layoutHeight = state.layoutHeight

        layout(layoutWidth, layoutHeight) {
            state.visibleChannels.forEach {
                val placeables = measure(
                    itemIndex = it.channel.index,
                    constraints = it.constraints
                )
                placeables.first().place(it.x, it.y)
            }
            state.visiblePrograms.forEach {
                val placeables = measure(
                    itemIndex = it.program.itemIndex,
                    constraints = it.constraints
                )
                placeables.first().place(it.x, it.y)
            }
        }
    }
}


@Composable
fun Program(
    program: ProgramGuideItem.Program,
    modifier: Modifier = Modifier,
    onClick: (ProgramGuideItem.Program) -> Unit = {},
    onFocus: (ProgramGuideItem.Program) -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val backgroundColor = if (isFocused) MaterialTheme.colorScheme.secondary else Color.White
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .border(1.dp, Color.Black)
            .padding(8.dp)
            .onFocusChanged {
                if (it.isFocused) {
                    onFocus(program)
                }
            }
            .clickable {
                onClick(program)
            }
            .focusable(interactionSource = interactionSource)
    ) {
        Text(text = program.title, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
fun Channel(
    channel: ProgramGuideItem.Channel,
    modifier: Modifier = Modifier,
    onClick: (ProgramGuideItem.Channel) -> Unit = {},
    onFocus: (ProgramGuideItem.Channel) -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val backgroundColor = if (isFocused) MaterialTheme.colorScheme.primary else Color.White
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .border(1.dp, Color.Black)
            .padding(8.dp)
            .onFocusChanged {
                if (it.isFocused) {
                    onFocus(channel)
                }
            }
            .clickable {
                onClick(channel)
            }
            .focusable(interactionSource = interactionSource)
    ) {
        Text(text = channel.title, modifier = Modifier.align(Alignment.Center))
    }
}

internal class ProgramGuideItemProvider(
    private val dataProvider: DataProvider
) : LazyLayoutItemProvider {
    override val itemCount: Int
        get() = dataProvider.size() + dataProvider.eventsCount

    val programGuideItems by lazy {
        dataProvider.mapToProgramGuide()
    }

    @Composable
    override fun Item(index: Int) {
        when (val item = programGuideItems[index]) {
            is ProgramGuideItem.Channel -> {
                Channel(
                    channel = item,
                    modifier = Modifier.focusRequester(FocusRequester())
                )
            }

            is ProgramGuideItem.Program -> {
                Program(
                    program = item,
                    modifier = Modifier.focusRequester(FocusRequester())
                )
            }
        }
    }
}


internal fun DataProvider.mapToProgramGuide(): List<ProgramGuideItem> {
    val items = mutableListOf<ProgramGuideItem>()
    var itemIndex = 0
    for (channelIndex in 0 until size()) {
        val channel = channelAt(channelIndex)!!
        items.add(
            ProgramGuideItem.Channel(
                index = itemIndex++,
                channelIndex = channelIndex,
                title = channel.title,
                logo = channel.icon
            )
        )
        val events = eventsOfChannel(channelIndex)
        for (event in events) {
            items.add(
                ProgramGuideItem.Program(
                    itemIndex = itemIndex++,
                    channelIndex = channelIndex,
                    title = event.title,
                    description = event.description,
                    start = event.start,
                    end = event.end
                )
            )
        }
    }
    return items
}

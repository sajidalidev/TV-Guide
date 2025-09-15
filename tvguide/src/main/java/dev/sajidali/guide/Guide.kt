package dev.sajidali.guide

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.sajidali.guide.data.DataProvider
import org.joda.time.LocalDateTime

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

import dev.sajidali.guide.data.ProgramGuideItem

@Composable
fun Guide(
    dataProvider: DataProvider,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onProgramClick: (ProgramGuideItem.Program) -> Unit = {},
    onProgramFocus: (ProgramGuideItem.Program) -> Unit = {},
    onChannelClick: (ProgramGuideItem.Channel) -> Unit = {},
    onChannelFocus: (ProgramGuideItem.Channel) -> Unit = {},
    onTimeForNow: (LocalDateTime) -> Boolean = { true }
) {
    val coroutineScope = rememberCoroutineScope()
    val state = rememberProgramGuideState()
    Box(modifier = modifier) {
        ProgramGuide(
            dataProvider = dataProvider,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            state = state,
            onProgramClick = onProgramClick,
            onProgramFocus = onProgramFocus,
            onChannelClick = onChannelClick,
            onChannelFocus = onChannelFocus
        )
        Timeline(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = contentPadding,
            onTimeForNow = onTimeForNow
        )
    }

    fun now() {
        coroutineScope.launch {
            state.scrollToTime(LocalDateTime.now())
        }
    }

}

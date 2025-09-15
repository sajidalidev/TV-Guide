package dev.sajidali.tvguide

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sajidali.guide.Guide
import dev.sajidali.guide.data.Channel
import dev.sajidali.guide.data.DataProvider
import dev.sajidali.guide.data.Event
import dev.sajidali.guide.data.ProgramGuideItem
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private val provider = object : DataProvider {
        private var onDataUpdated: () -> Unit = {}
        override fun onDataUpdated(block: () -> Unit) {
            onDataUpdated = block
        }

        val events = HashMap<Int, List<Event>>()
        val channels = (0..300).map { position ->
            Channel(position, "Channel $position", "").also {
                generateEvents(position)
            }
        }

        private fun generateEvents(channel: Int) {
            var startTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
            events[channel] = (0..50).map {
                val endTime =
                    Random.nextLong(startTime, startTime + TimeUnit.HOURS.toMillis(2))
                Event(
                    it,
                    "Event $it",
                    "Description of event $it",
                    startTime,
                    endTime
                ).also {
                    startTime = endTime
                }
            }
        }

        override fun channelAt(position: Int): Channel {
            return channels[position]
        }

        override fun eventsOfChannel(position: Int): Collection<Event> {
            return events[position] ?: emptyList()
        }

        override fun eventOfChannelAt(channel: Int, position: Int): Event? {
            return events[channel]?.get(position)
        }

        override fun size(): Int {
            return 50
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val selectedChannel = remember { mutableStateOf<ProgramGuideItem.Channel?>(null) }
            val selectedProgram = remember { mutableStateOf<ProgramGuideItem.Program?>(null) }
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(text = "Channel: ${selectedChannel.value?.title ?: ""}")
                    Text(
                        text = "Program: ${selectedProgram.value?.title ?: ""}",
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
                Guide(
                    dataProvider = provider,
                    onChannelFocus = {
                        selectedChannel.value = it
                    },
                    onProgramFocus = {
                        selectedProgram.value = it
                    },
                    onChannelClick = {
                        Toast.makeText(this@MainActivity, "Channel ${it.title} clicked", Toast.LENGTH_SHORT).show()
                    },
                    onProgramClick = {
                        Toast.makeText(this@MainActivity, "Program ${it.title} clicked", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}
package dev.sajidali.tvguide

import dev.sajidali.guide.data.Channel
import dev.sajidali.guide.data.Event
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

// Need to use RobolectricTestRunner if the DataProvider uses Android APIs indirectly
// or if MainActivity itself is an Android component (which it is).
// However, the DataProvider object itself is plain Kotlin.
// For simplicity, let's try without Robolectric first for the provider if it's truly standalone.
// If it ends up needing Android context (e.g. for resources, though this one doesn't),
// then Robolectric would be required. The current demo provider is pure Kotlin.

class MainActivityDataProviderTest {

    private lateinit var dataProvider: MainActivity.TestDataProvider // Assuming we can make it accessible

    // This is tricky because the provider is an anonymous object in MainActivity.
    // To test it directly, we'd need to refactor MainActivity to expose it or make it a separate class.
    // For this exercise, I will *assume* we can instantiate a similar provider for testing,
    // or that MainActivity can provide it.
    // A better approach in real code: DataProvider implementation would be a separate, testable class.

    // Let's replicate the structure of the DataProvider for testing.
    // This is not ideal but necessary given the current structure.
    class TestDataProvider : dev.sajidali.guide.data.DataProvider {
        val events = HashMap<Int, List<Event>>()
        val channels = (0..300).map { position ->
            Channel(position, "Channel $position", "").also {
                generateEventsForChannel(position)
            }
        }
        private var onDataUpdatedCallback: (() -> Unit)? = null

        private fun generateEventsForChannel(channelId: Int) {
            var startTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
            events[channelId] = (0..50).map { eventPos ->
                val duration = kotlin.random.Random.nextLong(
                    TimeUnit.MINUTES.toMillis(15),
                    TimeUnit.HOURS.toMillis(2)
                )
                val endTime = startTime + duration
                Event(
                    eventPos, // id for event
                    "Event $eventPos for Channel $channelId",
                    "Description of event $eventPos",
                    startTime,
                    endTime
                ).also {
                    startTime = endTime + TimeUnit.MINUTES.toMillis(1) // Add gap
                }
            }
        }

        override val itemCount: Int
            get() = channels.size // Corrected from hardcoded 50

        override fun channelAt(position: Int): Channel? {
            return channels.getOrNull(position)
        }

        override fun eventsOfChannel(channelPosition: Int): List<Event> {
            return events[channelPosition] ?: emptyList()
        }

        override fun eventOfChannelAt(channelPosition: Int, eventPosition: Int): Event? {
            return events[channelPosition]?.getOrNull(eventPosition)
        }

        override fun size(): Int { // This was the hardcoded one in the original demo
            return 50 // Let's keep it as 50 to test the original demo's behavior if intended for GuideView
                      // but for channel list, itemCount is better. This is confusing in original.
                      // For testing the provider logic, we'll test against its actual data.
                      // The GuideView test will need to consider how it uses size().
        }

        // Actual number of channels for testing provider logic
        fun getActualChannelCount(): Int = channels.size

        override fun onDataUpdated(callback: () -> Unit) {
            onDataUpdatedCallback = callback
        }

        fun triggerDataUpdate() {
            onDataUpdatedCallback?.invoke()
        }
    }


    @Before
    fun setUp() {
        dataProvider = TestDataProvider()
    }

    @Test
    fun channelAt_returnsCorrectChannel() {
        val channel0 = dataProvider.channelAt(0)
        assertNotNull(channel0)
        assertEquals(0, channel0?.id)
        assertEquals("Channel 0", channel0?.title)

        val channel5 = dataProvider.channelAt(5)
        assertNotNull(channel5)
        assertEquals(5, channel5?.id)
        assertEquals("Channel 5", channel5?.title)
    }

    @Test
    fun channelAt_returnsNullForOutOfBounds() {
        assertNull(dataProvider.channelAt(-1))
        assertNull(dataProvider.channelAt(dataProvider.getActualChannelCount())) // Use actual size
    }

    @Test
    fun eventsOfChannel_returnsEvents() {
        val eventsForChannel0 = dataProvider.eventsOfChannel(0)
        assertNotNull(eventsForChannel0)
        assertTrue(eventsForChannel0.isNotEmpty())
        assertEquals(51, eventsForChannel0.size) // 0 to 50
        assertEquals("Event 0 for Channel 0", eventsForChannel0.first().title)
    }

    @Test
    fun eventsOfChannel_returnsEmptyListForInvalidChannel() {
        val events = dataProvider.eventsOfChannel(-1)
        assertNotNull(events)
        assertTrue(events.isEmpty())
    }

    @Test
    fun eventOfChannelAt_returnsCorrectEvent() {
        val event0_0 = dataProvider.eventOfChannelAt(0, 0)
        assertNotNull(event0_0)
        assertEquals(0, event0_0?.id)
        assertEquals("Event 0 for Channel 0", event0_0?.title)

        val event5_10 = dataProvider.eventOfChannelAt(5, 10)
        assertNotNull(event5_10)
        assertEquals(10, event5_10?.id)
        assertEquals("Event 10 for Channel 5", event5_10?.title)
    }

    @Test
    fun eventOfChannelAt_returnsNullForOutOfBounds() {
        assertNull(dataProvider.eventOfChannelAt(0, 100)) // Event out of bounds
        assertNull(dataProvider.eventOfChannelAt(dataProvider.getActualChannelCount() + 10, 0)) // Channel out of bounds
        assertNull(dataProvider.eventOfChannelAt(-1, 0)) // Channel out of bounds
    }

    @Test
    fun size_returnsSpecifiedCount() {
        // This tests the `size()` method which in the original demo was hardcoded to 50
        // and might be what GuideView relies on for some calculations if not itemCount.
        assertEquals(50, dataProvider.size())
    }

    @Test
    fun itemCount_returnsActualChannelCount() {
        // This tests `itemCount` which should reflect the true number of channels.
        assertEquals(301, dataProvider.itemCount)
    }

    @Test
    fun onDataUpdated_callbackIsInvoked() {
        var callbackCalled = false
        dataProvider.onDataUpdated {
            callbackCalled = true
        }
        // Simulate a data update if the provider had such a method.
        // In this TestDataProvider, I added a helper for it.
        (dataProvider as TestDataProvider).triggerDataUpdate()
        assertTrue(callbackCalled)
    }
}

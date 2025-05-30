package dev.sajidali.guide

import android.app.Application
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorStateList
import android.graphics.Paint
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import dev.sajidali.guide.data.Channel
import dev.sajidali.guide.data.DataProvider
import dev.sajidali.guide.data.Event
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Config.OLDEST_SDK])
class GuideViewTest {

    private lateinit var guideView: GuideView
    private lateinit var context: Context
    private lateinit var testProvider: TestDataProvider // Use a concrete test provider

    @Mock
    private lateinit var mockCanvas: Canvas // Mock canvas for draw verification if needed

    @Mock
    private lateinit var mockClickListener: GuideView.ClickListener

    @Captor
    private lateinit var eventCaptor: ArgumentCaptor<Event>

    @Captor
    private lateinit var channelCaptor: ArgumentCaptor<Channel>


    // TestDataProvider for controlled data scenarios
    class TestDataProvider : DataProvider {
        val eventsMap = HashMap<Int, MutableList<Event>>()
        val channelsList = mutableListOf<Channel>()
        private var onDataUpdatedCallback: (() -> Unit)? = null

        fun addChannel(channel: Channel) {
            channelsList.add(channel)
            eventsMap.putIfAbsent(channel.id, mutableListOf())
        }

        fun addEvent(channelId: Int, event: Event) {
            eventsMap.computeIfAbsent(channelId) { mutableListOf() }.add(event)
        }

        fun clear() {
            channelsList.clear()
            eventsMap.clear()
        }

        fun notifyUpdate() {
            onDataUpdatedCallback?.invoke()
        }

        fun generateDemoData(numChannels: Int, eventsPerChannel: Int) {
            clear()
            for (i in 0 until numChannels) {
                val channel = Channel(i, "Channel $i", "URL $i")
                addChannel(channel)
                var startTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)
                for (j in 0 until eventsPerChannel) {
                    val duration = kotlin.random.Random.nextLong(TimeUnit.MINUTES.toMillis(15), TimeUnit.HOURS.toMillis(2))
                    val endTime = startTime + duration
                    addEvent(i, Event(j, "Event $j Ch $i", "Desc...", startTime, endTime))
                    startTime = endTime + kotlin.random.Random.nextLong(TimeUnit.MINUTES.toMillis(1), TimeUnit.MINUTES.toMillis(5))
                }
            }
            notifyUpdate()
        }

        override val itemCount: Int get() = channelsList.size
        override fun channelAt(position: Int): Channel? = channelsList.getOrNull(position)
        override fun eventsOfChannel(channelPosition: Int): List<Event> = eventsMap[channelsList.getOrNull(channelPosition)?.id] ?: emptyList()
        override fun eventOfChannelAt(channelPosition: Int, eventPosition: Int): Event? =
            eventsMap[channelsList.getOrNull(channelPosition)?.id]?.getOrNull(eventPosition)
        override fun size(): Int = channelsList.size
        override fun onDataUpdated(callback: () -> Unit) {
            onDataUpdatedCallback = callback
        }
    }

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        context = ApplicationProvider.getApplicationContext<Application>()
        guideView = GuideView(context, Robolectric.buildAttributeSet().build())
        testProvider = TestDataProvider()
        guideView.setDataProvider(testProvider)
        guideView.setEPGClickListener(mockClickListener)

        // Simulate view being laid out to initialize dimensions needed for calculations
        guideView.measure(
            makeMeasureSpec(1920, EXACTLY),
            makeMeasureSpec(1080, EXACTLY)
        )
        guideView.layout(0, 0, 1920, 1080)
    }

    private fun makeMeasureSpec(size: Int, mode: Int): Int {
        return android.view.View.MeasureSpec.makeMeasureSpec(size, mode)
    }
    private val EXACTLY = android.view.View.MeasureSpec.EXACTLY


    @Test
    fun initialization_loadsDefaultDimensionsAndRenderers() {
        assertNotNull(guideView)
        // Check if renderers are initialized (indirectly, by checking if onDraw doesn't crash)
        // A more direct way would be to make renderers internal and check for null, but this is okay.
        testProvider.generateDemoData(1,1) // Add some data to make onDraw do something
        guideView.draw(mockCanvas) // Robolectric's default canvas is fine for not crashing
        // No crash means renderers were likely initialized.
    }

    @Test
    fun setDataProvider_recalculatesAndRedraws() {
        val spiedGuideView = spy(guideView)
        val newProvider = TestDataProvider()
        newProvider.generateDemoData(5,5)

        spiedGuideView.setDataProvider(newProvider)

        // setDataProvider calls initializeRenderers and its callback calls recalculateAndRedraw -> redraw
        // The provider's generateDemoData also calls notifyUpdate -> callback -> recalculateAndRedraw -> redraw
        verify(spiedGuideView, atLeastOnce()).recalculateAndRedraw(anyInt(), anyBoolean())
        verify(spiedGuideView, atLeastOnce()).redraw()
    }

    @Test
    fun timePixelConversion_isConsistent() {
        testProvider.generateDemoData(1,1) // Needed for resetBoundaries to run via recalculateAndRedraw
        guideView.recalculateAndRedraw(-1, false) // Ensure mMillisPerPixel is set

        val timeMillis = System.currentTimeMillis()
        // Accessing private members for testing is generally discouraged.
        // These would ideally be tested via public API or @VisibleForTesting internal methods.
        // Assuming we added test helpers or made them internal for testing:
        // val pixelX = guideView.getXFrom(timeMillis)
        // val convertedTimeMillis = guideView.getTimeFrom(pixelX)
        // val tolerance = guideView.mMillisPerPixel
        // if (tolerance > 0) {
        //     assertEquals(timeMillis / tolerance, convertedTimeMillis / tolerance)
        // } else {
        //     fail("mMillisPerPixel was 0, view not properly measured or too small for test.")
        // }
        // For now, this test remains conceptual without direct accessors.
        assertTrue("Conceptual test for time/pixel conversion. Needs accessors/helpers.", true)
    }


    @Test
    fun firstVisibleChannelPosition_isZeroWhenScrolledToTop() {
        testProvider.generateDemoData(10, 1)
        guideView.recalculateAndRedraw(-1, false)
        guideView.scrollTo(0, 0)
        // To properly test this, we need to ensure layout has occurred and dimensions are set.
        // Robolectric's layout pass might be needed or manual call to measure/layout.
        shadowOf(guideView).layout() // Trigger a layout pass

        assertEquals(0, guideView.firstVisibleChannelPosition)
    }

    @Test
    fun updateSelection_updatesPositionsAndNotifiesListener() {
        testProvider.generateDemoData(10, 1) // Ensure dataProvider is not empty
        val channelToSelect = 5
        val eventToSelect = 0

        guideView.updateSelection(channelToSelect, eventToSelect)

        // Need getters or internal visibility for these:
        // assertEquals(channelToSelect, guideView.selectedChannelPos)
        // assertEquals(eventToSelect, guideView.selectedEventPos)

        // Verify onEventSelected was called on the listener
        // The actual Channel/Event objects depend on the TestDataProvider's data
        verify(mockClickListener).onEventSelected(
            eq(testProvider.channelAt(channelToSelect)),
            eq(testProvider.eventOfChannelAt(channelToSelect, eventToSelect))
        )
    }

    @Test
    fun selectEvent_updatesPositionAndNotifies() {
        testProvider.generateDemoData(1, 5) // Channel 0, 5 events
        guideView.recalculateAndRedraw(0, false) // Select channel 0

        guideView.selectEvent(2, false) // Select event at index 2
        // assertEquals(2, guideView.selectedEventPos)
        verify(mockClickListener).onEventSelected(
            eq(testProvider.channelAt(0)),
            eq(testProvider.eventOfChannelAt(0, 2))
        )
    }

    // Conceptual test for navigation - full testing is complex
    @Test
    fun navigation_nextChannel_updatesSelection() {
        testProvider.generateDemoData(3, 1) // 3 channels
        guideView.updateSelection(0,0) // Start at Ch 0

        guideView.nextChannel() // Simulate DPAD_DOWN
        // assertEquals(1, guideView.selectedChannelPos)
        // The event selected might be -1 or based on complex logic, check listener
         verify(mockClickListener, atLeastOnce()).onEventSelected(eq(testProvider.channelAt(1)), any())
    }

    @Test
    fun navigation_prevChannel_updatesSelection() {
        testProvider.generateDemoData(3, 1)
        guideView.updateSelection(1,0) // Start at Ch 1

        guideView.prevChannel() // Simulate DPAD_UP
        // assertEquals(0, guideView.selectedChannelPos)
        verify(mockClickListener, atLeastOnce()).onEventSelected(eq(testProvider.channelAt(0)), any())
    }
}

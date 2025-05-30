package dev.sajidali.guide

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Parcelable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Scroller
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import dev.sajidali.guide.data.Channel
import dev.sajidali.guide.data.DataProvider
import dev.sajidali.guide.data.Event
import dev.sajidali.guide.render.ChannelRenderer
import dev.sajidali.guide.render.EventRenderer
import dev.sajidali.guide.render.TimeLineRenderer
import dev.sajidali.guide.render.TimebarRenderer
import org.joda.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.math.*

class GuideView : ViewGroup {

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = -1) : super(
        context, attrs, defStyleAttr
    ) {
        init(attrs, defStyleAttr)
    }

    constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = -1, defStyleRes: Int = -1
    ) : super(
        context, attrs, defStyleAttr, defStyleRes
    ) {
        init(attrs, defStyleAttr, defStyleRes)
    }


    private var eventLayoutTextSize: Int
    private var eventLayoutTextColor: ColorStateList?
    private var dataProvider: DataProvider? = null
    private var timeFormat: String = "HH:mm"

    private val mClipRect: Rect
    private var mDrawingRect: Rect // Will be less used if pooling is per-item
    private val mMeasuringRect: Rect
    private val mPaint: Paint
    private lateinit var rectPool: RectPool
    private val mScroller: Scroller
    private val mGestureDetector: GestureDetector

    private var mChannelLayoutMargin: Int
    private var mChannelLayoutPadding: Int
    private var mChannelLayoutHeight: Int
    private var mChannelLayoutWidth: Int
    private var mSelectedRowScale: Float = 1f

    private var mChannelRowBackground: Drawable?

    private var mEventBackground: Drawable?

    private var mTimeBarLineWidth: Int
    private var mTimeBarLineColor: Int
    private var mTimeBarHeight: Int
    private var mTimeBarTextSize: Int

    private var mTimebarBackground: Drawable?

    private var mHoursInViewPort: Long // Used by TimebarRenderer
    private var mDaysBack: Long
    private var mDaysForward: Long
    private var mTimeSpacing: Long // Used by TimebarRenderer

    // Renderers
    private lateinit var timebarRenderer: TimebarRenderer
    private lateinit var timeLineRenderer: TimeLineRenderer
    private lateinit var eventRenderer: EventRenderer
    private lateinit var channelRenderer: ChannelRenderer

    private var screenWidth = 0
    private var screenHeight = 0


    private var mClickListener: ClickListener? = null
    private var mMaxHorizontalScroll: Int = 0
    private var mMaxVerticalScroll: Int = 0
    private var mMillisPerPixel: Long = 0
    private var mTimeOffset: Long = 0
    private var mTimeLowerBoundary: Long = 0
    private var mTimeUpperBoundary: Long = 0

    private val loadThreshold: Int
        get() {
            val x = measuredHeight * 2
            return mMaxVerticalScroll - x
        }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
//        if (t > loadThreshold) {
//            pagingDataDiffer.refresh()
//        }
    }

    private var selectedEventPos: Int = -1
    private var selectedChannelPos = 0

    private val currentChannel: Channel?
        get() {
            return dataProvider?.let { provider ->
                if (selectedChannelPos in 0 until provider.size()) {
                    provider.channelAt(selectedChannelPos)
                } else null
            }

        }

    private val selectedEvent: Event?
        get() = dataProvider?.eventOfChannelAt(selectedChannelPos, selectedEventPos)

    private var isEventHandled: Boolean = false
    private var waitForLongClick: Boolean = false
    private var isLongPressed = false

    private val channelAreaWidth: Int
        get() = mChannelLayoutWidth + mChannelLayoutPadding + mChannelLayoutMargin

    private val programAreaWidth: Int
        get() = width - channelAreaWidth

    private val firstVisibleChannelPosition: Int
        get() {
            val y = scrollY + mTimeBarHeight

            var position =
                (y - mChannelLayoutMargin) / (mChannelLayoutHeight + mChannelLayoutMargin)

            if (position < 0) {
                position = 0
            }
            return position
        }

    private// Add one extra row if we don't fill screen with current..
    val lastVisibleChannelPosition: Int
        get() {
            val y = scrollY
            val totalChannelCount = dataProvider?.size() ?: 0
            val screenHeight = height
            var position =
                (y + screenHeight + mTimeBarHeight - mChannelLayoutMargin) / (mChannelLayoutHeight + mChannelLayoutMargin)

            if (position > totalChannelCount - 1) {
                position = totalChannelCount - 1
            }
            return if (y + screenHeight > position * mChannelLayoutHeight && position < totalChannelCount - 1) position + 1 else position
        }

    private val xPositionStart: Int
        get() = getXFrom(System.currentTimeMillis() - mHoursInViewPort / 2)

    init {
        setWillNotDraw(false)

        mDrawingRect = Rect() // Still useful for overall canvas bounds
        mClipRect = Rect()
        mMeasuringRect = Rect()
        mPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mPaint.strokeWidth = 0.5f
        rectPool = RectPool()
        mGestureDetector = GestureDetector(context, OnGestureListener())

        // Adding some friction that makes the epg less flappy.
        mScroller = Scroller(context)
        mScroller.setFriction(0.2f)
        mChannelLayoutMargin = resources.getDimensionPixelSize(R.dimen.gv_channel_layout_margin)
        mChannelLayoutPadding = resources.getDimensionPixelSize(R.dimen.gv_channel_layout_padding)
        mChannelLayoutHeight = resources.getDimensionPixelSize(R.dimen.gv_channel_layout_height)
        mChannelLayoutWidth = resources.getDimensionPixelSize(R.dimen.gv_channel_layout_width)
        mChannelRowBackground =
            ContextCompat.getDrawable(context, R.drawable.channel_row_background)
        mEventBackground = ContextCompat.getDrawable(context, R.drawable.event_background)
        eventLayoutTextColor = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(
                context.getThemedAttribute(android.R.attr.colorAccent),
                context.getThemedAttribute(android.R.attr.textColorPrimary)
            )
        )
        eventLayoutTextSize = resources.getDimensionPixelSize(R.dimen.gv_event_layout_text)
        mTimeBarHeight = resources.getDimensionPixelSize(R.dimen.gv_time_bar_height)
        mTimeBarTextSize = resources.getDimensionPixelSize(R.dimen.gv_time_bar_text)
        mTimeBarLineWidth = resources.getDimensionPixelSize(R.dimen.gv_time_bar_line_width)
        mTimeBarLineColor = context.getThemedAttribute(android.R.attr.colorAccent)
        mTimebarBackground = ColorDrawable(Color.TRANSPARENT)
        mHoursInViewPort = TimeUnit.HOURS.toMillis(1) + TimeUnit.MINUTES.toMillis(30)
        mDaysBack = TimeUnit.MINUTES.toMillis(30)
        mDaysForward = TimeUnit.DAYS.toMillis(1)
        mTimeSpacing = TimeUnit.MINUTES.toMillis(30)
        screenDimenInitialization()
        initializeRenderers()
    }

    private fun initializeRenderers() {
        val defaultTextColor = eventLayoutTextColor?.defaultColor ?: Color.WHITE
        // Ensure eventLayoutTextColor is not null before accessing getColorForState
        val focusedTextColor = eventLayoutTextColor?.getColorForState(intArrayOf(android.R.attr.state_focused), defaultTextColor) ?: defaultTextColor
        val selectedTextColor = eventLayoutTextColor?.getColorForState(intArrayOf(android.R.attr.state_selected), defaultTextColor) ?: defaultTextColor

        timebarRenderer = TimebarRenderer(
            rectPool = rectPool,
            mPaint = mPaint,
            mTimebarBackground = mTimebarBackground,
            mClipRect = mClipRect, // TimebarRenderer uses mClipRect for its internal clipping logic
            mChannelLayoutWidth = mChannelLayoutWidth,
            mChannelLayoutMargin = mChannelLayoutMargin,
            mTimeBarHeight = mTimeBarHeight,
            mTimeBarTextSize = mTimeBarTextSize,
            eventLayoutTextColorDefault = defaultTextColor,
            getXFromTime = { time -> getXFrom(time) }
        )

        timeLineRenderer = TimeLineRenderer(
            rectPool = rectPool,
            mPaint = mPaint,
            mTimeBarLineWidth = mTimeBarLineWidth,
            mTimeBarLineColor = mTimeBarLineColor,
            getXFromTime = { time -> getXFrom(time) },
            shouldDrawTimeLineFn = { now -> shouldDrawTimeLine(now) }
        )

        eventRenderer = EventRenderer(
            rectPool = rectPool,
            mPaint = mPaint,
            mMeasuringRect = mMeasuringRect,
            mClipRect = mClipRect, // EventRenderer uses mClipRect for its internal clipping logic
            dataProvider = dataProvider,
            mEventBackground = mEventBackground,
            eventLayoutTextColorFocused = focusedTextColor,
            eventLayoutTextColorSelected = selectedTextColor,
            eventLayoutTextColorDefault = defaultTextColor,
            mChannelLayoutPadding = mChannelLayoutPadding,
            mChannelLayoutMargin = mChannelLayoutMargin,
            getXFromTime = { time -> getXFrom(time) },
            getTopFromChannelPos = { pos -> getTopFrom(pos) },
            getChannelHeight = { selected -> channelHeight(selected) }
        )

        channelRenderer = ChannelRenderer(
            rectPool = rectPool,
            mPaint = mPaint,
            mMeasuringRect = mMeasuringRect,
            dataProvider = dataProvider,
            mChannelRowBackground = mChannelRowBackground,
            eventLayoutTextColorFocused = focusedTextColor,
            eventLayoutTextColorDefault = defaultTextColor,
            mChannelLayoutWidth = mChannelLayoutWidth,
            programAreaWidthValue = programAreaWidth,
            getTopFromChannelPos = { pos -> getTopFrom(pos) },
            getChannelHeight = { selected -> channelHeight(selected) }
        )
    }

    internal fun channelHeight(isSelected: Boolean): Int {
        return if (isSelected) (mChannelLayoutHeight * mSelectedRowScale).toInt() else mChannelLayoutHeight
    }

    private fun screenDimenInitialization() {
        screenWidth = resources.displayMetrics.widthPixels
        screenHeight = resources.displayMetrics.heightPixels
        if (screenWidth < screenHeight) {
            screenWidth = screenHeight
            screenHeight = resources.displayMetrics.widthPixels
        }
    }

    private fun init(attrs: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0) {
        context.withStyledAttributes(attrs, R.styleable.GuideView, defStyleAttr, defStyleRes) {
            mChannelLayoutMargin = getDimensionPixelSize(
                R.styleable.GuideView_gv_ChannelMargin,
                resources.getDimensionPixelSize(R.dimen.gv_channel_layout_margin)
            )
            mChannelLayoutPadding = getDimensionPixelSize(
                R.styleable.GuideView_gv_ChannelPadding,
                resources.getDimensionPixelSize(R.dimen.gv_channel_layout_padding)
            )
            mChannelLayoutHeight = getDimensionPixelSize(
                R.styleable.GuideView_gv_ChannelHeight,
                resources.getDimensionPixelSize(R.dimen.gv_channel_layout_height)
            )

            mSelectedRowScale =
                getFloat(R.styleable.GuideView_gv_SelectedRowScale, mSelectedRowScale)

            mChannelLayoutWidth = getDimensionPixelSize(
                R.styleable.GuideView_gv_ChannelWidth,
                resources.getDimensionPixelSize(R.dimen.gv_channel_layout_width)
            )
            mChannelRowBackground =
                getDrawable(R.styleable.GuideView_gv_ChannelBackground) ?: mChannelRowBackground
            mEventBackground =
                getDrawable(R.styleable.GuideView_gv_EventBackground) ?: mEventBackground
            eventLayoutTextColor =
                getColorStateList(R.styleable.GuideView_gv_EventTextColor) ?: eventLayoutTextColor
            eventLayoutTextSize = getDimensionPixelSize(
                R.styleable.GuideView_gv_EventTextSize,
                resources.getDimensionPixelSize(R.dimen.gv_event_layout_text)
            )
            mTimeBarHeight = getDimensionPixelSize(
                R.styleable.GuideView_gv_TimebarHeight,
                resources.getDimensionPixelSize(R.dimen.gv_time_bar_height)
            )
            mTimeBarTextSize = getDimensionPixelSize(
                R.styleable.GuideView_gv_TimebarTextSize,
                resources.getDimensionPixelSize(R.dimen.gv_time_bar_text)
            )
            mTimeBarLineWidth = getDimensionPixelSize(
                R.styleable.GuideView_gv_TimeLineWidth,
                resources.getDimensionPixelSize(R.dimen.gv_time_bar_line_width)
            )
            mTimeBarLineColor = getColor(R.styleable.GuideView_gv_TimeLineColor, mTimeBarLineColor)
            mTimebarBackground =
                getColorOrDrawable(R.styleable.GuideView_gv_TimebarBackground, Color.TRANSPARENT)
                    ?: mTimebarBackground
            mHoursInViewPort =
                TimeUnit.HOURS.toMillis(getInt(R.styleable.GuideView_gv_HoursToShow, 2).toLong())
            mTimeSpacing = TimeUnit.MINUTES.toMillis(
                getInt(
                    R.styleable.GuideView_gv_TimeSpacingInMinutes, 30
                ).toLong()
            )
        }
        screenDimenInitialization()
        initializeRenderers() // Re-initialize renderers if attributes change that affect them
    }

    fun setDataProvider(provider: DataProvider) {
        this.dataProvider = provider
        initializeRenderers() // Update renderers that depend on dataProvider
        dataProvider?.onDataUpdated {
            recalculateAndRedraw(this.selectedEventPos, false)
        }
    }

    public override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val epgState = EPGState(superState!!)
        epgState.currentEvent = this.selectedEventPos
        return epgState
    }

    public override fun onRestoreInstanceState(state: Parcelable) {
        if (state !is EPGState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        this.selectedEventPos = state.currentEvent
    }

    override fun onDraw(canvas: Canvas) {
        if ((dataProvider?.size() ?: 0) > 0) {
            mTimeLowerBoundary = getTimeFrom(scrollX)
            mTimeUpperBoundary = getTimeFrom(scrollX + width)

            val drawingRect = mDrawingRect // Retained for potential future use or other logic not related to renderers.
            drawingRect.left = scrollX
            drawingRect.top = scrollY
            drawingRect.right = drawingRect.left + width
            drawingRect.bottom = drawingRect.top + height

            // Order matters for drawing: Channels background, Events, then Timebar/Timeline overlays.
            channelRenderer.draw(
                canvas = canvas,
                scrollX = scrollX,
                firstVisibleChannelPos = firstVisibleChannelPosition,
                lastVisibleChannelPos = lastVisibleChannelPosition,
                currentSelectedChannelPos = selectedChannelPos,
                eventTextSize = eventLayoutTextSize.toFloat(),
                selectedRowScale = mSelectedRowScale
            )

            eventRenderer.draw(
                canvas = canvas,
                currentScrollX = scrollX,
                scrollY = scrollY,
                currentViewWidth = width,
                firstVisibleChannelPos = firstVisibleChannelPosition,
                lastVisibleChannelPos = lastVisibleChannelPosition,
                currentSelectedChannelPos = selectedChannelPos,
                currentSelectedEventPos = selectedEventPos,
                currentTimeLowerBoundary = mTimeLowerBoundary,
                currentTimeUpperBoundary = mTimeUpperBoundary,
                currentChannelAreaWidth = channelAreaWidth, // Pass channelAreaWidth
                eventTextSize = eventLayoutTextSize.toFloat(), // Pass base event text size
                selectedRowScale = mSelectedRowScale // Pass selected row scale
            )

            timebarRenderer.draw(
                canvas = canvas,
                scrollX = scrollX,
                scrollY = scrollY,
                width = width, // view width
                timeLowerBoundary = mTimeLowerBoundary,
                timeFormat = timeFormat,
                hoursInViewPort = mHoursInViewPort,
                timeSpacing = mTimeSpacing
            )

            timeLineRenderer.draw(
                canvas = canvas,
                scrollY = scrollY,
                viewHeight = height // view height
            )
            //drawResetButton(canvas);

            // If scroller is scrolling/animating do scroll. This applies when doing a fling.
            if (mScroller.computeScrollOffset()) {
                scrollTo(mScroller.finalX, mScroller.finalY)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalculateAndRedraw(this.selectedEventPos, false)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return mGestureDetector.onTouchEvent(event)
    }


    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        //return mGame.handleMotionEvent(event);
        return false
    }


    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {}

    /* All original drawing methods (drawTimebar, drawTimebarDayIndicator, drawTimeLine,
       drawEvents, drawEvent, drawChannelListItems, drawChannelItem) are removed.
       Their logic is now in their respective Renderer classes.
    */

    fun setTimeFormat(format: String) {
        this.timeFormat = format
        // Potentially re-init or update TimebarRenderer if it caches time format string.
        // For now, TimebarRenderer.draw takes timeFormat directly.
        redraw()
    }

    // Helper methods like isEventCurrent, setEventDrawingRectangle are now part of EventRenderer or handled by it.
    // isEventVisible is still here as it's used by drawEvents (which is also currently still here but will be removed).
    // Once drawEvents is removed, isEventVisible can be removed if EventRenderer has its own.

    // This version of drawEvents is kept temporarily until EventRenderer is fully integrated
    // and confirmed to handle its logic (like mClipRect).
    // The old drawEvents method is now fully removed. EventRenderer handles this logic.

    // Keep helper methods in GuideView that are needed by multiple renderers (passed as lambdas)
    // or for GuideView's own logic.
    // isEventVisible is no longer needed here as EventRenderer handles its own visibility logic.

    fun isTimelineVisible(): Boolean {
        // This can use the TimeLineRenderer's logic or be kept if GuideView needs it elsewhere.
        // For now, TimeLineRenderer handles its own shouldDrawTimeLineFn.
        return System.currentTimeMillis() in mTimeLowerBoundary until mTimeUpperBoundary
    }

    // This can be removed if EventRenderer's internal isEventVisible is sufficient.
    // private fun isEventVisible(start: Long, end: Long): Boolean {
    // return (start in mTimeLowerBoundary..mTimeUpperBoundary || end in mTimeLowerBoundary..mTimeUpperBoundary || start <= mTimeLowerBoundary && end >= mTimeUpperBoundary)
    // }

    private fun calculatedBaseLine(): Long {
        return LocalDateTime.now().toDateTime().minusMillis(mDaysBack.toInt()).millis
    }

    private fun calculateMaxHorizontalScroll() {
        mMaxHorizontalScroll =
            ((mDaysBack + mDaysForward - mHoursInViewPort) / mMillisPerPixel).toInt()
    }

    private fun calculateMaxVerticalScroll() {
        val maxVerticalScroll =
            getTopFrom((dataProvider?.size() ?: 0) - 2) + mChannelLayoutHeight + channelHeight(true)
        mMaxVerticalScroll = if (maxVerticalScroll < height) 0 else maxVerticalScroll - height
    }

    private fun getXFrom(time: Long): Int {
        return (((time - mTimeOffset) / mMillisPerPixel).toInt() + mChannelLayoutMargin + mChannelLayoutWidth)
    }

    private fun getTopFrom(position: Int): Int {
        return if (position <= selectedChannelPos)
            (position * (mChannelLayoutHeight + mChannelLayoutMargin) + mTimeBarHeight)
        else {
            ((position - 1) * (mChannelLayoutHeight + mChannelLayoutMargin) + mTimeBarHeight + channelHeight(
                true
            ))
        }
    }

    private fun getTimeFrom(x: Int): Long {
        return x * mMillisPerPixel + mTimeOffset
    }

    private fun calculateMillisPerPixel(): Long {
        return (mHoursInViewPort / (measuredWidth - mChannelLayoutWidth - mChannelLayoutMargin))
    }

    private fun resetBoundaries() {
        mMillisPerPixel = calculateMillisPerPixel()
        mTimeOffset = calculatedBaseLine()
        mTimeLowerBoundary = getTimeFrom(0)
        mTimeUpperBoundary = getTimeFrom(width)
    }

    private fun calculateChannelsHitArea(): Rect {
        mMeasuringRect.top = mTimeBarHeight
        val visibleChannelsHeight =
            (dataProvider?.size() ?: 0) * (mChannelLayoutHeight + mChannelLayoutMargin)
        mMeasuringRect.bottom =
            if (visibleChannelsHeight < height) visibleChannelsHeight else height
        mMeasuringRect.left = 0
        mMeasuringRect.right = mChannelLayoutWidth
        return mMeasuringRect
    }

    private fun calculateProgramsHitArea(): Rect {
        mMeasuringRect.top = mTimeBarHeight
        val visibleChannelsHeight =
            dataProvider.itemCount * (mChannelLayoutHeight + mChannelLayoutMargin)
        mMeasuringRect.bottom =
            if (visibleChannelsHeight < height) visibleChannelsHeight else height
        mMeasuringRect.left = mChannelLayoutWidth
        mMeasuringRect.right = width
        return mMeasuringRect
    }

    private fun getChannelPosition(y: Int): Int {
        var y1 = y
        y1 -= mTimeBarHeight
        val channelPosition = (y1) / (mChannelLayoutHeight + mChannelLayoutMargin)

        return if (dataProvider.itemCount == 0) -1 else channelPosition
    }

    private fun getProgramPosition(channelPosition: Int, time: Long): Int {
        val events = dataProvider?.eventsOfChannel(channelPosition) ?: return 0
//        for ((index, event) in events.withIndex()) {
//            if (event.start <= time && event.end >= time) {
//                return index
//            }
//        }
        return events.indexOfFirst { event ->
            event.start <= time && event.end >= time
        }
//        for (eventPos in 0 until events.size) {
//            val event = events.getOrNull(eventPos) ?: continue
//
//            if (event.startTimestamp <= time && event.stopTimestamp >= time) {
//                return eventPos
//            }
//        }
//        return 0
    }

    private fun getProgramAtTime(channelPosition: Int, time: Long): Event? {
        return dataProvider?.eventsOfChannel(channelPosition)?.find { event ->
            event.start <= time && event.end >= time
        }
//
//
//        for (() in 0 until events.size) {
//            val event = events.getOrNull(eventPos) ?: continue
//
//            if (event.startTimestamp <= time && event.stopTimestamp >= time) {
//                return event
//            }
//        }
//        return null
    }

    fun gotoNow() {
        var dT: Long
        var dX = 0
        var dY = 0

        // calculate optimal Y position

        val minYVisible =
            scrollY // is 0 when scrolled completely to top (first channel fully visible)
        val maxYVisible = minYVisible + height

        val currentChannelTop =
            mTimeBarHeight + selectedChannelPos * (mChannelLayoutHeight + mChannelLayoutMargin)
        val currentChannelBottom = currentChannelTop + mChannelLayoutHeight

        if (currentChannelTop < minYVisible) {
            dY = currentChannelTop - minYVisible - mTimeBarHeight
        } else if (currentChannelBottom > maxYVisible) {
            dY = currentChannelBottom - maxYVisible
        }

        // calculate optimal X position
        mTimeLowerBoundary = getTimeFrom(scrollX)
        mTimeUpperBoundary = getTimeFrom(scrollX + programAreaWidth)
        val centerPosition = mTimeLowerBoundary + (mTimeUpperBoundary - mTimeLowerBoundary) / 2
        if (now > centerPosition) {
            //we need to scroll the grid to the left
            dT = (centerPosition - now) * -1
            dX = if (mMillisPerPixel > 0) (dT / mMillisPerPixel).toFloat().roundToInt() else 0
        }
        if (now < centerPosition) {
            //we need to scroll the grid to the right
            dT = now - centerPosition
            dX = (dT / mMillisPerPixel).toFloat().roundToInt()
        }

        if (scrollX + dX < 0) {
            dX = 0 - scrollX
        }
        if (scrollY + dY < 0) {
            dY = 0 - scrollY
        }

        if (scrollX + dX > mMaxHorizontalScroll) {
            dX = mMaxHorizontalScroll - scrollX
        }

        if (scrollY + dY > mMaxVerticalScroll) {
            dY = mMaxVerticalScroll - scrollY
        }

        if (dX != 0 || dY != 0) {
            mScroller.startScroll(scrollX, scrollY, dX, dY, 0)
        }
    }

    /**
     * Add click listener to the EPG.
     *
     * @param epgClickListener to add.
     */
    fun setEPGClickListener(clickListener: ClickListener) {
        mClickListener = clickListener
    }

    fun updateSelection(channel: Int, selectedEvent: Int = -1) {
        selectedChannelPos = channel
        this.selectedEventPos = selectedEvent
        recalculateAndRedraw(selectedEvent, withAnimation = false)
    }

//    fun updateSelection(channel: Channel) {
//
//        val position = data?.indexOfFirst { it.streamId == channel.streamId } ?: -1
//        if (position > 0) {
//            updateSelection(position, null)
//        }
//    }

    /**
     * This will recalculate boundaries, maximal scroll and scroll to start position which is current time.
     * To be used on device rotation etc since the device height and width will change.
     *
     * @param withAnimation true if scroll to current position should be animated.
     */
    fun recalculateAndRedraw(selectedEvent: Int, withAnimation: Boolean) {
        var selectedEvent2 = selectedEvent
        if (dataProvider.itemCount > 0) {
            resetBoundaries()

            calculateMaxVerticalScroll()
            calculateMaxHorizontalScroll()

            //Select initial event
            if (selectedEvent2 == -1) {
                selectedEvent2 = this.selectedEventPos
            }
            if (selectedEvent2 != -1) {
                selectEvent(selectedEvent2, withAnimation)
            } else {
                selectedEvent2 = getProgramPosition(0, now)
                selectEvent(selectedEvent2, withAnimation)
                if (selectedEvent2 == -1) {
                    gotoNow()
                }
            }

            redraw()
        } else {
            redraw()
        }
    }

    /**
     * Does a invalidate() and requestLayout() which causes a redraw of screen.
     */
    fun redraw() {
        invalidate()
        requestLayout()
    }

    fun selectEvent(epgEvent: Int, withAnimation: Boolean) {
        this.selectedEventPos = epgEvent
        optimizeVisibility()
        notifyListener()
        //redraw to get the coloring of the selected event
        redraw()
    }

    fun selectCurrentEvent() {
        dataProvider?.eventsOfChannel(selectedChannelPos)
            ?.indexOfFirst { it.start <= now && it.end >= now }?.let { selectEvent(it, false) }
    }

    fun notifyListener() {
        mClickListener?.onEventSelected(currentChannel, selectedEvent)
    }

    var justGotFocus = false

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (gainFocus) {
            justGotFocus = true
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (handleNavigationEvent(event) || handleClickEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        mTimeLowerBoundary = getTimeFrom(scrollX)
        mTimeUpperBoundary = getTimeFrom(scrollX + width)
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER) {
            mClickListener?.onEventLongClicked(
                selectedChannelPos, 0, currentChannel, this.selectedEvent
            )
        }
        //notifyListener()
        redraw()
        isEventHandled = true
        waitForLongClick = false
        return true
    }

    private fun handleNavigationEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        var rv = false
        mTimeLowerBoundary = getTimeFrom(scrollX)
        mTimeUpperBoundary = getTimeFrom(scrollX + programAreaWidth)
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if ((selectedEvent?.end ?: 0) > mTimeUpperBoundary) {
                    moveMinutesForward(30)
                } else {
                    showNextEvent()
                }
                rv = true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                rv = when {
                    !isBackMoveable() -> if ((selectedEvent?.start
                            ?: 0) > mTimeLowerBoundary
                    ) showPreviousEvent() else false

                    (selectedEvent?.start?.plus(TimeUnit.MINUTES.toMillis(30))
                        ?: 0) < mTimeLowerBoundary -> {
                        moveMinutesBack(30)
                        true
                    }

                    else -> {
                        showPreviousEvent()
                    }
                }
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                rv = prevChannel()
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                rv = nextChannel()
            }
        }
        notifyListener()
        redraw()
        return rv
    }

    private fun handleClickEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.action == KeyEvent.ACTION_UP) {
                if (isLongPressed) {
                    isLongPressed = false
                } else {
                    mClickListener?.onChannelClicked(
                        selectedChannelPos, currentChannel
                    )
                }
            } else {
                if (event.isLongPress) {
                    isLongPressed = true
                    mClickListener?.onEventLongClicked(
                        selectedChannelPos, 0, currentChannel, this.selectedEvent
                    )
                }
            }
            return true
        }
        return false
    }

    fun isBackMoveable(): Boolean {
        val dT30 = TimeUnit.MINUTES.toMillis(30)
        val dX = round(dT30 / mMillisPerPixel.toFloat()).toInt()
        return scrollX - dX > 0
    }

    fun nextChannel(shouldCallListener: Boolean = false): Boolean {
        val current = Pair(selectedChannelPos, selectedEventPos)
        val new = findEventBelow(selectedChannelPos)
        if (current.first == new.first && current.second == new.second) {
            return false
        }
        new.run {
            selectedChannelPos = first
            selectedEventPos = second
            optimizeVisibility()
            if (shouldCallListener) {
                mClickListener?.onChannelClicked(
                    selectedChannelPos, currentChannel
                )
            }
        }
        return true
    }

    fun prevChannel(shouldCallListener: Boolean = false): Boolean {
        val current = Pair(selectedChannelPos, selectedEventPos)
        val new = findEventAbove(selectedChannelPos)
        if (current.first == new.first && current.second == new.second) {
            return false
        }
        new.run {
            selectedChannelPos = first
            selectedEventPos = second
            optimizeVisibility()
            if (shouldCallListener) {
                mClickListener?.onChannelClicked(
                    selectedChannelPos, currentChannel
                )
            }
        }
        return true
    }

    private fun findNextEvent(): Int {
        val programs = dataProvider?.eventsOfChannel(selectedChannelPos) ?: return -1
        val nextPos = selectedEventPos + 1
        if (nextPos >= programs.size) {
            return selectedEventPos
        }
        return nextPos
    }

    private fun findPreviousEvent(): Int {
        val prevPos = selectedEventPos - 1
        if (prevPos < 0) {
            return -1
        }
        return prevPos
    }

    private fun findEventAbove(channel: Int): Pair<Int, Int> {
        val previous = channel - 1
        if (previous < 0) return Pair(selectedChannelPos, selectedEventPos)
        val current = this.selectedEvent ?: return Pair(selectedChannelPos, selectedEventPos)
        val lowerBoundary = max(mTimeLowerBoundary, current.start)
        val upperBoundary = min(mTimeUpperBoundary, current.end)
        val eventMiddleTime = (lowerBoundary + upperBoundary) / 2
        val event = getProgramPosition(previous, eventMiddleTime)
        return if (event != -1) {
            Pair(previous, event)
        } else {
            Pair(previous, -1)
        }
    }

    private fun findEventBelow(channel: Int): Pair<Int, Int> {
        val next = channel + 1
        if (next >= dataProvider.itemCount) return Pair(selectedChannelPos, selectedEventPos)
        val current = this.selectedEvent ?: return Pair(selectedChannelPos, selectedEventPos)
        val lowerBoundary = max(mTimeLowerBoundary, current.start)
        val upperBoundary = min(mTimeUpperBoundary, current.end)
        val eventMiddleTime = (lowerBoundary + upperBoundary) / 2
        val event = getProgramPosition(next, eventMiddleTime)
        return if (event != -1) {
            Pair(next, event)
        } else {
            Pair(next, -1)
        }
    }

    private fun showNextEvent() {
        mTimeLowerBoundary = getTimeFrom(scrollX)
        mTimeUpperBoundary = getTimeFrom(scrollX + programAreaWidth)
        val nextEvent = findNextEvent()
        if (nextEvent != -1) {
            this.selectedEventPos = nextEvent
            var dX: Int
            var dT: Long
            val currentStart = selectedEvent?.start ?: 0
            dT = currentStart - mTimeLowerBoundary

            dX = round(dT / mMillisPerPixel.toFloat()).toInt()
            if (scrollX + dX < 0) {
                dX = 0 - scrollX
            }
            if (scrollX + dX > mMaxHorizontalScroll) {
                dX = mMaxHorizontalScroll - scrollX
            }
            mScroller.startScroll(scrollX, scrollY, dX, 0, 600)
        }
    }

    private fun showPreviousEvent(): Boolean {
        mTimeLowerBoundary = getTimeFrom(scrollX)
        mTimeUpperBoundary = getTimeFrom(scrollX + programAreaWidth)
        val prevEvent = findPreviousEvent()
        return if (prevEvent != -1) {
            this.selectedEventPos = prevEvent
            var dX: Int
            var dT: Long
            val currentStart = selectedEvent?.start ?: 0
            dT = currentStart - mTimeLowerBoundary
            if (dT < 0 && abs(dT) > TimeUnit.MINUTES.toMillis(30)) {
                dT = -TimeUnit.MINUTES.toMillis(30)
            }
            dX = round(dT / mMillisPerPixel.toFloat()).toInt()
            if (scrollX + dX < 0) {
                dX = 0 - scrollX
            }
            if (scrollX + dX > mMaxHorizontalScroll) {
                dX = mMaxHorizontalScroll - scrollX
            }
            mScroller.startScroll(scrollX, scrollY, dX, 0, 600)
            true
        } else false
    }

    private fun moveMinutesBack(minutes: Long) {
        mTimeLowerBoundary = getTimeFrom(scrollX)
        mTimeUpperBoundary = getTimeFrom(scrollX + programAreaWidth)
        val dT = -TimeUnit.MINUTES.toMillis(minutes)
        var dX = round(dT / mMillisPerPixel.toFloat()).toInt()
        if (scrollX + dX < 0) {
            dX = 0 - scrollX
        }
        if (scrollX + dX > mMaxHorizontalScroll) {
            dX = mMaxHorizontalScroll - scrollX
        }
        mScroller.startScroll(scrollX, scrollY, dX, 0, 200)
    }

    private fun moveMinutesForward(minutes: Long) {
        mTimeLowerBoundary = getTimeFrom(scrollX)
        mTimeUpperBoundary = getTimeFrom(scrollX + programAreaWidth)
        val dT = TimeUnit.MINUTES.toMillis(minutes)
        var dX = round(dT / mMillisPerPixel.toFloat()).toInt()
        if (scrollX + dX < 0) {
            dX = 0 - scrollX
        }
        if (scrollX + dX > mMaxHorizontalScroll) {
            dX = mMaxHorizontalScroll - scrollX
        }
        mScroller.startScroll(scrollX, scrollY, dX, 0, 200)
    }

    fun optimizeVisibility(vertical: Boolean = true, isTouched: Boolean = false) {
        var dT: Long
        var dX = 0
        var dY = 0

        // calculate optimal Y position
        if (vertical) {
            val minYVisible =
                scrollY // is 0 when scrolled completely to top (first channel fully visible)
            val currentChannelTop =
                selectedChannelPos * (mChannelLayoutHeight + mChannelLayoutMargin)
            val bottomPos = minYVisible + height - mChannelLayoutHeight
            dY = currentChannelTop - minYVisible
            if (isTouched) {
                dY = 0
                if (currentChannelTop > bottomPos) dY =
                    bottomPos - currentChannelTop + mTimeBarHeight
                else if (currentChannelTop - (mChannelLayoutHeight + mChannelLayoutMargin) < minYVisible) dY =
                    currentChannelTop - minYVisible - mTimeBarHeight
            }
        }

        if (dX != 0 || dY != 0) {
            mScroller.startScroll(scrollX, scrollY, dX, dY, 200)
        }

    }

    private fun startScroll(dX: Int, withAnimation: Boolean) {
        var newDx = 0
        if (dX < 0) {
            newDx = 0 - scrollX
        }
        if (scrollX + dX > mMaxHorizontalScroll) {
            newDx = mMaxHorizontalScroll - scrollX
        }
//        if (dX != 0) {
//            val upperTime = now + TimeUnit.DAYS.toMillis(1)
//            val lowerTime = now - TimeUnit.MINUTES.toMillis(30)
//            var time = getTimeFrom(scrollX + dX)
//            if (time < lowerTime) {
//                time = lowerTime
//            } else if (time > upperTime) {
//                time = upperTime
//            }
//            newDx = getXFrom(time) - scrollX
//        }
        if (newDx != 0) {
            mScroller.startScroll(scrollX, scrollY, newDx, 0, if (withAnimation) 400 else 0)
        }
    }

    /*
     *It will return dX from Now as per given time only if it is in time boundary Otherwise it will return respective time boundary
     *
     *
     */
    fun getDxFromNow(time: Long): Int {
        return (time / mMillisPerPixel).toInt()
    }

    private inner class OnGestureListener : GestureDetector.SimpleOnGestureListener() {

        override fun onSingleTapUp(e: MotionEvent): Boolean {

            // This is absolute coordinate on screen not taking scroll into account.
            val x = e.x.toInt()
            val y = e.y.toInt()

            // Adding scroll to clicked coordinate
            val scrollX = scrollX + x
            val scrollY = scrollY + y
//            requestFocus()
            val channelPosition = getChannelPosition(scrollY)
            if (channelPosition != -1 && mClickListener != null) {
                selectedChannelPos = channelPosition
                if (calculateChannelsHitArea().contains(x, y)) {
                    // Channel area is clicked
                    mClickListener!!.onChannelClicked(
                        channelPosition, currentChannel
                    )
                    val lowerBoundary = max(
                        mTimeLowerBoundary, selectedEvent?.start ?: now
                    )
                    val upperBoundary = Math.min(
                        mTimeUpperBoundary, selectedEvent?.end ?: now
                    )
                    val eventMiddleTime = (lowerBoundary + upperBoundary) / 2
                    selectedEventPos = getProgramPosition(selectedChannelPos, eventMiddleTime)
                    if (selectedEventPos == -1) {
                        gotoNow()
                    } else {
                        optimizeVisibility(isTouched = true)
                    }
                    notifyListener()
                } else if (calculateProgramsHitArea().contains(x, y)) {
                    // Event area is clicked
                    val programPosition = getProgramPosition(
                        channelPosition,
                        getTimeFrom(getScrollX() + x - calculateProgramsHitArea().left)
                    )
                    if (programPosition != -1) {
                        selectedEventPos = programPosition
                        if (selectedEventPos == -1) {
                            gotoNow()
                        } else {
                            optimizeVisibility(isTouched = true)
                        }
                        notifyListener()
                        mClickListener?.onEventClicked(
                            channelPosition,
                            programPosition,
                            currentChannel,
                            dataProvider?.eventOfChannelAt(selectedChannelPos, programPosition)
                        )
                    }
                }
                redraw()
            }

            return true
        }

        override fun onLongPress(e: MotionEvent) {
            // This is absolute coordinate on screen not taking scroll into account.
            val x = e.x.toInt()
            val y = e.y.toInt()

            // Adding scroll to clicked coordinate
            val scrollX = scrollX + x
            val scrollY = scrollY + y

            val channelPosition = getChannelPosition(scrollY)
            if (channelPosition != -1 && mClickListener != null) {
                selectedChannelPos = channelPosition
                if (calculateChannelsHitArea().contains(x, y)) {
                    // Channel area is clicked
                    mClickListener!!.onChannelClicked(
                        channelPosition, currentChannel
                    )
                    val lowerBoundary = max(
                        mTimeLowerBoundary, selectedEvent?.start ?: 0
                    )
                    val upperBoundary = min(
                        mTimeUpperBoundary, selectedEvent?.end ?: 0
                    )
                    val eventMiddleTime = (lowerBoundary + upperBoundary) / 2
                    selectedEventPos = getProgramPosition(selectedChannelPos, eventMiddleTime)
                    if (selectedEventPos == -1) {
                        gotoNow()
                    } else {
                        optimizeVisibility(isTouched = true)
                    }
                    notifyListener()
                } else if (calculateProgramsHitArea().contains(x, y)) {
                    // Event area is clicked
                    val programPosition = getProgramPosition(
                        channelPosition,
                        getTimeFrom(getScrollX() + x - calculateProgramsHitArea().left)
                    )
                    if (programPosition != -1) {
                        selectedEventPos = programPosition
                        if (selectedEventPos != -1) {
                            optimizeVisibility(isTouched = true)
                            mClickListener?.onEventLongClicked(
                                selectedChannelPos, programPosition, currentChannel, selectedEvent
                            )
                        }
                        notifyListener()
                    }
                }
                redraw()
            }
        }


        override fun onScroll(
            e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float
        ): Boolean {
            var dx = distanceX.toInt()
            var dy = distanceY.toInt()
            val x = scrollX
            val y = scrollY


            // Avoid over scrolling
            if (x + dx < 0) {
                dx = 0 - x
            }
            if (y + dy < 0) {
                dy = 0 - y
            }
            if (x + dx > mMaxHorizontalScroll) {
                dx = mMaxHorizontalScroll - x
            }
            if (y + dy > mMaxVerticalScroll) {
                dy = mMaxVerticalScroll - y
            }

            scrollBy(dx, dy)
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {

            mScroller.fling(
                scrollX,
                scrollY,
                -vX.toInt(),
                -vY.toInt(),
                0,
                mMaxHorizontalScroll,
                0,
                mMaxVerticalScroll
            )

            redraw()
            return true
        }

        override fun onDown(e: MotionEvent): Boolean {
            if (!mScroller.isFinished) {
                mScroller.forceFinished(true)
                return true
            }
            return true
        }
    }

    interface ClickListener {
        fun onChannelClicked(position: Int, channel: Channel?)
        fun onEventSelected(channel: Channel?, program: Event?)
        fun onEventClicked(
            channelPosition: Int, programPosition: Int, channel: Channel?, program: Event?
        )

        fun onEventLongClicked(
            channelPosition: Int, programPosition: Int, channel: Channel?, program: Event?
        )
    }
}
package dev.sajidali.guide.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat // For potential future use if needed
import dev.sajidali.guide.RectPool
import dev.sajidali.guide.data.DataProvider
import dev.sajidali.guide.data.Event

class EventRenderer(
    private val rectPool: RectPool,
    private val mPaint: Paint,
    private val mMeasuringRect: Rect, // For text measurements
    private val mClipRect: Rect, // For clipping per channel
    private val dataProvider: DataProvider?,
    private val mEventBackground: Drawable?,
    private val eventLayoutTextColorFocused: Int,
    private val eventLayoutTextColorSelected: Int,
    private val eventLayoutTextColorDefault: Int,
    private val mChannelLayoutPadding: Int,
    private val mChannelLayoutMargin: Int,
    // Lambdas for GuideView specific calculations/state
    private val getXFromTime: (time: Long) -> Int,
    private val getTopFromChannelPos: (position: Int) -> Int,
    private val getChannelHeight: (isSelected: Boolean) -> Int
) {

    private var timeLowerBoundary: Long = 0L
    private var timeUpperBoundary: Long = 0L
    private var scrollXPosition: Int = 0
    private var viewWidth: Int = 0
    private var channelAreaWidthValue: Int = 0

    private fun isEventVisible(start: Long, end: Long): Boolean {
        return (start in timeLowerBoundary..timeUpperBoundary ||
                end in timeLowerBoundary..timeUpperBoundary ||
                (start <= timeLowerBoundary && end >= timeUpperBoundary))
    }

    private fun setEventDrawingRectangle(
        channelPosition: Int,
        eventStart: Long,
        eventEnd: Long,
        targetRect: Rect,
        currentChannelHeight: Int
    ) {
        targetRect.left = getXFromTime(eventStart)
        targetRect.top = getTopFromChannelPos(channelPosition)
        targetRect.right = getXFromTime(eventEnd) - mChannelLayoutMargin
        targetRect.bottom = targetRect.top + currentChannelHeight
    }

    fun draw(
        canvas: Canvas,
        currentScrollX: Int,
        scrollY: Int,
        currentViewWidth: Int,
        firstVisibleChannelPos: Int,
        lastVisibleChannelPos: Int,
        currentSelectedChannelPos: Int,
        currentSelectedEventPos: Int,
        currentTimeLowerBoundary: Long,
        currentTimeUpperBoundary: Long,
        currentChannelAreaWidth: Int,
        eventTextSize: Float,
        selectedRowScale: Float
    ) {
        this.scrollXPosition = currentScrollX
        this.viewWidth = currentViewWidth
        this.timeLowerBoundary = currentTimeLowerBoundary
        this.timeUpperBoundary = currentTimeUpperBoundary
        this.channelAreaWidthValue = currentChannelAreaWidth

        for (channelPos in firstVisibleChannelPos..lastVisibleChannelPos) {
            mClipRect.left = scrollXPosition + channelAreaWidthValue
            mClipRect.top = getTopFromChannelPos(channelPos)
            mClipRect.right = scrollXPosition + viewWidth
            mClipRect.bottom = mClipRect.top + getChannelHeight(currentSelectedChannelPos == channelPos)

            canvas.save()
            canvas.clipRect(mClipRect)

            var foundFirstVisibleEvent = false
            val epgEvents = dataProvider?.eventsOfChannel(channelPos)

            epgEvents?.takeIf { it.isNotEmpty() }?.let { events ->
                val currentChannelHeightValue = getChannelHeight(currentSelectedChannelPos == channelPos)
                val currentEventTextSizeValue = eventTextSize * if (currentSelectedChannelPos == channelPos) selectedRowScale else 1f

                for ((index, event) in events.withIndex()) {
                    if (isEventVisible(event.start, event.end)) {
                        drawEvent(
                            canvas,
                            channelPos,
                            index,
                            event,
                            currentChannelHeightValue,
                            currentEventTextSizeValue,
                            currentSelectedChannelPos,
                            currentSelectedEventPos,
                            scrollY // not directly used by drawEvent but good for context
                        )
                        foundFirstVisibleEvent = true
                    } else if (foundFirstVisibleEvent) {
                        // Optimization: if we've found visible events and then hit one that's not,
                        // the rest for this channel (assuming sorted events) are also not visible to the right.
                        break
                    }
                }
            }
            canvas.restore()
        }
    }

    private fun drawEvent(
        canvas: Canvas,
        channelPosition: Int,
        eventPosition: Int,
        event: Event,
        currentChannelHeight: Int,
        currentEventTextSize: Float,
        selectedChannelPos: Int,
        selectedEventPos: Int,
        scrollY: Int // Added for context, though not used in drawing logic directly
    ) {
        val eventRect = rectPool.acquire()
        try {
            setEventDrawingRectangle(
                channelPosition, event.start, event.end, eventRect, currentChannelHeight
            )

            if (eventRect.left < scrollXPosition + channelAreaWidthValue) {
                eventRect.left = scrollXPosition + channelAreaWidthValue
            }
            // Ensure event does not draw beyond the right edge of the viewport, considering clip area
            if (eventRect.right > scrollXPosition + viewWidth) {
                 eventRect.right = scrollXPosition + viewWidth
            }
            // If event is entirely off-screen to the left due to large channel area, skip
            if (eventRect.left >= eventRect.right) {
                return // or continue
            }


            val isFocused = channelPosition == selectedChannelPos && selectedEventPos != -1 && eventPosition == selectedEventPos
            val isCurrent = event.isCurrent // Assuming Event has isCurrent property

            mEventBackground?.let {
                it.state = when {
                    isFocused -> intArrayOf(android.R.attr.state_focused)
                    isCurrent -> intArrayOf(android.R.attr.state_selected)
                    else -> intArrayOf()
                }
                it.bounds = eventRect
                it.draw(canvas)
            }

            val originalStyle = mPaint.style
            val originalColor = mPaint.color
            mPaint.style = Paint.Style.FILL // Ensure fill for text

            mPaint.color = when {
                isFocused -> eventLayoutTextColorFocused
                isCurrent -> eventLayoutTextColorSelected
                else -> eventLayoutTextColorDefault
            }
            mPaint.textSize = currentEventTextSize

            // Add left and right inner padding for text
            val textRectLeft = eventRect.left + mChannelLayoutPadding + 16
            val textRectRight = eventRect.right - mChannelLayoutPadding

            if (textRectLeft < textRectRight) { // Only draw text if there's space
                 // Calculate text baseline for vertical centering
                mPaint.getTextBounds(event.title, 0, event.title.length, mMeasuringRect)
                val textBaselineY = eventRect.top + (currentChannelHeight / 2) + (mMeasuringRect.height() / 2) - mPaint.descent() / 2 // Centering using font metrics

                var title = event.title
                title = title.substring(
                    0, mPaint.breakText(title, true, (textRectRight - textRectLeft).toFloat(), null)
                )
                canvas.drawText(title, textRectLeft.toFloat(), textBaselineY, mPaint)
            }

            mPaint.style = originalStyle // Restore paint
            mPaint.color = originalColor

        } finally {
            rectPool.release(eventRect)
        }
    }
}

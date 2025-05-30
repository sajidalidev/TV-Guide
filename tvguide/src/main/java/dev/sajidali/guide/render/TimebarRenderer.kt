package dev.sajidali.guide.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import dev.sajidali.guide.RectPool
import dev.sajidali.guide.toDayName
import dev.sajidali.guide.formatToPattern

class TimebarRenderer(
    private val rectPool: RectPool,
    private val mPaint: Paint,
    private val mTimebarBackground: Drawable?,
    private val mClipRect: Rect, // For clipping the timestamp area
    private val mChannelLayoutWidth: Int,
    private val mChannelLayoutMargin: Int,
    private val mTimeBarHeight: Int,
    private val mTimeBarTextSize: Int,
    private val eventLayoutTextColorDefault: Int = Color.WHITE,
    private val getXFromTime: (time: Long) -> Int // Helper from GuideView
) {

    fun draw(
        canvas: Canvas,
        scrollX: Int,
        scrollY: Int,
        width: Int, // view width
        timeLowerBoundary: Long,
        timeFormat: String,
        hoursInViewPort: Long,
        timeSpacing: Long
    ) {
        drawTimebar(canvas, scrollX, scrollY, width, timeLowerBoundary, timeFormat, hoursInViewPort, timeSpacing)
    }

    private fun drawTimebar(
        canvas: Canvas,
        scrollX: Int,
        scrollY: Int,
        viewWidth: Int,
        timeLowerBoundary: Long,
        timeFormat: String,
        hoursInViewPort: Long,
        timeSpacing: Long
    ) {
        val timebarBgRect = rectPool.acquire()
        try {
            timebarBgRect.left = scrollX
            timebarBgRect.top = scrollY
            timebarBgRect.right = timebarBgRect.left + viewWidth
            timebarBgRect.bottom = timebarBgRect.top + mTimeBarHeight

            mTimebarBackground?.let {
                it.bounds = timebarBgRect
                it.draw(canvas)
            }
        } finally {
            rectPool.release(timebarBgRect)
        }

        val timeStampAreaRect = rectPool.acquire()
        try {
            timeStampAreaRect.left = scrollX + mChannelLayoutWidth + mChannelLayoutMargin
            timeStampAreaRect.top = scrollY
            timeStampAreaRect.right = scrollX + viewWidth
            timeStampAreaRect.bottom = timeStampAreaRect.top + mTimeBarHeight

            mClipRect.left = scrollX + mChannelLayoutWidth + mChannelLayoutMargin
            mClipRect.top = scrollY
            mClipRect.right = scrollX + viewWidth
            mClipRect.bottom = mClipRect.top + mTimeBarHeight

            canvas.save()
            canvas.clipRect(mClipRect)

            mPaint.color = eventLayoutTextColorDefault
            mPaint.textSize = mTimeBarTextSize.toFloat()

            var currentTime = timeLowerBoundary
            val endTime = timeLowerBoundary + hoursInViewPort + timeSpacing // Add buffer for last items

            while (currentTime < endTime) {
                val roundedTime = timeSpacing * (currentTime / timeSpacing)
                if (roundedTime >= timeLowerBoundary - timeSpacing) { // Start drawing from one spacing before
                     val xPos = getXFromTime(roundedTime)
                     // Only draw if visible within the clipped area
                     if (xPos >= timeStampAreaRect.left && xPos <= timeStampAreaRect.right + mPaint.measureText(roundedTime.formatToPattern(timeFormat))) {
                        canvas.drawText(
                            roundedTime.formatToPattern(timeFormat),
                            xPos.toFloat(),
                            (timeStampAreaRect.top + ((timeStampAreaRect.bottom - timeStampAreaRect.top) / 2 + mTimeBarTextSize / 2)).toFloat(),
                            mPaint
                        )
                    }
                }
                currentTime += timeSpacing
                if (timeSpacing == 0L) break // prevent infinite loop
            }
            canvas.restore()
        } finally {
            rectPool.release(timeStampAreaRect)
        }
        drawTimebarDayIndicator(canvas, scrollX, scrollY, timeLowerBoundary)
    }

    private fun drawTimebarDayIndicator(
        canvas: Canvas,
        scrollX: Int,
        scrollY: Int,
        timeLowerBoundary: Long
    ) {
        val dayIndicatorRect = rectPool.acquire()
        try {
            dayIndicatorRect.left = scrollX
            dayIndicatorRect.top = scrollY
            dayIndicatorRect.right = dayIndicatorRect.left + mChannelLayoutWidth
            dayIndicatorRect.bottom = dayIndicatorRect.top + mTimeBarHeight

            mPaint.style = Paint.Style.FILL
            mPaint.color = eventLayoutTextColorDefault
            mPaint.textSize = mTimeBarTextSize.toFloat()
            mPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(
                timeLowerBoundary.toDayName(),
                (dayIndicatorRect.left + (dayIndicatorRect.right - dayIndicatorRect.left) / 2).toFloat(),
                (dayIndicatorRect.top + ((dayIndicatorRect.bottom - dayIndicatorRect.top) / 2 + mTimeBarTextSize / 2)).toFloat(),
                mPaint
            )
            mPaint.textAlign = Paint.Align.LEFT
        } finally {
            rectPool.release(dayIndicatorRect)
        }
    }
}

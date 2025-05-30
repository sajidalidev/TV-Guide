package dev.sajidali.guide.render

import android.graphics.Canvas
import android.graphics.Paint
import dev.sajidali.guide.RectPool

class TimeLineRenderer(
    private val rectPool: RectPool,
    private val mPaint: Paint,
    private val mTimeBarLineWidth: Int,
    private val mTimeBarLineColor: Int,
    private val getXFromTime: (time: Long) -> Int, // Helper from GuideView
    private val shouldDrawTimeLineFn: (now: Long) -> Boolean // Helper from GuideView
) {

    fun draw(
        canvas: Canvas,
        scrollY: Int,
        viewHeight: Int
    ) {
        val now = System.currentTimeMillis()
        if (shouldDrawTimeLineFn(now)) {
            val timeLineRect = rectPool.acquire()
            try {
                timeLineRect.left = getXFromTime(now)
                timeLineRect.top = scrollY
                timeLineRect.right = timeLineRect.left + mTimeBarLineWidth
                timeLineRect.bottom = timeLineRect.top + viewHeight

                val originalColor = mPaint.color // Preserve original paint color
                mPaint.color = mTimeBarLineColor
                mPaint.style = Paint.Style.FILL // Ensure style is fill for the line
                canvas.drawRect(timeLineRect, mPaint)
                mPaint.color = originalColor // Restore original paint color
            } finally {
                rectPool.release(timeLineRect)
            }
        }
    }
}

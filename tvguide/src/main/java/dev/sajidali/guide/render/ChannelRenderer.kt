package dev.sajidali.guide.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import dev.sajidali.guide.RectPool
import dev.sajidali.guide.data.DataProvider

class ChannelRenderer(
    private val rectPool: RectPool,
    private val mPaint: Paint,
    private val mMeasuringRect: Rect, // For text measurements
    private val dataProvider: DataProvider?,
    private val mChannelRowBackground: Drawable?,
    private val eventLayoutTextColorFocused: Int,
    private val eventLayoutTextColorDefault: Int,
    private val mChannelLayoutWidth: Int,
    private val programAreaWidthValue: Int, // calculated in GuideView
    // Lambdas for GuideView specific calculations/state
    private val getTopFromChannelPos: (position: Int) -> Int,
    private val getChannelHeight: (isSelected: Boolean) -> Int
) {

    fun draw(
        canvas: Canvas,
        scrollX: Int,
        firstVisibleChannelPos: Int,
        lastVisibleChannelPos: Int,
        currentSelectedChannelPos: Int,
        eventTextSize: Float, // Base text size
        selectedRowScale: Float
    ) {
        for (pos in firstVisibleChannelPos..lastVisibleChannelPos) {
            val isSelected = currentSelectedChannelPos == pos
            val currentChannelHeightValue = getChannelHeight(isSelected)
            val currentChannelTextColor = if (isSelected) {
                eventLayoutTextColorFocused
            } else {
                eventLayoutTextColorDefault
            }
            val currentChannelTextSize = eventTextSize * if (isSelected) selectedRowScale else 1f

            drawChannelItem(
                canvas,
                pos,
                scrollX,
                isSelected,
                currentChannelHeightValue,
                currentChannelTextColor,
                currentChannelTextSize
            )
        }
    }

    private fun drawChannelItem(
        canvas: Canvas,
        position: Int,
        scrollX: Int,
        isSelected: Boolean,
        currentChannelHeight: Int,
        currentChannelTextColor: Int,
        currentChannelTextSize: Float
    ) {
        val channelItemRect = rectPool.acquire()
        try {
            // Draw Full Row Background
            channelItemRect.left = scrollX
            channelItemRect.top = getTopFromChannelPos(position)
            channelItemRect.right = channelItemRect.left + programAreaWidthValue + mChannelLayoutWidth // Full width
            channelItemRect.bottom = channelItemRect.top + currentChannelHeight

            mChannelRowBackground?.state = if (isSelected) {
                intArrayOf(android.R.attr.state_focused)
            } else {
                intArrayOf()
            }
            mChannelRowBackground?.bounds = channelItemRect
            mChannelRowBackground?.draw(canvas)

            // Configure rect for the text part (channel name area)
            channelItemRect.left = scrollX
            channelItemRect.right = scrollX + mChannelLayoutWidth
            // channelItemRect.top and .bottom are already correct for the row height.

            channelItemRect.left += 16 // Inner padding for text

            // Draw Channel Name
            val originalColor = mPaint.color
            val originalTextSize = mPaint.textSize
            val originalTextAlign = mPaint.textAlign

            mPaint.color = currentChannelTextColor
            mPaint.textSize = currentChannelTextSize
            mPaint.textAlign = Paint.Align.LEFT // Ensure alignment

            var title = dataProvider?.channelAt(position)?.title ?: ""

            mPaint.getTextBounds(title, 0, title.length, mMeasuringRect)
            val textBaselineY = channelItemRect.top + (currentChannelHeight / 2) + (mMeasuringRect.height() / 2) - mPaint.descent() / 2


            title = title.substring(
                0, mPaint.breakText(title, true, (channelItemRect.right - channelItemRect.left).toFloat(), null)
            )
            canvas.drawText(title, channelItemRect.left.toFloat(), textBaselineY, mPaint)

            // Restore paint
            mPaint.color = originalColor
            mPaint.textSize = originalTextSize
            mPaint.textAlign = originalTextAlign

        } finally {
            rectPool.release(channelItemRect)
        }
    }
}

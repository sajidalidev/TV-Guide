package dev.sajidali.guide

import android.graphics.Rect
import java.util.LinkedList

class RectPool(private val maxSize: Int = 20) {
    private val pool = LinkedList<Rect>()

    fun acquire(): Rect {
        synchronized(pool) {
            if (pool.isNotEmpty()) {
                return pool.pop()
            }
        }
        return Rect()
    }

    fun release(rect: Rect) {
        synchronized(pool) {
            if (pool.size < maxSize) {
                rect.setEmpty() // Reset the rect
                pool.push(rect)
            }
        }
    }
}

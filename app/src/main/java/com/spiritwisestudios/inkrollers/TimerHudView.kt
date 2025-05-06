package com.spiritwisestudios.inkrollers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * HUD overlay that shows a countdown timer in mm:ss format.
 */
class TimerHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var remainingMs: Long = 0L
    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 48f * resources.displayMetrics.density
        isAntiAlias = true
        textAlign = Paint.Align.RIGHT
    }

    /**
     * Update the countdown (in milliseconds) and redraw.
     */
    fun updateTime(ms: Long) {
        remainingMs = ms
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Format mm:ss
        val totalSec = (remainingMs / 1000).coerceAtLeast(0L)
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        val timeText = String.format("%02d:%02d", minutes, seconds)
        // Draw text aligned to the right, near top
        val x = width.toFloat()
        val y = textPaint.textSize + 8f * resources.displayMetrics.density
        canvas.drawText(timeText, x, y, textPaint)
    }
} 
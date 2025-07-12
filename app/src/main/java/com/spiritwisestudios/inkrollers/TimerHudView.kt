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
        color = Color.WHITE
        textSize = 32f * resources.displayMetrics.density
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    /**
     * Update the countdown (in milliseconds) and redraw.
     * Pass -1L for infinite time (no time limit).
     */
    fun updateTime(ms: Long) {
        remainingMs = ms
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val timeText = if (remainingMs < 0) {
            "∞" // Infinite time
        } else {
            // Format mm:ss
            val totalSec = (remainingMs / 1000).coerceAtLeast(0L)
            val minutes = totalSec / 60
            val seconds = totalSec % 60
            String.format("%02d:%02d", minutes, seconds)
        }
        
        // Draw text centered in the view
        val x = width / 2f
        val y = height / 2f + textPaint.textSize / 3f // Vertically center the text
        canvas.drawText(timeText, x, y, textPaint)
    }
} 
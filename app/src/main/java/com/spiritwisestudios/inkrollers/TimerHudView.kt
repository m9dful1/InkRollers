package com.spiritwisestudios.inkrollers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View

/**
 * HUD overlay that shows a countdown timer in mm:ss format.
 */
class TimerHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "TimerHudView"
    }

    private var remainingMs: Long = 0L
    private var lastUpdateTime: Long = 0L
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f * resources.displayMetrics.density
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    /**
     * Update the countdown (in milliseconds) and redraw.
     * Pass -1L for infinite time (no time limit).
     * Enhanced with robust UI thread handling and diagnostic logging.
     */
    fun updateTime(ms: Long) {
        val currentTime = System.currentTimeMillis()
        
        try {
            // Always ensure we're on the main thread for UI updates
            if (Looper.myLooper() != Looper.getMainLooper()) {
                // Use a simple, fail-fast approach instead of complex Handler logic
                try {
                    Handler(Looper.getMainLooper()).post {
                        updateTimeInternal(ms, currentTime)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to post timer update to main thread: ms=$ms", e)
                    // Don't attempt fallback from non-main thread to avoid threading issues
                }
            } else {
                updateTimeInternal(ms, currentTime)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateTime: ms=$ms", e)
        }
    }
    
    /**
     * Internal update method that handles the actual timer display update
     */
    private fun updateTimeInternal(ms: Long, updateTime: Long) {
        try {
            val previousValue = remainingMs
            remainingMs = ms
            lastUpdateTime = updateTime
            
            // Force immediate invalidation and redraw
            invalidate()
            
            // Enhanced logging for debugging timer freeze issues
            val totalSec = (ms / 1000).coerceAtLeast(0L)
            
            // Log more frequently during suspected freeze periods
            if (totalSec % 5 == 0L && previousValue != ms) {
                Log.d(TAG, "Timer display updated: ${formatTime(ms)} (${ms}ms)")
            } else if (ms > 0 && ms == previousValue) {
                // Detect potential freeze - same value being set repeatedly
                Log.w(TAG, "Timer display REPEAT: ${formatTime(ms)} (${ms}ms) - possible freeze")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateTimeInternal: ms=$ms", e)
        }
    }
    
    /**
     * Format time for consistent logging
     */
    private fun formatTime(ms: Long): String {
        return if (ms < 0) {
            "∞"
        } else {
            val totalSec = (ms / 1000).coerceAtLeast(0L)
            val minutes = totalSec / 60
            val seconds = totalSec % 60
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        try {
            val timeText = formatTime(remainingMs)
            
            // Draw text centered in the view
            val x = width / 2f
            val y = height / 2f + textPaint.textSize / 3f // Vertically center the text
            canvas.drawText(timeText, x, y, textPaint)
            
            // Debug logging for draw calls (very occasionally)
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdateTime > 30000) { // Every 30 seconds
                Log.v(TAG, "onDraw called: displaying $timeText, last update was ${currentTime - lastUpdateTime}ms ago")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDraw", e)
            // Draw error state
            canvas.drawText("--:--", width / 2f, height / 2f + textPaint.textSize / 3f, textPaint)
        }
    }
} 
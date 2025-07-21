package com.spiritwisestudios.inkrollers

import android.util.Log

/**
 * Manager for game modes: handles lifecycle (start, update) and end-of-match detection.
 */
class GameModeManager(
    val mode: GameMode,
    private val durationMs: Long,
    private val providedStartTime: Long? = null
) {
    private var startTime: Long = 0L
    private var finished: Boolean = false
    
    companion object {
        private const val TAG = "GameModeManager"
    }

    /**
     * Call to begin the match timer.
     */
    fun start() {
        val currentTime = System.currentTimeMillis()
        startTime = providedStartTime ?: currentTime
        finished = false
        
        // Validate that the start time is reasonable
        val timeDiff = startTime - currentTime
        if (timeDiff > 60000L) { // More than 1 minute in the future
            // Log.e(TAG, "GameModeManager: startTime is ${timeDiff}ms in the future. This is likely a timing sync issue!")
            // Log.e(TAG, "GameModeManager: currentTime=$currentTime, startTime=$startTime")
            // Use current time instead of future time to avoid stuck timer
            startTime = currentTime
        } else if (timeDiff > 10000L) { // More than 10 seconds in the future
            // Log.w(TAG, "GameModeManager: startTime is ${timeDiff}ms in the future. This might cause timing issues.")
            // Log.w(TAG, "GameModeManager: currentTime=$currentTime, startTime=$startTime")
        } else if (timeDiff < -durationMs) { // Start time is so far in the past that match should have ended
            // Log.e(TAG, "GameModeManager: startTime is ${-timeDiff}ms in the past (match duration: ${durationMs}ms). Match will end immediately!")
            // Log.e(TAG, "GameModeManager: currentTime=$currentTime, startTime=$startTime")
            // Set finished to true since the match duration has already passed
            finished = true
        } else if (timeDiff < -10000L) { // More than 10 seconds in the past
            // Log.w(TAG, "GameModeManager: startTime is ${-timeDiff}ms in the past. This might cause timing issues.")
            // Log.w(TAG, "GameModeManager: currentTime=$currentTime, startTime=$startTime")
        }
        
        // Log.d(TAG, "GameModeManager started with durationMs=$durationMs, startTime=$startTime, providedStartTime=$providedStartTime")
        // Log.d(TAG, "Current time: $currentTime, time difference: ${timeDiff}ms")
    }

    /**
     * Call each frame to update timer and finish state.
     */
    fun update() {
        if (!finished && System.currentTimeMillis() - startTime >= durationMs) {
            finished = true
        }
    }

    /**
     * True once the match duration has elapsed.
     */
    fun isFinished(): Boolean = finished

    /**
     * How many milliseconds remain (clamped to zero).
     */
    fun timeRemainingMs(): Long {
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - startTime
        val remaining = (durationMs - elapsed).coerceAtLeast(0L)
        
        // Debug logging to track timing issues
        if (remaining > durationMs) {
            // Log.w(TAG, "timeRemainingMs: Remaining time ($remaining) is greater than duration ($durationMs). This suggests a timing issue.")
            // Log.w(TAG, "timeRemainingMs: currentTime=$currentTime, startTime=$startTime, elapsed=$elapsed")
            // Clamp to duration if something went wrong
            return durationMs.coerceAtLeast(0L)
        }
        
        // Log.v(TAG, "timeRemainingMs: currentTime=$currentTime, startTime=$startTime, elapsed=$elapsed, durationMs=$durationMs, remaining=$remaining")
        return remaining
    }
}

enum class GameMode {
    COVERAGE,
    ZONES
} 
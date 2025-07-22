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
    
    // Debug fields for timer freeze detection
    private var lastUpdateTime: Long = 0L
    private var updateCallCount: Long = 0L
    private var lastLogTime: Long = 0L
    private var isTimerHealthy: Boolean = true
    
    companion object {
        private const val TAG = "GameModeManager"
        private const val HEALTH_CHECK_INTERVAL_MS = 5000L // Log health every 5 seconds
        private const val STALE_UPDATE_THRESHOLD_MS = 2000L // Consider timer stale if no updates for 2 seconds
    }

    /**
     * Call to begin the match timer.
     */
    fun start() {
        val currentTime = System.currentTimeMillis()
        startTime = providedStartTime ?: currentTime
        finished = false
        lastUpdateTime = currentTime
        updateCallCount = 0L
        lastLogTime = currentTime
        isTimerHealthy = true
        
        // Validate that the start time is reasonable
        val timeDiff = startTime - currentTime
        if (timeDiff > 60000L) { // More than 1 minute in the future
            Log.e(TAG, "GameModeManager: startTime is ${timeDiff}ms in the future. This is likely a timing sync issue!")
            Log.e(TAG, "GameModeManager: currentTime=$currentTime, startTime=$startTime")
            // Use current time instead of future time to avoid stuck timer
            startTime = currentTime
            Log.w(TAG, "GameModeManager: Corrected startTime to currentTime to avoid timer freeze")
        } else if (timeDiff > 10000L) { // More than 10 seconds in the future
            Log.w(TAG, "GameModeManager: startTime is ${timeDiff}ms in the future. This might cause timing issues.")
            Log.w(TAG, "GameModeManager: currentTime=$currentTime, startTime=$startTime")
        } else if (timeDiff < -durationMs) { // Start time is so far in the past that match should have ended
            Log.e(TAG, "GameModeManager: startTime is ${-timeDiff}ms in the past (match duration: ${durationMs}ms). Match will end immediately!")
            Log.e(TAG, "GameModeManager: currentTime=$currentTime, startTime=$startTime")
            // Set finished to true since the match duration has already passed
            finished = true
        } else if (timeDiff < -10000L) { // More than 10 seconds in the past
            Log.w(TAG, "GameModeManager: startTime is ${-timeDiff}ms in the past. This might cause timing issues.")
            Log.w(TAG, "GameModeManager: currentTime=$currentTime, startTime=$startTime")
        }
        
        Log.d(TAG, "GameModeManager started with durationMs=$durationMs, startTime=$startTime, providedStartTime=$providedStartTime")
        Log.d(TAG, "Current time: $currentTime, time difference: ${timeDiff}ms, mode: $mode")
    }

    /**
     * Call each frame to update timer and finish state.
     */
    fun update() {
        val currentTime = System.currentTimeMillis()
        lastUpdateTime = currentTime
        updateCallCount++
        
        // Periodic health check logging
        if (currentTime - lastLogTime > HEALTH_CHECK_INTERVAL_MS) {
            logTimerHealth(currentTime)
            lastLogTime = currentTime
        }
        
        if (!finished && currentTime - startTime >= durationMs) {
            finished = true
            Log.i(TAG, "Timer finished! Elapsed: ${currentTime - startTime}ms, Duration: ${durationMs}ms")
        }
    }
    
    /**
     * Log timer health status for debugging
     */
    private fun logTimerHealth(currentTime: Long) {
        val elapsed = currentTime - startTime
        val remaining = (durationMs - elapsed).coerceAtLeast(0L)
        
        Log.d(TAG, "TIMER_HEALTH: Updates=${updateCallCount}, Elapsed=${elapsed}ms, Remaining=${remaining}ms, Finished=${finished}")
        
        // Check if timer appears frozen
        if (updateCallCount > 0 && currentTime - lastUpdateTime > STALE_UPDATE_THRESHOLD_MS) {
            Log.w(TAG, "TIMER_FREEZE_DETECTED: Last update was ${currentTime - lastUpdateTime}ms ago!")
            isTimerHealthy = false
        } else {
            isTimerHealthy = true
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
            Log.w(TAG, "timeRemainingMs: Remaining time ($remaining) is greater than duration ($durationMs). This suggests a timing issue.")
            Log.w(TAG, "timeRemainingMs: currentTime=$currentTime, startTime=$startTime, elapsed=$elapsed")
            // Clamp to duration if something went wrong
            return durationMs.coerceAtLeast(0L)
        }
        
        // Log verbose timing info occasionally
        if (updateCallCount % 300 == 0L) { // Every ~5 seconds at 60fps
            Log.v(TAG, "timeRemainingMs: currentTime=$currentTime, startTime=$startTime, elapsed=$elapsed, durationMs=$durationMs, remaining=$remaining")
        }
        
        return remaining
    }
    
    /**
     * Check if the timer appears to be working correctly
     */
    fun isTimerHealthy(): Boolean = isTimerHealthy
    
    /**
     * Get diagnostic information for debugging
     */
    fun getTimerDiagnostics(): String {
        val currentTime = System.currentTimeMillis()
        return "TimerDiagnostics[updates=$updateCallCount, lastUpdate=${currentTime - lastUpdateTime}ms ago, " +
               "started=${startTime > 0}, finished=$finished, healthy=$isTimerHealthy, " +
               "elapsed=${currentTime - startTime}ms, duration=${durationMs}ms]"
    }
}

enum class GameMode {
    COVERAGE,
    ZONES
} 
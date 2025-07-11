package com.spiritwisestudios.inkrollers.campaign

import android.util.Log
import java.util.concurrent.atomic.AtomicLong

/**
 * Performance monitoring system for campaign levels.
 * Tracks frame rates, memory usage, and performance metrics.
 */
class PerformanceMonitor {
    
    companion object {
        private const val TAG = "PerformanceMonitor"
        private const val TARGET_FPS = 60
        private const val FRAME_TIME_TARGET_MS = 1000f / TARGET_FPS
        private const val PERFORMANCE_LOG_INTERVAL_MS = 5000L // Log every 5 seconds
    }
    
    private var frameCount = 0L
    private var lastFrameTime = 0L
    private var lastLogTime = 0L
    private var totalFrameTime = 0L
    private var minFrameTime = Long.MAX_VALUE
    private var maxFrameTime = 0L
    
    // Performance thresholds
    private var lowPerformanceThreshold = 30 // FPS
    private var criticalPerformanceThreshold = 20 // FPS
    
    /**
     * Call this at the start of each frame to track frame timing.
     */
    fun onFrameStart() {
        val currentTime = System.nanoTime()
        
        if (lastFrameTime > 0) {
            val frameTime = currentTime - lastFrameTime
            totalFrameTime += frameTime
            
            if (frameTime < minFrameTime) minFrameTime = frameTime
            if (frameTime > maxFrameTime) maxFrameTime = frameTime
            
            frameCount++
            
            // Log performance periodically
            if (currentTime - lastLogTime > PERFORMANCE_LOG_INTERVAL_MS * 1_000_000) {
                logPerformance()
                lastLogTime = currentTime
            }
        }
        
        lastFrameTime = currentTime
    }
    
    /**
     * Gets the current average FPS.
     */
    fun getAverageFPS(): Float {
        if (frameCount == 0L) return 0f
        val averageFrameTime = totalFrameTime.toFloat() / frameCount
        return 1_000_000_000f / averageFrameTime // Convert nanoseconds to FPS
    }
    
    /**
     * Gets the current frame time in milliseconds.
     */
    fun getCurrentFrameTime(): Float {
        if (lastFrameTime == 0L) return 0f
        val currentTime = System.nanoTime()
        return (currentTime - lastFrameTime) / 1_000_000f // Convert to milliseconds
    }
    
    /**
     * Gets the minimum frame time in milliseconds.
     */
    fun getMinFrameTime(): Float {
        return if (minFrameTime == Long.MAX_VALUE) 0f else minFrameTime / 1_000_000f
    }
    
    /**
     * Gets the maximum frame time in milliseconds.
     */
    fun getMaxFrameTime(): Float {
        return maxFrameTime / 1_000_000f
    }
    
    /**
     * Checks if performance is below acceptable thresholds.
     */
    fun isPerformanceLow(): Boolean {
        val currentFPS = getAverageFPS()
        return currentFPS < lowPerformanceThreshold
    }
    
    /**
     * Checks if performance is critically low.
     */
    fun isPerformanceCritical(): Boolean {
        val currentFPS = getAverageFPS()
        return currentFPS < criticalPerformanceThreshold
    }
    
    /**
     * Logs current performance metrics.
     */
    private fun logPerformance() {
        val avgFPS = getAverageFPS()
        val currentFrameTime = getCurrentFrameTime()
        val minFrameTime = getMinFrameTime()
        val maxFrameTime = getMaxFrameTime()
        
        val performanceLevel = when {
            avgFPS >= TARGET_FPS * 0.9f -> "EXCELLENT"
            avgFPS >= TARGET_FPS * 0.7f -> "GOOD"
            avgFPS >= TARGET_FPS * 0.5f -> "FAIR"
            else -> "POOR"
        }
        
        Log.d(TAG, "Performance: $performanceLevel | " +
                "FPS: ${String.format("%.1f", avgFPS)} | " +
                "Frame Time: ${String.format("%.2f", currentFrameTime)}ms | " +
                "Min: ${String.format("%.2f", minFrameTime)}ms | " +
                "Max: ${String.format("%.2f", maxFrameTime)}ms")
        
        // Log warning if performance is low
        if (isPerformanceCritical()) {
            Log.w(TAG, "CRITICAL PERFORMANCE ISSUE: FPS below $criticalPerformanceThreshold")
        } else if (isPerformanceLow()) {
            Log.w(TAG, "LOW PERFORMANCE: FPS below $lowPerformanceThreshold")
        }
    }
    
    /**
     * Resets all performance metrics.
     */
    fun reset() {
        frameCount = 0L
        lastFrameTime = 0L
        lastLogTime = 0L
        totalFrameTime = 0L
        minFrameTime = Long.MAX_VALUE
        maxFrameTime = 0L
    }
    
    /**
     * Sets performance thresholds for warnings.
     */
    fun setPerformanceThresholds(low: Int, critical: Int) {
        lowPerformanceThreshold = low
        criticalPerformanceThreshold = critical
    }
    
    /**
     * Gets a performance summary as a string.
     */
    fun getPerformanceSummary(): String {
        val avgFPS = getAverageFPS()
        val currentFrameTime = getCurrentFrameTime()
        
        return "FPS: ${String.format("%.1f", avgFPS)} | " +
               "Frame Time: ${String.format("%.2f", currentFrameTime)}ms"
    }
} 
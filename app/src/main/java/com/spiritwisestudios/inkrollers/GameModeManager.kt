package com.spiritwisestudios.inkrollers

/**
 * Manager for game modes: handles lifecycle (start, update) and end-of-match detection.
 */
class GameModeManager(
    val mode: GameMode,
    private val durationMs: Long
) {
    private var startTime: Long = 0L
    private var finished: Boolean = false

    /**
     * Call to begin the match timer.
     */
    fun start() {
        startTime = System.currentTimeMillis()
        finished = false
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
        val elapsed = System.currentTimeMillis() - startTime
        return (durationMs - elapsed).coerceAtLeast(0L)
    }
}

enum class GameMode {
    COVERAGE,
    ZONES
} 
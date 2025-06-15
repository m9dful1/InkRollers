package com.spiritwisestudios.inkrollers

/**
 * Manages match timing and game mode state for Coverage and Zones gameplay modes.
 * 
 * Tracks match duration and provides end-of-match detection for GameView to trigger
 * win condition evaluation. Supports synchronized start times across multiplayer clients.
 */
class GameModeManager(
    val mode: GameMode,
    private val durationMs: Long,
    private val providedStartTime: Long? = null
) {
    private var startTime: Long = 0L
    private var finished: Boolean = false

    /** Begins the match timer using either synchronized or local start time. */
    fun start() {
        startTime = providedStartTime ?: System.currentTimeMillis()
        finished = false
    }

    /** Updates timer state. Called by GameView each frame to check for match completion. */
    fun update() {
        if (!finished && System.currentTimeMillis() - startTime >= durationMs) {
            finished = true
        }
    }

    /** Returns true when match duration has elapsed, triggering win condition evaluation. */
    fun isFinished(): Boolean = finished

    /** Returns remaining match time in milliseconds for TimerHudView display. */
    fun timeRemainingMs(): Long {
        val elapsed = System.currentTimeMillis() - startTime
        return (durationMs - elapsed).coerceAtLeast(0L)
    }
}

/** Available game modes that determine win conditions and HUD displays. */
enum class GameMode {
    COVERAGE,
    ZONES
} 
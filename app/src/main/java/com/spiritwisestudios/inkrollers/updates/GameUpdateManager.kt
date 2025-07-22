package com.spiritwisestudios.inkrollers.updates

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import com.spiritwisestudios.inkrollers.Player
import com.spiritwisestudios.inkrollers.PlayerState
import com.spiritwisestudios.inkrollers.Level
import com.spiritwisestudios.inkrollers.MazeLevel
import com.spiritwisestudios.inkrollers.VirtualJoystick
import com.spiritwisestudios.inkrollers.MultiplayerManager
import com.spiritwisestudios.inkrollers.PaintSurface
import com.spiritwisestudios.inkrollers.GameModeManager
import com.spiritwisestudios.inkrollers.GameMode
import com.spiritwisestudios.inkrollers.InkHudView
import com.spiritwisestudios.inkrollers.CoverageHudView
import com.spiritwisestudios.inkrollers.ZoneHudView
import com.spiritwisestudios.inkrollers.TimerHudView
import com.spiritwisestudios.inkrollers.ZoneOwnershipCalculator
import com.spiritwisestudios.inkrollers.effects.ParticleManager
import com.spiritwisestudios.inkrollers.items.ItemManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages game state updates and coordinates different update cycles.
 * 
 * This class extracts update logic from GameView, providing clean separation
 * of concerns and making the update system more maintainable and testable.
 */
class GameUpdateManager {
    
    companion object {
        private const val TAG = "GameUpdateManager"
        private const val TIMER_STUCK_THRESHOLD = 30 // Consider timer stuck if same value for 30 consecutive checks
        private const val TIMER_CHECK_INTERVAL_MS = 1000L // Check timer health every second
        private const val MAX_TIMER_RECOVERY_ATTEMPTS = 3
    }
    
    // Update timing and throttling
    private var timeSinceLastHudUpdate = 0f
    private val hudUpdateInterval = 0.5f // ~2 times per second
    private var timeSinceLastFirebaseUpdate = 0f
    private val firebaseUpdateInterval = 0.05f // ~20 times per second
    
    // Coverage calculation timing
    private var frameCount = 0
    private var coverageUpdateFrames = 30
    private var coverageStats: Map<Int, Float> = emptyMap()
    
    // Match state management
    private var isMatchReady: Boolean = false
    private var endNotified: Boolean = false
    
    // Timer freeze detection and recovery
    private var lastTimerValue: Long = -1L
    private var timerStuckCount: Int = 0
    private var lastTimerCheckTime: Long = 0L
    private var timerRecoveryAttempts: Int = 0
    private var lastGameModeManagerDiagnostics: String = ""
    
    // Thread-safe timer value storage for main thread access
    @Volatile
    private var currentTimerValueMs: Long = 0L
    @Volatile 
    private var shouldUpdateTimer: Boolean = false
    
    // Callbacks for game events
    var onMatchEnd: ((String) -> Unit)? = null
    var onStopGameLoop: (() -> Unit)? = null
    
    /**
     * Get current timer value - thread-safe access from main UI thread
     * Returns -1 if no timer is active
     */
    fun getCurrentTimerValue(): Long {
        return if (shouldUpdateTimer) currentTimerValueMs else -1L
    }
    
    /**
     * Initialize the update manager with game parameters
     */
    fun initialize(
        matchReady: Boolean = false,
        coverageFrames: Int = 30
    ) {
        isMatchReady = matchReady
        coverageUpdateFrames = coverageFrames
        endNotified = false
        
        // Reset timer freeze detection state
        lastTimerValue = -1L
        timerStuckCount = 0
        lastTimerCheckTime = System.currentTimeMillis()
        timerRecoveryAttempts = 0
        
        Log.d(TAG, "GameUpdateManager initialized with matchReady=$matchReady")
    }
    
    /**
     * Update all game systems for a single frame
     */
    fun update(
        deltaTime: Float,
        localPlayer: Player?,
        localJoystick: VirtualJoystick?,
        currentLevel: Level?,
        surface: PaintSurface,
        players: ConcurrentHashMap<String, Player>,
        multiplayerManager: MultiplayerManager?,
        gameModeManager: GameModeManager?,
        inkHudView: InkHudView?,
        coverageHudView: CoverageHudView?,
        zoneHudView: ZoneHudView?,
        timerHudView: TimerHudView?,
        localPlayerId: String?,
        particleManager: ParticleManager? = null,
        itemManager: ItemManager? = null
    ) {
        // Update different game systems
        updateLocalPlayer(deltaTime, localPlayer, localJoystick, currentLevel, multiplayerManager)
        updateGameElements(deltaTime, currentLevel, surface)
        updateParticles(deltaTime, particleManager)
        updateItems(deltaTime, itemManager, currentLevel, players, surface)
        updateHUDs(deltaTime, localPlayer, inkHudView)
        updateGameMode(deltaTime, gameModeManager, currentLevel, surface, players, coverageHudView, zoneHudView, timerHudView)
    }
    
    /**
     * Update local player movement and Firebase state synchronization
     */
    private fun updateLocalPlayer(
        deltaTime: Float,
        localPlayer: Player?,
        localJoystick: VirtualJoystick?,
        currentLevel: Level?,
        multiplayerManager: MultiplayerManager?
    ) {
        if (localPlayer != null && localJoystick != null && currentLevel != null) {
            // Move local player
            localPlayer.move(localJoystick.directionX, localJoystick.directionY, localJoystick.magnitude, currentLevel, deltaTime)
            
            // Handle campaign-specific interactions
            if (currentLevel is com.spiritwisestudios.inkrollers.campaign.CampaignLevel && localPlayer.isCampaignMode()) {
                val paintSurface = localPlayer.surface
                currentLevel.handlePlayerInteraction(localPlayer, paintSurface)
            }
            
            // Firebase sync only for multiplayer mode
            if (multiplayerManager != null && currentLevel is MazeLevel) {
                // Convert local player's screen position to normalized coordinates
                val (nx, ny) = currentLevel.screenToMazeCoord(localPlayer.x, localPlayer.y)

                // Throttled Firebase Update
                timeSinceLastFirebaseUpdate += deltaTime
                if (timeSinceLastFirebaseUpdate >= firebaseUpdateInterval) {
                    timeSinceLastFirebaseUpdate = 0f
                    // Send local player state to Firebase using normalized coordinates
                    val currentState = PlayerState(
                        normX = nx,
                        normY = ny,
                        color = localPlayer.getColor(),
                        mode = localPlayer.mode,
                        ink = localPlayer.ink,
                        active = true, // Mark as active
                        playerName = localPlayer.playerName, // Pass player name
                        uid = multiplayerManager.getCurrentUserUid() ?: "" // Include UID
                    )
                    multiplayerManager.updateLocalPlayerState(currentState)
                }
            }
        }
    }
    
    /**
     * Update game elements like level and coverage statistics
     */
    private fun updateGameElements(deltaTime: Float, currentLevel: Level?, surface: PaintSurface) {
        // Update level
        currentLevel?.update()
        
        // Periodically update coverage stats
        frameCount++
        if (frameCount >= coverageUpdateFrames) {
            frameCount = 0
            currentLevel?.let { level ->
                try {
                    coverageStats = level.calculateCoverage(surface)
                } catch (e: Exception) {
                    Log.w(TAG, "Error calculating coverage stats", e)
                }
            }
        }
    }
    
    /**
     * Update particle effects
     */
    private fun updateParticles(deltaTime: Float, particleManager: ParticleManager?) {
        particleManager?.update(deltaTime)
    }
    
    /**
     * Update item system
     */
    private fun updateItems(deltaTime: Float, itemManager: ItemManager?, currentLevel: Level?, players: ConcurrentHashMap<String, Player>, surface: PaintSurface) {
        itemManager?.update(deltaTime, currentLevel, players.values.toList(), surface)
    }
    
    /**
     * Update HUD elements based on local player state
     * Enhanced with comprehensive error handling and validation
     */
    private fun updateHUDs(deltaTime: Float, localPlayer: Player?, inkHudView: InkHudView?) {
        try {
            // Validate input parameters
            if (localPlayer == null) {
                Log.v(TAG, "updateHUDs: No local player available")
                return
            }
            
            if (inkHudView == null) {
                Log.v(TAG, "updateHUDs: No ink HUD view available")
                return
            }
            
            // Get ink color with comprehensive validation
            val inkColor = try {
                if (localPlayer.isCampaignMode()) {
                    // Campaign mode - use frequency color with validation
                    try {
                        val isStateValid = localPlayer.validateFrequencyState()
                        if (!isStateValid) {
                            Log.w(TAG, "updateHUDs: Player frequency state was corrected")
                        }
                        localPlayer.getFrequencyColor()
                    } catch (frequencyError: Exception) {
                        Log.e(TAG, "updateHUDs: Error getting frequency color, using fallback", frequencyError)
                        android.graphics.Color.RED // Safe fallback for campaign mode
                    }
                } else {
                    // Regular mode - use standard player color
                    try {
                        localPlayer.getColor()
                    } catch (colorError: Exception) {
                        Log.e(TAG, "updateHUDs: Error getting player color, using fallback", colorError)
                        android.graphics.Color.BLUE // Safe fallback for regular mode
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "updateHUDs: Error determining ink color", e)
                android.graphics.Color.BLUE // Ultimate fallback
            }
            
            // Get ink percentage with validation
            val inkPercent = try {
                localPlayer.getInkPercent()
            } catch (e: Exception) {
                Log.e(TAG, "updateHUDs: Error getting ink percent", e)
                1.0f // Safe fallback - full ink
            }
            
            // Get mode text with validation
            val modeText = try {
                localPlayer.getModeText()
            } catch (e: Exception) {
                Log.e(TAG, "updateHUDs: Error getting mode text", e)
                "PAINT" // Safe fallback
            }
            
            // Update HUD with validated values
            try {
                inkHudView.updateHud(inkPercent, modeText, inkColor)
            } catch (e: Exception) {
                Log.e(TAG, "updateHUDs: Error updating ink HUD display", e)
                // Try one more time with safe fallback values
                try {
                    inkHudView.updateHud(1.0f, "PAINT", android.graphics.Color.BLUE)
                } catch (fallbackError: Exception) {
                    Log.e(TAG, "updateHUDs: Failed to update HUD with fallback values", fallbackError)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "updateHUDs: Unexpected error during HUD update", e)
        }
    }
    
    /**
     * Update game mode management, timer, and mode-specific HUDs
     */
    private fun updateGameMode(
        deltaTime: Float,
        gameModeManager: GameModeManager?,
        currentLevel: Level?,
        surface: PaintSurface,
        players: ConcurrentHashMap<String, Player>,
        coverageHudView: CoverageHudView?,
        zoneHudView: ZoneHudView?,
        timerHudView: TimerHudView?
    ) {
        if (!isMatchReady) {
            Log.v(TAG, "updateGameMode: Match not ready, skipping timer updates")
            return // Only check if match is flagged as ready
        }
        
        gameModeManager?.let { mgr ->
            // Update the manager's internal state (timer) FIRST
            mgr.update()
            
            // Get current timer value for freeze detection
            val currentTimerValue = mgr.timeRemainingMs()
            
            // Detect timer freeze conditions
            detectTimerFreeze(mgr, currentTimerValue)
            
            // SIMPLE THREAD-SAFE TIMER VALUE STORAGE
            // Just store the value - no complex Handler threading
            currentTimerValueMs = currentTimerValue
            shouldUpdateTimer = true
            
            // Log periodically to monitor that values are being stored
            if (currentTimerValue > 0 && currentTimerValue % 30000 < 1000) { // Every 30 seconds
                Log.d(TAG, "Timer value stored for main thread: ${currentTimerValue}ms remaining")
            }
            
            // Debug logging for timer issues - log more frequently at start
            val seconds = (currentTimerValue / 1000).coerceAtLeast(0L)
            if (seconds >= 175 || seconds % 10 == 0L) { // First 5 seconds or every 10 seconds
                Log.d(TAG, "DEBUG: Timer stored - ${currentTimerValue}ms (${seconds}s), shouldUpdate=${shouldUpdateTimer}")
            }

            // Check if finished *after* updating the manager
            if (mgr.isFinished()) {
                if (!endNotified) {
                    endNotified = true
                    Log.i(TAG, "Timer expired, ending match")
                    onStopGameLoop?.invoke()
                    
                    // Ensure match end callback happens on main thread for UI dialogs
                    Handler(Looper.getMainLooper()).post {
                        try {
                            onMatchEnd?.invoke("timer_expired")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error invoking onMatchEnd on main thread", e)
                        }
                    }
                }
            } else {
                // Throttled HUD Update
                timeSinceLastHudUpdate += deltaTime
                if (timeSinceLastHudUpdate >= hudUpdateInterval) {
                    timeSinceLastHudUpdate = 0f
                    updateModeSpecificHUD(mgr, currentLevel, surface, players, coverageHudView, zoneHudView)
                }
            }
        } ?: run {
            Log.w(TAG, "updateGameMode: GameModeManager is null!")
        }
    }
    
    /**
     * Update timer display - can be called from any thread
     * This eliminates cross-thread Handler complexity by handling thread dispatch internally
     */
    fun updateTimerOnMainThread(timerHudView: TimerHudView?) {
        if (shouldUpdateTimer && timerHudView != null) {
            val timerValue = currentTimerValueMs
            
            if (Looper.myLooper() == Looper.getMainLooper()) {
                // Already on main thread - update directly
                try {
                    timerHudView.updateTime(timerValue)
                    shouldUpdateTimer = false // Reset flag after successful update
                    
                    // Log success periodically
                    if (timerValue > 0 && timerValue % 30000 < 1000) { // Every 30 seconds
                        Log.d(TAG, "Timer UI updated directly on main thread: ${timerValue}ms remaining")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating timer on main thread: ${timerValue}ms", e)
                }
            } else {
                // Not on main thread - post to main thread
                try {
                    Handler(Looper.getMainLooper()).post {
                        try {
                            timerHudView.updateTime(timerValue)
                            shouldUpdateTimer = false // Reset flag after successful update
                            
                            // Log success periodically
                            if (timerValue > 0 && timerValue % 30000 < 1000) { // Every 30 seconds
                                Log.d(TAG, "Timer UI updated via Handler post: ${timerValue}ms remaining")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in Handler.post timer update: ${timerValue}ms", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to post timer update to main thread: ${timerValue}ms", e)
                }
            }
        }
    }
    
    /**
     * Detect timer freeze conditions and attempt recovery
     */
    private fun detectTimerFreeze(gameModeManager: GameModeManager, currentTimerValue: Long) {
        val currentTime = System.currentTimeMillis()
        
        // Only check periodically to avoid spam
        if (currentTime - lastTimerCheckTime < TIMER_CHECK_INTERVAL_MS) {
            return
        }
        lastTimerCheckTime = currentTime
        
        // Check if timer value has changed
        if (currentTimerValue == lastTimerValue && currentTimerValue > 0) {
            timerStuckCount++
            
            if (timerStuckCount >= TIMER_STUCK_THRESHOLD) {
                Log.w(TAG, "TIMER_FREEZE_DETECTED: Timer stuck at ${currentTimerValue}ms for ${timerStuckCount} checks")
                
                // Get diagnostics from GameModeManager
                val diagnostics = gameModeManager.getTimerDiagnostics()
                Log.w(TAG, "Timer diagnostics: $diagnostics")
                
                // Check if timer health is bad
                if (!gameModeManager.isTimerHealthy()) {
                    attemptTimerRecovery(gameModeManager, currentTimerValue)
                }
                
                // Reset counter after logging
                timerStuckCount = 0
            }
        } else {
            // Timer is moving, reset stuck counter
            timerStuckCount = 0
        }
        
        lastTimerValue = currentTimerValue
    }
    
    /**
     * Attempt to recover from timer freeze condition
     */
    private fun attemptTimerRecovery(gameModeManager: GameModeManager, frozenValue: Long) {
        if (timerRecoveryAttempts >= MAX_TIMER_RECOVERY_ATTEMPTS) {
            Log.e(TAG, "Maximum timer recovery attempts reached ($MAX_TIMER_RECOVERY_ATTEMPTS), forcing match end")
            if (!endNotified) {
                endNotified = true
                onStopGameLoop?.invoke()
                
                // Ensure forced match end also happens on main thread for UI dialogs
                Handler(Looper.getMainLooper()).post {
                    try {
                        onMatchEnd?.invoke("timer_freeze_forced_end")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error invoking forced onMatchEnd on main thread", e)
                    }
                }
            }
            return
        }
        
        timerRecoveryAttempts++
        
        Log.w(TAG, "TIMER_RECOVERY_ATTEMPT #$timerRecoveryAttempts: Timer frozen at ${frozenValue}ms")
        
        try {
            // Log current state for debugging
            Log.w(TAG, "Recovery: Current diagnostics: ${gameModeManager.getTimerDiagnostics()}")
            
            // Force a manual update call - sometimes the update loop gets stuck
            gameModeManager.update()
            
            // Check if recovery worked
            val newValue = gameModeManager.timeRemainingMs()
            if (newValue != frozenValue) {
                Log.i(TAG, "TIMER_RECOVERY_SUCCESS: Timer recovered, now shows ${newValue}ms")
                timerRecoveryAttempts = 0 // Reset on success
                timerStuckCount = 0
            } else {
                Log.w(TAG, "TIMER_RECOVERY_FAILED: Timer still frozen at ${newValue}ms")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception during timer recovery attempt #$timerRecoveryAttempts", e)
        }
    }
    
    /**
     * Update HUD based on current game mode (Coverage or Zones)
     */
    private fun updateModeSpecificHUD(
        mgr: GameModeManager,
        currentLevel: Level?,
        surface: PaintSurface,
        players: ConcurrentHashMap<String, Player>,
        coverageHudView: CoverageHudView?,
        zoneHudView: ZoneHudView?
    ) {
        when (mgr.mode) {
            GameMode.COVERAGE -> {
                if (currentLevel is MazeLevel) {
                    try {
                        val allStats = currentLevel.calculateCoverage(surface)
                        val activeColors = players.values.map { it.getColor() }.toSet()
                        val activeStats = allStats.filterKeys { it in activeColors }

                        val leftColor = players["player0"]?.getColor()
                        val rightColor = players["player1"]?.getColor()

                        // Move UI updates to main thread to avoid threading exception
                        Handler(Looper.getMainLooper()).post {
                            try {
                                coverageHudView?.updateCoverage(activeStats, leftColor, rightColor)
                                // Hide zone HUD in coverage mode
                                zoneHudView?.visibility = View.GONE
                                coverageHudView?.visibility = View.VISIBLE
                            } catch (e: Exception) {
                                Log.e(TAG, "Error updating coverage HUD on main thread", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error calculating coverage", e)
                    }
                }
            }
            GameMode.ZONES -> {
                if (currentLevel is MazeLevel) {
                    try {
                        val zoneOwnership = ZoneOwnershipCalculator.calculateZoneOwnership(
                            currentLevel, 
                            surface,
                            sampleStep = 10
                        )

                        val leftColor = players["player0"]?.getColor()
                        val rightColor = players["player1"]?.getColor()

                        // Move UI updates to main thread to avoid threading exception
                        Handler(Looper.getMainLooper()).post {
                            try {
                                zoneHudView?.updateZones(zoneOwnership, leftColor, rightColor)
                                // Hide coverage HUD in zones mode
                                coverageHudView?.visibility = View.GONE
                                zoneHudView?.visibility = View.VISIBLE
                            } catch (e: Exception) {
                                Log.e(TAG, "Error updating zone HUD on main thread", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error calculating zone ownership", e)
                    }
                }
            }
        }
    }
    
    /**
     * Set match ready state and log the change for debugging
     */
    fun setMatchReady(ready: Boolean) {
        val wasReady = isMatchReady
        isMatchReady = ready
        Log.d(TAG, "setMatchReady: $wasReady -> $ready")
        
        if (ready && !wasReady) {
            // Reset timer freeze detection when match becomes ready
            lastTimerValue = -1L
            timerStuckCount = 0
            timerRecoveryAttempts = 0
            Log.d(TAG, "Match ready: Reset timer freeze detection state")
        }
    }
    
    /**
     * Get current timer freeze detection state for debugging
     */
    fun getTimerFreezeState(): String {
        return "TimerFreezeState[matchReady=$isMatchReady, lastValue=${lastTimerValue}ms, " +
               "stuckCount=$timerStuckCount, recoveryAttempts=$timerRecoveryAttempts]"
    }
    
    /**
     * Reset update manager state for a new match
     */
    fun reset() {
        timeSinceLastHudUpdate = 0f
        timeSinceLastFirebaseUpdate = 0f
        frameCount = 0
        coverageStats = emptyMap()
        isMatchReady = false
        endNotified = false
        Log.d(TAG, "GameUpdateManager reset")
    }
    
    /**
     * Get current coverage statistics
     */
    fun getCoverageStats(): Map<Int, Float> = coverageStats
    
    /**
     * Check if match is ready
     */
    fun isMatchReady(): Boolean = isMatchReady
    
    /**
     * Check if match end has been notified
     */
    fun hasEndBeenNotified(): Boolean = endNotified
} 
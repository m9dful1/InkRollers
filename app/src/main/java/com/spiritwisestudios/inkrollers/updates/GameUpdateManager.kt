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
    
    // Callbacks for game events
    var onMatchEnd: ((String) -> Unit)? = null
    var onStopGameLoop: (() -> Unit)? = null
    
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
        Log.d(TAG, "GameUpdateManager initialized")
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
     */
    private fun updateHUDs(deltaTime: Float, localPlayer: Player?, inkHudView: InkHudView?) {
        // Update ink HUD based on local player
        localPlayer?.let { 
            inkHudView?.updateHud(it.getInkPercent(), it.getModeText()) 
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
        if (!isMatchReady) return // Only check if match is flagged as ready
        
        gameModeManager?.let { mgr ->
            // Update countdown timer
            timerHudView?.updateTime(mgr.timeRemainingMs())
            mgr.update() // Update the manager's internal state (timer)

            // Check if finished *after* updating the manager
            if (mgr.isFinished()) {
                if (!endNotified) {
                    endNotified = true
                    onStopGameLoop?.invoke()
                    onMatchEnd?.invoke("timer_expired")
                }
            } else {
                // Throttled HUD Update
                timeSinceLastHudUpdate += deltaTime
                if (timeSinceLastHudUpdate >= hudUpdateInterval) {
                    timeSinceLastHudUpdate = 0f
                    updateModeSpecificHUD(mgr, currentLevel, surface, players, coverageHudView, zoneHudView)
                }
            }
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
     * Set match ready state
     */
    fun setMatchReady(ready: Boolean) {
        isMatchReady = ready
        if (ready) {
            endNotified = false
        }
        Log.d(TAG, "Match ready state set to: $ready")
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
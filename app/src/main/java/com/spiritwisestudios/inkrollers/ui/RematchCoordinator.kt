package com.spiritwisestudios.inkrollers.ui

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleCoroutineScope
import com.spiritwisestudios.inkrollers.AudioManager
import com.spiritwisestudios.inkrollers.GameView
import com.spiritwisestudios.inkrollers.MultiplayerManager
import com.spiritwisestudios.inkrollers.Player
import com.spiritwisestudios.inkrollers.PlayerState
import com.spiritwisestudios.inkrollers.Level
import com.spiritwisestudios.inkrollers.MazeLevel
import com.spiritwisestudios.inkrollers.GameMode
import com.spiritwisestudios.inkrollers.HomeActivity
import com.spiritwisestudios.inkrollers.model.PlayerProfile
import com.spiritwisestudios.inkrollers.repository.ProfileRepository
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Handles all rematch-related logic including state management, profile loading,
 * color assignment, and Firebase coordination.
 * 
 * This class encapsulates the complex rematch flow that was previously scattered
 * throughout MainActivity, providing clean separation of concerns.
 */
class RematchCoordinator(
    private val activity: AppCompatActivity,
    private val dialogManager: DialogManager
) {
    
    companion object {
        private const val TAG = "RematchCoordinator"
        private val NEON_GREEN = Color.parseColor("#39FF14")
        private val NEON_BLUE = Color.parseColor("#1F51FF")
    }
    
    private val audioManager = AudioManager.getInstance(activity)
    
    // Dependencies - must be initialized before use
    private lateinit var multiplayerManager: MultiplayerManager
    private lateinit var gameView: GameView
    private lateinit var gameSetupController: GameSetupController
    private lateinit var lifecycleScope: LifecycleCoroutineScope
    
    // Rematch state management
    private var rematchInProgressHandled = false
    
    // Callbacks
    var onMatchStarted: (() -> Unit)? = null
    var onRematchError: ((String) -> Unit)? = null
    
    /**
     * Initialize the RematchCoordinator with required dependencies
     */
    fun initialize(
        multiplayerManager: MultiplayerManager,
        gameView: GameView,
        gameSetupController: GameSetupController,
        lifecycleScope: LifecycleCoroutineScope
    ) {
        this.multiplayerManager = multiplayerManager
        this.gameView = gameView
        this.gameSetupController = gameSetupController
        this.lifecycleScope = lifecycleScope
    }
    
    /**
     * Set up rematch-related callbacks with MultiplayerManager
     */
    fun setupRematchCallbacks() {
        // Listen for rematch decision
        multiplayerManager.onRematchDecision = { bothYes ->
            Handler(Looper.getMainLooper()).post {
                if (!bothYes) {
                    // Show dialog that the other player declined rematch, then finish
                    dialogManager.showRematchDeclinedDialog(activity) { 
                        activity.finish() 
                    }
                }
                // If bothYes, do nothing here; wait for onRematchStartSignal
            }
        }

        // Listen for rematch start signal (after both YES)
        multiplayerManager.onRematchStartSignal = {
            Handler(Looper.getMainLooper()).post {
                if (!rematchInProgressHandled) {
                    rematchInProgressHandled = true
                    startRematchFlow()
                } else {
                    Log.d(TAG, "Rematch already in progress, ignoring duplicate signal.")
                }
            }
        }
    }
    
    /**
     * Set up the match end callback for GameView to show rematch dialog
     */
    fun setupMatchEndCallback() {
        gameView.onMatchEnd = { didWin ->
            Handler(Looper.getMainLooper()).post { 
                showRematchDialog(didWin)
            }
        }
    }
    
    /**
     * Show rematch dialog using DialogManager
     */
    private fun showRematchDialog(didWin: Boolean) {
        // Stop background music when match ends
        audioManager.stopBackgroundMusic()
        
        // Play win/lose sound effect
        if (didWin) {
            audioManager.playSound(AudioManager.SoundType.MATCH_END_WIN)
        } else {
            audioManager.playSound(AudioManager.SoundType.MATCH_END_LOSE)
        }
        
        dialogManager.showRematchDialog(activity, didWin) { wantsRematch ->
            Log.d(TAG, "Rematch dialog: ${if (wantsRematch) "YES" else "NO"} selected")
            multiplayerManager.sendRematchAnswer(wantsRematch)
        }
    }
    
    /**
     * Start the complete rematch flow: reset state, show countdown, start match
     */
    private fun startRematchFlow() {
        Log.d(TAG, "Starting rematch flow")
        resetMatchForRematch {
            showRematchCountdownAndStart()
        }
    }
    
    /**
     * Reset state for rematch, then execute callback
     */
    private fun resetMatchForRematch(onComplete: () -> Unit) {
        Log.d(TAG, "Resetting match for rematch. Beginning rematch reset flow.")
        
        // 1. Stop the old game thread and wait for it
        gameView.stopThread()
        
        // 2. Clear non-player Firebase state
        Log.d(TAG, "Clearing Firebase paint/rematch state...")
        multiplayerManager.clearPaintActions()
        multiplayerManager.clearRematchAnswers()
        
        // 3. Load profiles and reset player states
        resetPlayerStatesForRematch(onComplete)
    }
    
    /**
     * Reset all player states in Firebase for rematch, handling profile loading and color assignment
     */
    private fun resetPlayerStatesForRematch(onComplete: () -> Unit) {
        val uid = Firebase.auth.currentUser?.uid
        if (uid == null) {
            Log.e(TAG, "Cannot reset for rematch: User not authenticated")
            onRematchError?.invoke("Error: User not authenticated")
            return
        }
        
        // Load local player profile first to ensure we have their preferences
        lifecycleScope.launch {
            try {
                // Load profile for future use (currently using fallback)
                ProfileRepository.loadPlayerProfile(uid)
                
                // Calculate and Reset Player States in Firebase
                Log.d(TAG, "Calculating and resetting Firebase player states...")
                val currentLevel = gameView.getCurrentLevel()
                val playerIds = gameView.getActivePlayerIds()
                val initialStates = mutableMapOf<String, PlayerState>()
                
                // Before proceeding, fetch PlayerState from Firebase for all active players
                multiplayerManager.getPlayersState { playerStatesMap ->
                    Log.d(TAG, "Fetched all player states from Firebase: ${playerStatesMap.keys}")
                    
                    // Collect UIDs from fetched player states
                    val uidsToLoad = mutableSetOf<String>()
                    playerStatesMap.forEach { (_, playerState) ->
                        if (playerState != null && playerState.uid.isNotEmpty()) {
                            uidsToLoad.add(playerState.uid)
                        } else {
                            Log.w(TAG, "Player state is null or has empty UID.")
                        }
                    }

                    if (uidsToLoad.isEmpty()) {
                        Log.w(TAG, "No valid player UIDs found from Firebase states for rematch setup.")
                        // Fallback to default colors/names if no profiles can be loaded
                        assignDefaultColorsAndNames(playerIds, initialStates, currentLevel)
                        completeRematchReset(onComplete)
                        return@getPlayersState
                    }

                    // TODO: Convert to coroutines - temporarily using fallback
                    Log.d(TAG, "TODO: Profile loading in rematch needs coroutine conversion")
                    assignDefaultColorsAndNames(playerIds, initialStates, currentLevel)
                    completeRematchReset(onComplete)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading profile during rematch", e)
                activity.runOnUiThread {
                    Toast.makeText(activity, "Error loading profile, using defaults", Toast.LENGTH_SHORT).show()
                }
                // Fallback to defaults
                val currentLevel = gameView.getCurrentLevel()
                val playerIds = gameView.getActivePlayerIds()
                val initialStates = mutableMapOf<String, PlayerState>()
                assignDefaultColorsAndNames(playerIds, initialStates, currentLevel)
                completeRematchReset(onComplete)
            }
        }
    }
    
    /**
     * Complete the rematch reset by initializing game and setting local player
     */
    private fun completeRematchReset(onComplete: () -> Unit) {
        activity.runOnUiThread {
            gameView.clearPaintSurface()
            gameView.initGame(gameSetupController.getMazeComplexity())
            
            val localPlayerId = gameSetupController.getLocalPlayerId()
            if (localPlayerId != null) {
                // For now, get initial state that was just set in Firebase
                val playerIndex = try {
                    localPlayerId.replace("player", "").toInt()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse player index from $localPlayerId", e)
                    0
                }
                val defaultColor = if (playerIndex == 0) NEON_GREEN else NEON_BLUE
                val defaultName = "Player ${playerIndex + 1}"
                
                gameView.setLocalPlayerId(localPlayerId, defaultColor, defaultName)
            }
            
            onComplete()
        }
    }
    
    /**
     * Assign default colors and names if profiles can't be loaded
     */
    private fun assignDefaultColorsAndNames(
        playerIds: Set<String>, 
        initialStates: MutableMap<String, PlayerState>, 
        currentLevel: Level?
    ) {
        Log.d(TAG, "Assigning default colors and names for rematch.")
        if (currentLevel !is MazeLevel) {
            Log.e(TAG, "Cannot assign default states: currentLevel is not MazeLevel or null")
            return
        }
        
        playerIds.forEach { playerId ->
            val playerIndex = try {
                playerId.replace("player", "").toInt()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse index for $playerId in assignDefaultColorsAndNames, using 0", e)
                0
            }
            
            val startPosScreen = currentLevel.getPlayerStartPosition(playerIndex)
            val (normX, normY) = currentLevel.screenToMazeCoord(startPosScreen.first, startPosScreen.second)
            val defaultColor = if (playerIndex == 0) NEON_GREEN else NEON_BLUE
            val defaultName = "Player ${playerIndex + 1}"
            
            initialStates[playerId] = PlayerState(
                normX = normX,
                normY = normY,
                color = defaultColor,
                mode = 0,
                ink = Player.MAX_INK,
                active = true,
                playerName = defaultName,
                uid = multiplayerManager.getCurrentUserUid() ?: ""
            )
        }
        
        Log.d(TAG, "Default initial states created: $initialStates")
        // Update Firebase with default states
        multiplayerManager.resetAllPlayerStatesFirebase(initialStates)
    }
    
    /**
     * Assign colors and names based on profiles, handling duplicates
     */
    private fun assignColorsAndNamesForRematch(
        playerIds: Set<String>, 
        playerProfiles: Map<String, PlayerProfile?>, 
        initialStates: MutableMap<String, PlayerState>, 
        currentLevel: Level?
    ) {
        Log.d(TAG, "Assigning colors and names based on profiles for rematch.")
        if (currentLevel !is MazeLevel) {
            Log.e(TAG, "Cannot assign states based on profiles: currentLevel is not MazeLevel or null")
            // Fallback to defaults
            assignDefaultColorsAndNames(playerIds, initialStates, currentLevel)
            return
        }

        val playerColors = mutableMapOf<String, Int>()
        val chosenColors = mutableSetOf<Int>()
        // Random for potential future tie-breaking logic

        // Attempt to assign first favorite colors
        playerIds.sorted().forEach { playerId ->
            val profile = playerProfiles[playerId]
            val favoriteColors = profile?.favoriteColors ?: emptyList()
            
            var assignedColor: Int? = null
            // Try first favorite color
            if (favoriteColors.isNotEmpty() && favoriteColors[0] !in chosenColors) {
                assignedColor = favoriteColors[0]
            }

            if (assignedColor != null) {
                playerColors[playerId] = assignedColor
                chosenColors.add(assignedColor)
            }
        }

        // Assign remaining colors, handling duplicates and using second preferences/defaults
        playerIds.sorted().forEach { playerId ->
            if (!playerColors.containsKey(playerId)) {
                val profile = playerProfiles[playerId]
                val favoriteColors = profile?.favoriteColors ?: emptyList()
                val playerIndex = try { 
                    playerId.replace("player", "").toInt() 
                } catch (e: Exception) { 
                    Log.e(TAG, "Failed to parse index for $playerId in assignColorsAndNamesForRematch (fallback), using 0", e)
                    0 
                }
                val defaultColor = if (playerIndex == 0) NEON_GREEN else NEON_BLUE

                var assignedColor: Int? = null
                // Try second favorite color if available and not chosen
                if (favoriteColors.size > 1 && favoriteColors[1] !in chosenColors) {
                    assignedColor = favoriteColors[1]
                }

                // If still no color assigned, find any available color from preferences or use default
                if (assignedColor == null) {
                    assignedColor = favoriteColors.firstOrNull { it !in chosenColors } ?: defaultColor
                }
                
                val finalAssignedColor = assignedColor
                if (finalAssignedColor != null) {
                    playerColors[playerId] = finalAssignedColor
                    chosenColors.add(finalAssignedColor)
                } else {
                    playerColors[playerId] = defaultColor
                    chosenColors.add(defaultColor)
                    Log.w(TAG, "Failed to assign color for $playerId, using default.")
                }
            }

            // Create initial state for this player
            val playerIndex = try { 
                playerId.replace("player", "").toInt() 
            } catch (e: Exception) { 
                Log.e(TAG, "Failed to parse index for $playerId in assignColorsAndNamesForRematch (state creation), using 0", e)
                0 
            }
            val startPosScreen = currentLevel.getPlayerStartPosition(playerIndex)
            val (normX, normY) = currentLevel.screenToMazeCoord(startPosScreen.first, startPosScreen.second)
            val playerName = playerProfiles[playerId]?.playerName ?: "Player ${playerIndex + 1}"
            val uid = playerProfiles[playerId]?.uid ?: multiplayerManager.getCurrentUserUid() ?: ""

            initialStates[playerId] = PlayerState(
                normX = normX,
                normY = normY,
                color = playerColors[playerId] ?: (if (playerIndex == 0) NEON_GREEN else NEON_BLUE),
                mode = 0,
                ink = Player.MAX_INK,
                active = true,
                playerName = playerName,
                uid = uid
            )
        }
        
        Log.d(TAG, "Final initial states for rematch: $initialStates")
        // Update Firebase with the determined states
        multiplayerManager.resetAllPlayerStatesFirebase(initialStates)
    }
    
    /**
     * Show countdown and start the rematch after reset
     */
    private fun showRematchCountdownAndStart() {
        val localPlayerId = gameSetupController.getLocalPlayerId()
        gameSetupController.startPreMatchCountdown(isHost = (localPlayerId == "player0")) {
            Log.d(TAG, "Countdown finished, starting rematch match.")
            rematchInProgressHandled = false // Reset the flag after match actually starts
            onMatchStarted?.invoke()
        }
    }
} 
package com.spiritwisestudios.inkrollers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

/**
 * Manages persistent game state across app lifecycle events.
 * Handles storing and retrieving active game information when the app goes to background.
 */
class GameStateManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val TAG = "GameStateManager"
        private const val PREFS_NAME = "inkrollers_game_state"
        private const val KEY_ACTIVE_GAME = "active_game"
        private const val KEY_IS_INTENTIONAL_EXIT = "is_intentional_exit"
    }
    
    /**
     * Data class to hold persistent game state information
     */
    data class GameState(
        val gameId: String,
        val localPlayerId: String,
        val matchDurationMs: Long,
        val mazeComplexity: String,
        val gameMode: String,
        val isPrivateMatch: Boolean,
        val isHost: Boolean,
        val playerColor: Int?,
        val playerName: String,
        val playerUid: String,
        val timestampSaved: Long = System.currentTimeMillis()
    )
    
    /**
     * Save the current active game state to persistent storage
     */
    fun saveActiveGameState(
        gameId: String,
        localPlayerId: String,
        matchDurationMs: Long,
        mazeComplexity: String,
        gameMode: String,
        isPrivateMatch: Boolean,
        isHost: Boolean,
        playerColor: Int?,
        playerName: String,
        playerUid: String
    ) {
        try {
            val gameState = GameState(
                gameId = gameId,
                localPlayerId = localPlayerId,
                matchDurationMs = matchDurationMs,
                mazeComplexity = mazeComplexity,
                gameMode = gameMode,
                isPrivateMatch = isPrivateMatch,
                isHost = isHost,
                playerColor = playerColor,
                playerName = playerName,
                playerUid = playerUid
            )
            
            val jsonString = gson.toJson(gameState)
            prefs.edit()
                .putString(KEY_ACTIVE_GAME, jsonString)
                .apply()
            
            Log.d(TAG, "Saved active game state: $gameId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save game state", e)
        }
    }
    
    /**
     * Retrieve the saved active game state
     */
    fun getActiveGameState(): GameState? {
        return try {
            val jsonString = prefs.getString(KEY_ACTIVE_GAME, null)
            if (jsonString != null) {
                val gameState = gson.fromJson(jsonString, GameState::class.java)
                Log.d(TAG, "Retrieved active game state: ${gameState.gameId}")
                gameState
            } else {
                Log.d(TAG, "No active game state found")
                null
            }
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse saved game state", e)
            clearActiveGameState()
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve game state", e)
            null
        }
    }
    
    /**
     * Clear the saved active game state
     */
    fun clearActiveGameState() {
        prefs.edit()
            .remove(KEY_ACTIVE_GAME)
            .apply()
        Log.d(TAG, "Cleared active game state")
    }
    
    /**
     * Check if there's an active game that should be rejoined
     * Returns true if there's a recent game state (within the last hour)
     */
    fun hasActiveGameToRejoin(): Boolean {
        val gameState = getActiveGameState()
        if (gameState == null) {
            return false
        }
        
        // Check if the saved state is recent (within 1 hour)
        val currentTime = System.currentTimeMillis()
        val timeDifference = currentTime - gameState.timestampSaved
        val oneHourInMs = 60 * 60 * 1000L
        
        return timeDifference < oneHourInMs
    }
    
    /**
     * Mark that the user is intentionally exiting the game
     * This prevents rejoin attempts when the user explicitly leaves
     */
    fun markIntentionalExit() {
        prefs.edit()
            .putBoolean(KEY_IS_INTENTIONAL_EXIT, true)
            .apply()
        Log.d(TAG, "Marked intentional exit")
    }
    
    /**
     * Check if the last exit was intentional
     */
    fun wasIntentionalExit(): Boolean {
        return prefs.getBoolean(KEY_IS_INTENTIONAL_EXIT, false)
    }
    
    /**
     * Clear the intentional exit flag (call when starting a new game)
     */
    fun clearIntentionalExit() {
        prefs.edit()
            .remove(KEY_IS_INTENTIONAL_EXIT)
            .apply()
        Log.d(TAG, "Cleared intentional exit flag")
    }
    
    /**
     * Check if a game state is still valid for rejoining
     * Considers both timestamp and intentional exit status
     */
    fun shouldAttemptRejoin(): Boolean {
        return hasActiveGameToRejoin() && !wasIntentionalExit()
    }
} 
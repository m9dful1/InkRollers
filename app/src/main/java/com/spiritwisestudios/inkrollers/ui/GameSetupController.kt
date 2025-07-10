package com.spiritwisestudios.inkrollers.ui

import android.graphics.Color
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.spiritwisestudios.inkrollers.AudioManager
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase
import com.spiritwisestudios.inkrollers.GameStateManager
import com.spiritwisestudios.inkrollers.GameView
import com.spiritwisestudios.inkrollers.HomeActivity
import com.spiritwisestudios.inkrollers.MultiplayerManager
import com.spiritwisestudios.inkrollers.Player
import com.spiritwisestudios.inkrollers.PlayerState
import com.spiritwisestudios.inkrollers.repository.ProfileRepository
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.launch
import javax.inject.Inject

@ActivityScoped
class GameSetupController @Inject constructor(
    private val activity: AppCompatActivity,
    private val dialogManager: DialogManager
) {
    companion object {
        private const val TAG = "GameSetupController"
        private val NEON_GREEN = Color.parseColor("#39FF14")
        private val NEON_BLUE = Color.parseColor("#1F51FF")
    }

    private val audioManager = AudioManager.getInstance(activity)
    private lateinit var multiplayerManager: MultiplayerManager
    private lateinit var gameView: GameView
    private lateinit var gameStateManager: GameStateManager
    
    // Game setup state
    private var localPlayerId: String? = null
    private var matchDurationMs: Long = 180000L // 3 minute match
    private var mazeComplexity: String = HomeActivity.COMPLEXITY_HIGH // Default High
    private var gameMode: String = HomeActivity.GAME_MODE_COVERAGE // Default Coverage
    private var isPrivateMatch: Boolean = false // Default to public
    private var matchStartTime: Long? = null

    // Callbacks for setup events
    var onGameSetupComplete: ((String) -> Unit)? = null
    var onSetupError: ((String) -> Unit)? = null
    var onMatchStart: (() -> Unit)? = null

    fun initialize(
        multiplayerManager: MultiplayerManager,
        gameView: GameView,
        gameStateManager: GameStateManager
    ) {
        this.multiplayerManager = multiplayerManager
        this.gameView = gameView
        this.gameStateManager = gameStateManager
    }

    /**
     * Handles intent extras and starts the appropriate game flow (host or join)
     */
    fun handleGameSetup(
        mode: String?,
        gameId: String?,
        timeLimit: Int?,
        complexity: String?,
        gameMode: String?,
        isPrivate: Boolean?
    ) {
        Log.d(TAG, "Received mode: $mode")

        if (mode == HomeActivity.MODE_HOST) {
            matchDurationMs = (timeLimit ?: 3) * 60000L
            mazeComplexity = complexity ?: HomeActivity.COMPLEXITY_HIGH
            this.gameMode = gameMode ?: HomeActivity.GAME_MODE_COVERAGE
            isPrivateMatch = isPrivate ?: false
            Log.d(TAG, "Host selected settings: Duration=${matchDurationMs}ms, Complexity=$mazeComplexity, GameMode=${this.gameMode}, Private=$isPrivateMatch")
        }

        // Load player profile to get favorite color and name before starting game flow
        val uid = Firebase.auth.currentUser?.uid
        if (uid != null) {
            activity.lifecycleScope.launch {
                try {
                    val profile = ProfileRepository.loadPlayerProfile(uid)
                    val playerColor = if (profile?.favoriteColors?.isNotEmpty() == true) {
                        // Use the player's first favorite color if available
                        profile.favoriteColors[0]
                    } else {
                        // Fallback to default colors based on mode if no profile or no favorite colors
                        if (mode == HomeActivity.MODE_HOST) NEON_GREEN else NEON_BLUE
                    }
                    
                    val playerName = profile?.playerName ?: "Player ${if (mode == HomeActivity.MODE_HOST) 1 else 2}"

                    // Create initial state with player name and UID
                    val initialState = PlayerState(
                        color = playerColor,
                        playerName = playerName,
                        uid = uid
                    )

                    when (mode) {
                        HomeActivity.MODE_HOST -> hostGame(initialState)
                        HomeActivity.MODE_JOIN -> joinGame(initialState, gameId)
                        else -> {
                            Log.e(TAG, "Invalid or missing mode specified.")
                            onSetupError?.invoke("Error: Invalid mode")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading profile during game initiation", e)
                    handleProfileLoadError(mode, uid)
                }
            }
        } else {
            Log.e(TAG, "User not authenticated during game initiation.")
            onSetupError?.invoke("Error: User not authenticated")
        }
    }

    /**
     * Attempts to rejoin an existing game from saved state
     */
    fun attemptRejoinExistingGame() {
        val savedState = gameStateManager.getActiveGameState()
        if (savedState == null) {
            Log.w(TAG, "No saved game state found for rejoin attempt")
            return
        }
        
        Log.d(TAG, "Attempting to rejoin game: ${savedState.gameId} as ${savedState.localPlayerId}")
        
        // Restore the saved game settings
        matchDurationMs = savedState.matchDurationMs
        mazeComplexity = savedState.mazeComplexity
        gameMode = savedState.gameMode
        isPrivateMatch = savedState.isPrivateMatch
        
        // Create initial player state for rejoin
        val playerColor = savedState.playerColor ?: (if (savedState.localPlayerId == "player0") NEON_GREEN else NEON_BLUE)
        val initialState = PlayerState(
            normX = 0.5f, // Default position, will be updated from Firebase
            normY = 0.5f,
            color = playerColor,
            mode = 0,
            ink = Player.MAX_INK,
            active = true,
            playerName = savedState.playerName,
            uid = savedState.playerUid
        )
        
        // Show reconnecting dialog
        dialogManager.showReconnectingDialog(activity)
        
        // Attempt to rejoin the specific game using the specialized rejoin method
        multiplayerManager.rejoinGame(initialState, savedState.gameId, savedState.localPlayerId) { success, joinedGameId, gameSettings ->
            activity.runOnUiThread {
                dialogManager.dismissWaitingDialog()
            }
            
            if (success && joinedGameId != null) {
                Log.d(TAG, "Successfully rejoined game: $joinedGameId")
                localPlayerId = multiplayerManager.localPlayerId
                
                // Set local player ID with saved color and name
                gameView.setLocalPlayerId(localPlayerId, playerColor, savedState.playerName)
                
                // Apply game settings (should match saved state)
                gameSettings?.let {
                    matchDurationMs = it.durationMs
                    mazeComplexity = it.complexity
                    gameMode = it.gameMode
                }
                
                activity.runOnUiThread {
                    Toast.makeText(activity, "Reconnected to game: $joinedGameId", Toast.LENGTH_SHORT).show()
                    
                    // Check if game has already started
                    checkGameStartedStatusForRejoin(savedState)
                }
                
                // Clear the saved state since we successfully rejoined
                gameStateManager.clearActiveGameState()
            } else {
                Log.w(TAG, "Failed to rejoin saved game: ${savedState.gameId}")
                // Clear the stale game state and proceed with normal flow
                gameStateManager.clearActiveGameState()
                activity.runOnUiThread {
                    Toast.makeText(activity, "Could not reconnect to previous game. Starting new game search.", Toast.LENGTH_SHORT).show()
                }
                onSetupError?.invoke("Failed to rejoin previous game")
            }
        }
    }

    /**
     * Hosts a new game
     */
    private fun hostGame(initialState: PlayerState) {
        multiplayerManager.hostGame(initialState, matchDurationMs, mazeComplexity, gameMode, isPrivateMatch) { success, gameId, gameSettings ->
            if (success && gameId != null) {
                localPlayerId = multiplayerManager.localPlayerId
                Log.d(TAG, "Host game successful. Game ID: $gameId. Settings: Duration=${gameSettings?.durationMs}, Complexity=${gameSettings?.complexity}, GameMode=${gameSettings?.gameMode}")
                        
                // Set local player ID with the determined color and name
                gameView.setLocalPlayerId("player0", initialState.color, initialState.playerName)
                
                setupHostListeners()
                
                // Show waiting dialog and toast
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        dialogManager.showWaitingForPlayersDialog(activity)
                        Toast.makeText(activity, "Hosting Game: $gameId", Toast.LENGTH_LONG).show()
                    } 
                }
                
                Log.i(TAG, "Hosting setup complete. Game ID: $gameId, Player ID: $localPlayerId")
                onGameSetupComplete?.invoke(gameId)
            } else {
                Log.e(TAG, "Failed to host game.")
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        Toast.makeText(activity, "Failed to host game", Toast.LENGTH_SHORT).show()
                    } 
                }
                onSetupError?.invoke("Failed to host game")
            }
        }
    }

    /**
     * Joins an existing game or a random game
     */
    private fun joinGame(initialState: PlayerState, gameId: String?) {
        if (gameId != null) {
            // Join specific game
            joinSpecificGame(initialState, gameId)
        } else {
            // Join random game
            joinRandomGame(initialState)
        }
    }

    private fun joinSpecificGame(initialState: PlayerState, gameId: String) {
        multiplayerManager.joinGame(initialState, gameId) { success, joinedGameId, gameSettings ->
            if (success && joinedGameId != null) {
                localPlayerId = multiplayerManager.localPlayerId
                gameView.setLocalPlayerId(localPlayerId, initialState.color, initialState.playerName)

                // Apply game settings received from Firebase
                gameSettings?.let {
                    matchDurationMs = it.durationMs
                    mazeComplexity = it.complexity
                    gameMode = it.gameMode
                    Log.d(TAG, "Joined game with settings: Duration=${matchDurationMs}ms, Complexity=$mazeComplexity, GameMode=${gameMode}")
                }
                
                setupJoinerListeners()
                
                // Show waiting dialog until host starts
                activity.runOnUiThread {
                    dialogManager.showWaitingForHostDialog(activity)
                    Toast.makeText(activity, "Joined Game: $joinedGameId as $localPlayerId", Toast.LENGTH_LONG).show()
                }
                
                Log.i(TAG, "Joining successful. Game ID: $joinedGameId, Player ID: $localPlayerId")
                onGameSetupComplete?.invoke(joinedGameId)
            } else {
                Log.e(TAG, "Failed to join game $gameId.")
                activity.runOnUiThread {
                    Toast.makeText(activity, "Failed to join game $gameId", Toast.LENGTH_SHORT).show()
                }
                onSetupError?.invoke("Failed to join game $gameId")
            }
        }
    }

    private fun joinRandomGame(initialState: PlayerState) {
        // Attempt to join a random game instead
        activity.runOnUiThread {
            Toast.makeText(activity, "Searching for an available game...", Toast.LENGTH_SHORT).show()
        }
        
        multiplayerManager.joinGame(initialState, null) { success, joinedGameId, gameSettings ->
            if (success && joinedGameId != null) {
                localPlayerId = multiplayerManager.localPlayerId
                gameView.setLocalPlayerId(localPlayerId, initialState.color, initialState.playerName)

                // Apply game settings received from Firebase
                gameSettings?.let {
                    matchDurationMs = it.durationMs
                    mazeComplexity = it.complexity
                    gameMode = it.gameMode
                    Log.d(TAG, "Joined random game with settings: Duration=${matchDurationMs}ms, Complexity=$mazeComplexity, GameMode=${gameMode}")
                }
                
                setupJoinerListeners()
                
                // Show the game ID that was joined
                activity.runOnUiThread {
                    Toast.makeText(activity, "Joined Random Game: $joinedGameId as $localPlayerId", Toast.LENGTH_LONG).show()
                    dialogManager.showWaitingForHostDialog(activity)
                }
                
                Log.i(TAG, "Random joining successful. Game ID: $joinedGameId, Player ID: $localPlayerId")
                onGameSetupComplete?.invoke(joinedGameId)
            } else {
                Log.e(TAG, "Failed to join any random game.")
                activity.runOnUiThread {
                    Toast.makeText(activity, "No available games found. Try hosting a game instead.", Toast.LENGTH_SHORT).show()
                }
                onSetupError?.invoke("No available games found")
            }
        }
    }

    /**
     * Sets up listeners for the host
     */
    private fun setupHostListeners() {
        // Set up player count listener *immediately* after confirmation
        multiplayerManager.onPlayerCountChanged = { count ->
            Log.d(TAG, "Host: onPlayerCountChanged received count: $count")
            if (count >= 2) {
                // Play player join sound when second player joins
                audioManager.playSound(AudioManager.SoundType.PLAYER_JOIN)
                
                // Only trigger once
                Log.d(TAG, "Host: Player count >= 2, triggering countdown.")
                // Now that all players are present, attach the rematch listener so expected count is accurate
                multiplayerManager.setupRematchListener()
                multiplayerManager.onPlayerCountChanged = null // Nullify listener *before* UI action
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        dialogManager.dismissWaitingDialog()
                        startPreMatchCountdown(isHost = true)
                    } else {
                        Log.w(TAG, "Host: Activity finishing, cannot start countdown.")
                    }
                }
            } else {
                Log.d(TAG, "Host: Player count is $count, waiting for more players.")
            }
        }
        Log.d(TAG, "Host: Player count listener attached.")
    }

    /**
     * Sets up listeners for joiners
     */
    private fun setupJoinerListeners() {
        multiplayerManager.onMatchStartRequested = {
            // Only trigger once
            multiplayerManager.onMatchStartRequested = null
            activity.runOnUiThread {
                dialogManager.dismissWaitingDialog()
                startPreMatchCountdown(isHost = false)
            }
        }
    }

    /**
     * Starts the pre-match countdown
     */
    fun startPreMatchCountdown(isHost: Boolean, onCountdownFinished: (() -> Unit)? = null) {
        dialogManager.showCountdownDialog(
            activity = activity,
            isHost = isHost,
            onCountdownFinished = {
                readAndStartWithSynchronizedTime(onCountdownFinished)
            },
            onSendMatchStart = {
                multiplayerManager.sendMatchStart()
            }
        )
    }

    /**
     * Reads synchronized start time from Firebase and starts the match
     */
    private fun readAndStartWithSynchronizedTime(onCountdownFinished: (() -> Unit)?) {
        val gameId = multiplayerManager.currentGameId
        if (gameId == null) {
            Log.e(TAG, "readAndStartWithSynchronizedTime: currentGameId is null!")
            onCountdownFinished?.invoke() ?: onMatchStart?.invoke()
            return
        }
        
        val gameRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("games").child(gameId)
        gameRef.child("startTime").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val startTime = snapshot.getValue(Long::class.java)
                if (startTime != null) {
                    Log.d(TAG, "readAndStartWithSynchronizedTime: Got startTime from Firebase: $startTime")
                    matchStartTime = startTime
                } else {
                    Log.w(TAG, "readAndStartWithSynchronizedTime: startTime not found, using local time")
                    matchStartTime = System.currentTimeMillis()
                }
                onCountdownFinished?.invoke() ?: onMatchStart?.invoke()
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "readAndStartWithSynchronizedTime: Failed to read startTime", error.toException())
                matchStartTime = System.currentTimeMillis()
                onCountdownFinished?.invoke() ?: onMatchStart?.invoke()
            }
        })
    }

    private fun handleProfileLoadError(mode: String?, uid: String) {
        // Fallback to default values
        val defaultColor = if (mode == HomeActivity.MODE_HOST) NEON_GREEN else NEON_BLUE
        val defaultName = "Player ${if (mode == HomeActivity.MODE_HOST) 1 else 2}"
        val initialState = PlayerState(
            color = defaultColor,
            playerName = defaultName,
            uid = uid
        )
        
        activity.runOnUiThread {
            Toast.makeText(activity, "Using default profile settings", Toast.LENGTH_SHORT).show()
        }
        
        // Continue with the same game logic using default values
        when (mode) {
            HomeActivity.MODE_HOST -> hostGame(initialState)
            HomeActivity.MODE_JOIN -> joinGame(initialState, null)
            else -> onSetupError?.invoke("Error: Invalid mode")
        }
    }

    private fun checkGameStartedStatusForRejoin(savedState: GameStateManager.GameState) {
        multiplayerManager.getGameRef()?.child("started")?.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val gameStarted = snapshot.getValue(Boolean::class.java) ?: false
                if (gameStarted) {
                    Log.d(TAG, "Rejoined a game that's already in progress - starting immediately")
                    // Game is already running, start immediately
                    onMatchStart?.invoke()
                } else {
                    Log.d(TAG, "Rejoined a game that hasn't started yet - waiting for start signal")
                    // Wait for start signal
                    setupJoinerListeners()
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Failed to check game started status for rejoin", error.toException())
                // Fallback to waiting for start signal
                setupJoinerListeners()
            }
        })
    }

    // Getters for the activity to access current state
    fun getLocalPlayerId(): String? = localPlayerId
    fun getMatchDurationMs(): Long = matchDurationMs
    fun getMazeComplexity(): String = mazeComplexity
    fun getGameMode(): String = gameMode
    fun getMatchStartTime(): Long? = matchStartTime
} 
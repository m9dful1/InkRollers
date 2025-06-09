package com.spiritwisestudios.inkrollers

import android.util.Log
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.database.*
import com.google.firebase.database.ChildEventListener
import kotlin.random.Random
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import android.os.Handler
import java.util.Date
import com.spiritwisestudios.inkrollers.repository.ProfileRepository

// Data class to hold game settings read by clients
data class GameSettings(val durationMs: Long, val complexity: String, val gameMode: String)

// Data class for paint actions
data class PaintAction(
    val x: Int = 0,
    val y: Int = 0,
    val color: Int = 0,
    val playerId: String = "",
    val timestamp: Any = ServerValue.TIMESTAMP,
    val mazeX: Int = -1,
    val mazeY: Int = -1
)

/**
 * Firebase Realtime Database manager for multiplayer game coordination.
 * 
 * Handles game hosting/joining, player state synchronization, real-time paint actions,
 * matchmaking, and rematch coordination. Manages Firebase listeners for seamless
 * multiplayer experience with automatic cleanup and stale game removal.
 * 
 * Coordinates between local game state and Firebase database structure:
 * - /games/{gameId}/players/{playerId} - Player positions, colors, ink levels
 * - /games/{gameId}/paint/{paintId} - Real-time paint actions with normalized coordinates  
 * - /games/{gameId}/rematchRequests/{playerId} - Rematch voting system
 * - /games/{gameId}/started - Match start signaling
 */
class MultiplayerManager(private val context: android.content.Context? = null) {

    private val database = Firebase.database("https://inkrollers-13595-default-rtdb.firebaseio.com/")
    private var gameRef: DatabaseReference? = null
    private var playersRef: DatabaseReference? = null
    private var childListener: ChildEventListener? = null
    private var initSnapshotListener: ValueEventListener? = null
    private var paintRef: DatabaseReference? = null
    private var rematchRef: DatabaseReference? = null

    // Audio manager for multiplayer events
    private val audioManager: com.spiritwisestudios.inkrollers.AudioManager? by lazy {
        context?.let { com.spiritwisestudios.inkrollers.AudioManager.getInstance(it) }
    }

    // Add companion object back
    companion object {
        private const val TAG = "MultiplayerManager"
        internal const val GAMES_NODE = "games"
        internal const val PLAYERS_NODE = "players"
        private const val PAINT_NODE = "paint"
        private const val REMATCH_NODE = "rematchRequests"
        internal const val LAST_ACTIVITY_NODE = "lastActivityAt"
        internal const val CREATED_AT_NODE = "createdAt"
        private const val STALE_GAME_TTL_MS = 3 * 60 * 60 * 1000L // 3 hours
        private const val INACTIVE_GRACE_PERIOD_MS = 10 * 60 * 1000L // 10 minutes
        private const val MAX_GAMES_TO_SCAN_FOR_CLEANUP = 10

        // Duration for the client-side "Get Ready! 3-2-1-Go!" visual countdown.
        // The main game timer should start from its full duration AFTER this period.
        const val CLIENT_SIDE_PRE_GAME_VISUAL_COUNTDOWN_MS = 4000L
    }

    // Callback for database error notifications
    var onDatabaseError: ((String) -> Unit)? = null

    // Number of players in this game (used for rematch decision)
    private var expectedRematchCount: Long = 0L

    // Flag to track if initial snapshot has been processed
    private var initialSnapshotProcessed = false

    // New callbacks for pre-match flow: player count and match start requests
    var onPlayerCountChanged: ((Int) -> Unit)? = null
    var onMatchStartRequested: (() -> Unit)? = null

    private var playerCountListener: ValueEventListener? = null
    private var startListener: ValueEventListener? = null
    private var startRef: DatabaseReference? = null
    // To store game settings for clients
    private var gameSettings: GameSettings? = null
    
    // Keep auth instance for potential future use if needed
    private val auth: FirebaseAuth = Firebase.auth

    // Add a connectivity check at initialization
    init {
        testFirebaseConnection()
    }

    /** Tests Firebase connectivity and sets up connection monitoring. */
    private fun testFirebaseConnection() {
        Log.d(TAG, "Testing Firebase connectivity...")
        val connectedRef = database.getReference(".info/connected")
        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                     Log.d(TAG, "Firebase connection established")
                } else {
                    Log.w(TAG, "Firebase NOT connected - waiting for connection...")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase connection check cancelled", error.toException())
                onDatabaseError?.invoke("Connection check error: ${error.message}")
            }
        })
    }

    var localPlayerId: String? = null
        private set
    var currentGameId: String? = null
        private set
        
    // Maze seed for the current game
    var mazeSeed: Long = 0
        private set
        
    // Callback interface for GameView to receive updates
    interface RemoteUpdateListener {
        fun onPlayerStateChanged(playerId: String, newState: PlayerState)
        fun onPlayerRemoved(playerId: String)
        fun onPaintAction(x: Int, y: Int, color: Int, normalizedX: Float? = null, normalizedY: Float? = null)
    }
    var updateListener: RemoteUpdateListener? = null

    // Keep track of player listeners to remove them correctly
    private val playerListeners = mutableMapOf<String, ValueEventListener>()
    private var gameStructureListener: ValueEventListener? = null // Listener for overall game changes (optional)
    private var paintListener: ChildEventListener? = null // Listener for paint actions

    /** Callback to notify when both players have answered rematch: true=both yes, false=at least one no */
    var onRematchDecision: ((Boolean) -> Unit)? = null

    // Add a new flag for rematch coordination
    private var rematchInProgressRef: DatabaseReference? = null
    private var rematchInProgressListener: ValueEventListener? = null

    // Callback for when rematch should actually start (after both YES)
    var onRematchStartSignal: (() -> Unit)? = null

    /**
     * Creates a new multiplayer game and hosts it on Firebase.
     * Generates game ID, maze seed, and sets up initial game structure with host as player0.
     * Performs authentication check and stale game cleanup before hosting.
     */
    fun hostGame(initialPlayerState: PlayerState, durationMs: Long, complexity: String, gameMode: String, isPrivate: Boolean, callback: (success: Boolean, gameId: String?, gameSettings: GameSettings?) -> Unit) {
        clearListeners()
        performStaleGameCleanup()
        
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "Cannot host game: User not authenticated")
            onDatabaseError?.invoke("Authentication required to host game")
            callback(false, null, null)
            return
        }
        Log.d(TAG, "User authenticated with UID: ${currentUser.uid}, proceeding with hostGame")
        
        initialPlayerState.uid = currentUser.uid
        currentGameId = generateGameId()
        gameRef = database.getReference(GAMES_NODE).child(currentGameId!!)
        playersRef = gameRef?.child("players")
        paintRef = gameRef?.child(PAINT_NODE)

        Log.d(TAG, "Hosting game with ID: $currentGameId")

        localPlayerId = "player0"
        mazeSeed = System.currentTimeMillis()
        initialPlayerState.mazeSeed = mazeSeed
        Log.d(TAG, "Generated maze seed: $mazeSeed")
        
        createGameAfterConnectionTest(initialPlayerState, durationMs, complexity, gameMode, isPrivate, callback)
    }
    
    /** 
     * Creates the Firebase game structure and verifies successful write operation.
     * Handles initial player data, game settings, and Firebase listener setup.
     */
    private fun createGameAfterConnectionTest(initialPlayerState: PlayerState, durationMs: Long, complexity: String, gameMode: String, isPrivate: Boolean, callback: (success: Boolean, gameId: String?, gameSettings: GameSettings?) -> Unit) {
        try {
            val gameUpdateMap = mapOf(
                "mazeSeed" to mazeSeed,
                "matchDurationMs" to durationMs,
                "mazeComplexity" to complexity,
                "gameMode" to gameMode,
                "isPrivate" to isPrivate,
                CREATED_AT_NODE to ServerValue.TIMESTAMP,
                LAST_ACTIVITY_NODE to ServerValue.TIMESTAMP,
                "started" to false,
                "playerCount" to 1L,
                "players/$localPlayerId" to initialPlayerState
            )

            Log.d(TAG, "Attempting to write game data to Firebase: $currentGameId")
            Log.d(TAG, "Complete game data structure for update: $gameUpdateMap")
            
            gameSettings = GameSettings(durationMs, complexity, gameMode)
            
            // Use updateChildren to allow for more granular security rules.
            // This will create the game node and all its children if it doesn't exist.
            val updateTask = gameRef?.updateChildren(gameUpdateMap)
            
            updateTask?.addOnSuccessListener {
                Log.i(TAG, "Game $currentGameId write initiated successfully (listener pending verification).")
                
                database.getReference("$GAMES_NODE/$currentGameId").addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            val storedSeed = snapshot.child("mazeSeed").getValue(Long::class.java)
                            if (storedSeed == mazeSeed) {
                                Log.d(TAG, "Verified game data was written correctly (mazeSeed matches)")
                                updateLastActivityTimestamp()
                                setupFirebaseListeners()
                                auth.currentUser?.uid?.let { uid ->
                                    ProfileRepository.updatePlayerLobby(uid, currentGameId) { success ->
                                        if (success) {
                                            ProfileRepository.setLobbyOnDisconnect(uid)
                                        } else {
                                            Log.w(TAG, "Failed to update player lobby ID for host.")
                                        }
                                    }
                                }
                                callback(true, currentGameId, gameSettings)
                            } else {
                                Log.e(TAG, "Game data verification failed - mazeSeed doesn't match: stored=$storedSeed, local=$mazeSeed")
                                onDatabaseError?.invoke("Game data mismatch after write")
                                callback(false, null, null)
                            }
                        } else {
                            Log.e(TAG, "ERROR: Game node $currentGameId does NOT exist after successful write callback.")
                            Log.e(TAG, "This strongly indicates a database permission/rule issue or data inconsistency.")
                            onDatabaseError?.invoke("Game creation failed verification")
                            callback(false, null, null)
                        }
                    }
                    
                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Game data verification read failed", error.toException())
                        Log.e(TAG, "ERROR: Could not read back game data ($currentGameId). Database permissions issue?")
                        onDatabaseError?.invoke("Could not verify game creation: ${error.message}")
                        callback(false, null, null)
                    }
                })
            }?.addOnFailureListener { exception ->
                Log.e(TAG, "Firebase updateChildren operation failed for game $currentGameId", exception)
                Log.e(TAG, "ERROR: Could not create game. Likely a database permissions issue.")
                onDatabaseError?.invoke("Could not create game: ${exception.message}")
                callback(false, null, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in createGameAfterConnectionTest", e)
            Log.e(TAG, "ERROR: Unexpected exception during game creation.")
            onDatabaseError?.invoke("Unexpected error creating game: ${e.message}")
            callback(false, null, null)
        }
    }

    /** Generates a random alphanumeric game ID for matchmaking. */
    private fun generateGameId(length: Int = 6): String {
        val allowedChars = ('A'..'Z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random(Random) }
            .joinToString("")
    }

    /**
     * Joins an existing multiplayer game by ID, or finds a random available game if ID is null.
     * Validates game availability, retrieves game settings and maze seed from host,
     * assigns next available player slot, and sets up Firebase listeners.
     */
    fun joinGame(gameId: String?, initialPlayerState: PlayerState, callback: (success: Boolean, playerId: String?, gameSettings: GameSettings?) -> Unit) {
        clearListeners()
        
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "Cannot join game: User not authenticated")
            onDatabaseError?.invoke("Authentication required to join game")
            callback(false, null, null)
            return
        }
        Log.d(TAG, "User authenticated with UID: ${currentUser.uid}, proceeding with joinGame")
        
        initialPlayerState.uid = currentUser.uid

        if (gameId == null) {
            findRandomAvailableGame { randomGameId ->
                if (randomGameId != null) {
                    Log.i(TAG, "Found random game to join: $randomGameId")
                    joinGame(randomGameId, initialPlayerState, callback)
                } else {
                    Log.w(TAG, "No available games found to join")
                    onDatabaseError?.invoke("No available games found")
                    callback(false, null, null)
                }
            }
            return
        }
        
        Log.d(TAG, "joinGame(): requested specific gameId=$gameId")

        val potentialGameRef = database.getReference(GAMES_NODE).child(gameId)
        Log.d(TAG, "Attempting to join game: $gameId")

        potentialGameRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    Log.w(TAG, "Game $gameId does not exist.")
                    onDatabaseError?.invoke("Game ID $gameId not found")
                    callback(false, null, null)
                    return
                }

                val gameMazeSeed = snapshot.child("mazeSeed").getValue(Long::class.java)
                val duration = snapshot.child("matchDurationMs").getValue(Long::class.java) ?: 180000L
                val complexity = snapshot.child("mazeComplexity").getValue(String::class.java) ?: HomeActivity.COMPLEXITY_HIGH
                val gameMode = snapshot.child("gameMode").getValue(String::class.java) ?: HomeActivity.GAME_MODE_COVERAGE
                gameSettings = GameSettings(duration, complexity, gameMode)

                if (gameMazeSeed != null) {
                    mazeSeed = gameMazeSeed
                    initialPlayerState.mazeSeed = gameMazeSeed
                    Log.d(TAG, "Retrieved maze seed from host: $mazeSeed")
                } else {
                    Log.w(TAG, "No maze seed found in game data for $gameId, using local time")
                    mazeSeed = System.currentTimeMillis()
                    initialPlayerState.mazeSeed = mazeSeed
                }
                
                val playersNodeSnapshot = snapshot.child("players")
                if (!playersNodeSnapshot.exists()) {
                    Log.w(TAG, "Game $gameId exists but has no players node")
                    onDatabaseError?.invoke("Game $gameId is corrupted (no players)")
                    callback(false, null, null)
                    return
                }

                val playerCount = playersNodeSnapshot.childrenCount
                val maxPlayers = 4
                if (playerCount >= maxPlayers) {
                    Log.w(TAG, "Game $gameId full ($playerCount players).")
                    onDatabaseError?.invoke("Game $gameId is full")
                    callback(false, null, null)
                    return
                }

                var nextIndex = 1
                while (playersNodeSnapshot.hasChild("player$nextIndex")) {
                    nextIndex++
                }
                val assignedId = "player$nextIndex"
                Log.i(TAG, "Joining game $gameId as $assignedId")

                currentGameId = gameId
                gameRef = potentialGameRef
                playersRef = potentialGameRef.child("players")
                paintRef = potentialGameRef.child(PAINT_NODE)
                localPlayerId = assignedId

                playersRef!!.child(assignedId).setValue(initialPlayerState)
                    .addOnSuccessListener {
                        Log.i(TAG, "Added $assignedId to Firebase game $gameId.")
                        updateLastActivityTimestamp()
                        setupFirebaseListeners()
                        setupRematchListener()
                        auth.currentUser?.uid?.let { uid ->
                            ProfileRepository.updatePlayerLobby(uid, currentGameId) { success ->
                                if (success) {
                                    ProfileRepository.setLobbyOnDisconnect(uid)
                                } else {
                                    Log.w(TAG, "Failed to update player lobby ID for joining client.")
                                }
                            }
                        }
                        callback(true, assignedId, gameSettings)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to add $assignedId to game $gameId", e)
                        onDatabaseError?.invoke("Failed to join game: ${e.message}")
                        leaveGame()
                        callback(false, null, null)
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "joinGame() cancelled while reading game $gameId: ${error.message}", error.toException())
                onDatabaseError?.invoke("Error joining game: ${error.message}")
                callback(false, null, null)
            }
        })
    }

    /**
     * Searches for public games with available player slots.
     * Filters out private, started, full, and empty games before selection.
     */
    private fun findRandomAvailableGame(callback: (String?) -> Unit) {
        val gamesRef = database.getReference(GAMES_NODE)
        
        Log.d(TAG, "Searching for available games...")
        performStaleGameCleanup()
        
        gamesRef.orderByChild(CREATED_AT_NODE).limitToLast(MAX_GAMES_TO_SCAN_FOR_CLEANUP)
              .addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    if (!snapshot.exists()) {
                        Log.d(TAG, "No games exist in the database (or recent 20)")
                        callback(null)
                        return
                    }
                    
                    Log.d(TAG, "Found ${snapshot.childrenCount} recent games to check")
                    val availableGames = mutableListOf<String>()
                    var skippedCount = 0
                    val currentTime = System.currentTimeMillis()
                    
                    for (gameSnapshot in snapshot.children.reversed()) {
                        val gameId = gameSnapshot.key ?: continue
                        
                        Log.v(TAG, "Examining game: $gameId -> ${gameSnapshot.value}")

                        val lastActivity = gameSnapshot.child(LAST_ACTIVITY_NODE).getValue(Long::class.java) ?: 0L
                        // If a game hasn't had any activity for 2 minutes, consider it stale for matchmaking purposes.
                        if (currentTime - lastActivity > 120_000L) {
                             Log.v(TAG, "Game $gameId skipped: stale (no activity for 2 minutes)")
                             skippedCount++
                             continue
                        }
                        
                        val playersSnapshot = gameSnapshot.child("players")
                        if (!playersSnapshot.exists()) {
                            Log.v(TAG, "Game $gameId skipped: no players node")
                            skippedCount++
                            continue
                        }
                        
                        val playerCount = playersSnapshot.childrenCount
                        val maxPlayers = 4
                        
                        if (playerCount == 0L || playerCount >= maxPlayers) {
                            Log.v(TAG, "Game $gameId skipped: Player count $playerCount (max $maxPlayers)")
                            skippedCount++
                            continue
                        }
                        
                        val started = gameSnapshot.child("started").getValue(Boolean::class.java)
                        if (started == true) {
                            Log.v(TAG, "Game $gameId skipped: already started")
                            skippedCount++
                            continue
                        }
                        
                        val isPrivateGame = gameSnapshot.child("isPrivate").getValue(Boolean::class.java) ?: false
                        if (isPrivateGame) {
                            Log.v(TAG, "Game $gameId skipped: marked as private")
                            skippedCount++
                            continue
                        }
                        
                        availableGames.add(gameId)
                        Log.d(TAG, "Found potentially available public game: $gameId with $playerCount players")
                    }
                    
                    Log.d(TAG, "Search result: ${availableGames.size} potentially available public games, $skippedCount skipped")
                    
                    if (availableGames.isNotEmpty()) {
                        val randomGame = availableGames.random()
                        Log.d(TAG, "Selected random game: $randomGame")
                        callback(randomGame)
                    } else {
                        Log.d(TAG, "No available games found after filtering")
                        callback(null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error finding random game", e)
                    onDatabaseError?.invoke("Error finding game: ${e.message}")
                    callback(null)
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "findRandomAvailableGame() cancelled: ${error.message}", error.toException())
                onDatabaseError?.invoke("Error finding game: ${error.message}")
                callback(null)
            }
        })
    }

    /** Updates the local player's complete state in Firebase. */
    @Deprecated("This function is buggy and causes permission errors. Use updatePlayerState instead.", ReplaceWith("updatePlayerState(newState.normX, newState.normY, newState.ink, newState.mode)"), DeprecationLevel.ERROR)
    fun updateLocalPlayerState(_newState: PlayerState) {
        // This function is deprecated because it sends the entire player state,
        // which can overwrite the UID and violate security rules.
        // The new updatePlayerState function sends only the changed fields.
        Log.e(TAG, "updateLocalPlayerState is deprecated and should not be called.")
        onDatabaseError?.invoke("Deprecated function was called. See logs.")
    }

    /**
     * Updates specific fields of the local player's state using atomic updates.
     * More efficient than full state updates for partial changes like position or ink level.
     */
    fun updateLocalPlayerPartialState(updateMap: Map<String, Any>) {
        if (localPlayerId == null || playersRef == null) return
        playersRef?.child(localPlayerId!!)?.updateChildren(updateMap)?.addOnFailureListener { e ->
            Log.w(TAG, "Failed to perform partial update for $localPlayerId with keys ${updateMap.keys}", e)
            onDatabaseError?.invoke("Partial update failed: ${e.message}")
        }
        updateLastActivityTimestamp()
        Log.v(TAG, "Updating partial state for $localPlayerId: ${updateMap.keys}")
    }

    /**
     * Sets up all Firebase listeners for real-time game synchronization.
     * Includes initial snapshot, incremental player updates, paint actions, 
     * player count monitoring, and match start signaling.
     */
    fun setupFirebaseListeners() {
        if (playersRef == null) return
        clearListeners()

        Log.d(TAG, "Setting up Firebase listeners for game: $currentGameId")

        initSnapshotListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "Initial player snapshot received for $currentGameId. Processing ${snapshot.childrenCount} players.")
                snapshot.children.forEach { handlePlayerData(it) }
                initialSnapshotProcessed = true
                Log.d(TAG, "Initial snapshot processing complete for $currentGameId")
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Initial player snapshot cancelled for $currentGameId", error.toException())
                onDatabaseError?.invoke("Initial load error: ${error.message}")
            }
        }
        playersRef!!.addListenerForSingleValueEvent(initSnapshotListener!!)
        Log.d(TAG, "Added single value event listener for initial player state.")

        childListener = object : ChildEventListener {
            override fun onChildAdded(snap: DataSnapshot, prev: String?) {
                Log.v(TAG, "Child added: ${snap.key} in $currentGameId")
                val playerId = snap.key
                
                // Play player join sound only for new players after initial snapshot is processed
                if (initialSnapshotProcessed && playerId != localPlayerId) {
                    Log.d(TAG, "New player joined: $playerId, playing join sound")
                    audioManager?.playSound(com.spiritwisestudios.inkrollers.AudioManager.SoundType.PLAYER_JOIN)
                }
                
                handlePlayerData(snap)
            }
            override fun onChildChanged(snap: DataSnapshot, prev: String?) {
                 Log.v(TAG, "Child changed: ${snap.key} in $currentGameId")
                 handlePlayerData(snap)
            }
            override fun onChildRemoved(snap: DataSnapshot) {
                 val playerId = snap.key
                 if (playerId != null) {
                    Log.d(TAG, "Player removed via child event: $playerId in $currentGameId")
                    updateListener?.onPlayerRemoved(playerId)
                 }
            }
            override fun onChildMoved(snap: DataSnapshot, prev: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Player child listener cancelled for $currentGameId", error.toException())
                onDatabaseError?.invoke("Player listener error: ${error.message}")
                updateListener = null
            }
        }
        playersRef!!.addChildEventListener(childListener!!)
        Log.d(TAG, "Added child event listener for player updates.")

        setupPaintListener()

        playerCountListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "Player count changed to ${snapshot.childrenCount} for game $currentGameId")
                onPlayerCountChanged?.invoke(snapshot.childrenCount.toInt())
            }
            override fun onCancelled(error: DatabaseError) {
                 Log.w(TAG, "Player count listener cancelled for $currentGameId", error.toException())
            }
        }
        playersRef!!.addValueEventListener(playerCountListener!!)
        Log.d(TAG, "Added player count listener.")

        startRef = gameRef?.child("started")
        startListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val started = snapshot.getValue(Boolean::class.java) ?: false
                Log.d(TAG, "Match start signal received: started=$started for game $currentGameId")
                if (started) {
                    onMatchStartRequested?.invoke()
                    startRef?.removeEventListener(this)
                    startListener = null
                    onMatchStartRequested = null
                    Log.d(TAG, "Removed match start listener for $currentGameId")
                }
            }
            override fun onCancelled(error: DatabaseError) {
                 Log.w(TAG, "Match start listener cancelled for $currentGameId", error.toException())
                 onDatabaseError?.invoke("Start listener error: ${error.message}")
            }
        }
        startRef?.addValueEventListener(startListener!!)
        Log.d(TAG, "Added match start listener.")
    }
    
    /** Processes individual player state updates from Firebase snapshots. */
    private fun handlePlayerData(snapshot: DataSnapshot) {
         val playerId = snapshot.key
         if (playerId == null) {
             Log.w(TAG, "handlePlayerData received null playerId in game $currentGameId")
             return
         }
         try {
             val playerState = snapshot.getValue(PlayerState::class.java)
             if (playerState != null) {
                 val logPrefix = if (playerId == localPlayerId) "Processing LOCAL" else "Received REMOTE"
                 Log.v(TAG, "$logPrefix state for $playerId in $currentGameId: NormPos=(${playerState.normX}, ${playerState.normY}), Ink=${playerState.ink}, Mode=${playerState.mode}, Active=${playerState.active}")
                 updateListener?.onPlayerStateChanged(playerId, playerState)
             } else {
                 Log.w(TAG, "Received null player state for $playerId in game $currentGameId")
             }
         } catch (e: DatabaseException) {
             Log.e(TAG, "Failed to parse player state for $playerId in game $currentGameId", e)
             onDatabaseError?.invoke("Error parsing player state for $playerId")
         }
    }

    /** 
     * Leaves the current game by removing the player's node and cleaning up listeners.
     * Checks if the game is empty after leaving and removes it from Firebase if necessary.
     */
    fun leaveGame() {
        val gameIdToLeave = currentGameId
        val playerToRemove = localPlayerId
        Log.d(TAG, "Leaving game: $gameIdToLeave as player $playerToRemove")

        auth.currentUser?.uid?.let { uid ->
            ProfileRepository.cancelLobbyOnDisconnect(uid)
            ProfileRepository.updatePlayerLobby(uid, null) { success ->
                if (!success) {
                    Log.w(TAG, "Failed to clear player lobby ID on leaveGame.")
                }
            }
        }
        
        val gameToRemoveRef = gameRef
        
        if (playerToRemove != null && playersRef != null) {
             playersRef?.child(playerToRemove)?.removeValue()
                 ?.addOnCompleteListener { task -> 
                     Log.d(TAG, "Removed player $playerToRemove from game $gameIdToLeave. Success: ${task.isSuccessful}")
                     checkAndRemoveGameIfEmpty(gameToRemoveRef)
                 }
        } else {
            // If there's no player to remove, still check if the game is empty
            checkAndRemoveGameIfEmpty(gameToRemoveRef)
        }
        
        clearListenersAndResetState()
    }
    
    /** Checks if game has any active players and removes it if empty. */
    private fun checkAndRemoveGameIfEmpty(gameToRemoveRef: DatabaseReference?) {
        val gameId = gameToRemoveRef?.key
        Log.d(TAG, "Checking if game $gameId should be removed...")
        gameToRemoveRef?.child("players")?.addListenerForSingleValueEvent(object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // If the players node exists and has no children, it's empty.
                // Or if it doesn't exist at all, it's also empty.
                if (!snapshot.exists() || !snapshot.hasChildren()) {
                    Log.i(TAG, "No players remaining in game $gameId. Removing game node.")
                    gameToRemoveRef.removeValue()
                        .addOnSuccessListener { Log.i(TAG, "Successfully removed empty game $gameId from Firebase.") }
                        .addOnFailureListener { e -> Log.w(TAG, "Failed to remove empty game $gameId from Firebase.", e) }
                } else {
                    Log.d(TAG, "Active players still exist in game $gameId. Not removing game node.")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Could not check player status for game $gameId removal.", error.toException())
            }
        })
    }
    
    /** Cleans up all Firebase listeners and resets local state. */
    private fun clearListenersAndResetState() {
        clearListeners()
        gameRef = null
        playersRef = null
        paintRef = null
        rematchRef = null
        startRef = null
        localPlayerId = null
        currentGameId = null
        expectedRematchCount = 0
        gameSettings = null
        initialSnapshotProcessed = false
        
        Log.d(TAG, "Local MultiplayerManager state reset.")
    }
    
    /** Removes all active Firebase listeners with error handling. */
    private fun clearListeners() {
        childListener?.let { listener ->
            try { playersRef?.removeEventListener(listener) } catch (e: Exception) { Log.w(TAG, "Error removing child listener", e) }
            Log.d(TAG, "Removed child listener.")
        }
        childListener = null

        initSnapshotListener = null

        paintListener?.let { listener ->
            try { paintRef?.removeEventListener(listener) } catch (e: Exception) { Log.w(TAG, "Error removing paint listener", e) }
        }
        paintListener = null

        playerCountListener?.let { listener -> 
             try { playersRef?.removeEventListener(listener) } catch (e: Exception) { Log.w(TAG, "Error removing player count listener", e) } 
        }
        playerCountListener = null

        startListener?.let { listener -> 
            try { startRef?.removeEventListener(listener) } catch (e: Exception) { Log.w(TAG, "Error removing start listener", e) }
        }
        startListener = null

        Log.d(TAG, "Detached Firebase listeners (player, paint, count, start). Rematch listener managed separately.")
    }

    /**
     * Retrieves current state of all players in the game.
     * Used for rematch coordination and game state synchronization.
     */
    fun getPlayersState(callback: (Map<String, PlayerState?>) -> Unit) {
        if (playersRef == null) {
            Log.w(TAG, "getPlayersState: playersRef is null.")
            callback(emptyMap())
            return
        }
        playersRef?.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val playerStates = mutableMapOf<String, PlayerState?>()
                snapshot.children.forEach { playerSnapshot ->
                    val playerId = playerSnapshot.key
                    if (playerId != null) {
                        try {
                            val playerState = playerSnapshot.getValue(PlayerState::class.java)
                            playerStates[playerId] = playerState
                        } catch (e: DatabaseException) {
                            Log.e(TAG, "getPlayersState: Failed to parse player state for $playerId", e)
                            playerStates[playerId] = null
                        }
                    } else {
                         Log.w(TAG, "getPlayersState: Received null playerId in snapshot.")
                    }
                }
                Log.d(TAG, "getPlayersState: Fetched ${playerStates.size} player states.")
                callback(playerStates)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "getPlayersState: Database query cancelled", error.toException())
                onDatabaseError?.invoke("Error fetching player states: ${error.message}")
                callback(emptyMap())
            }
        })
    }

    /** 
     * Signals all clients to begin the pre-match countdown sequence.
     * Sets match start timestamp and triggers client-side countdown coordination.
     */
    fun sendMatchStart() {
        if (gameRef == null) return
        Log.d(TAG, "Host sending match start signal for game $currentGameId")
        playersRef?.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val playerCount = snapshot.childrenCount
                Log.d(TAG, "sendMatchStart: Storing playerCount=$playerCount in game node for $currentGameId")
                gameRef!!.child("playerCount").setValue(playerCount)
                    .addOnCompleteListener {
                        val updates = mapOf(
                            "started" to true,
                            "gameStartTimeBase" to ServerValue.TIMESTAMP,
                            "gameStartOffsetMs" to CLIENT_SIDE_PRE_GAME_VISUAL_COUNTDOWN_MS,
                            LAST_ACTIVITY_NODE to ServerValue.TIMESTAMP
                        )
                        gameRef!!.updateChildren(updates)
                            .addOnFailureListener { e ->
                                Log.w(TAG, "Failed to send match start for $currentGameId", e)
                                onDatabaseError?.invoke("Failed to send start signal: "+e.message)
                            }
                    }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "sendMatchStart: Failed to get player count", error.toException())
                val updates = mapOf(
                    "started" to true,
                    "gameStartTimeBase" to ServerValue.TIMESTAMP,
                    "gameStartOffsetMs" to CLIENT_SIDE_PRE_GAME_VISUAL_COUNTDOWN_MS,
                    LAST_ACTIVITY_NODE to ServerValue.TIMESTAMP
                )
                gameRef!!.updateChildren(updates)
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Failed to send match start for $currentGameId (fallback)", e)
                        onDatabaseError?.invoke("Failed to send start signal (fallback): "+e.message)
                    }
            }
        })
    }

    /**
     * Broadcasts a paint action to all other players via Firebase.
     * Uses normalized coordinates for cross-device synchronization.
     */
    fun sendPaintAction(x: Int, y: Int, color: Int, normalizedX: Float, normalizedY: Float) {
        val gameId = currentGameId ?: return
        val paintRef = database.getReference(GAMES_NODE).child(gameId).child(PAINT_NODE).push()
        val paintAction = PaintAction(
            x = x,
            y = y,
            color = color,
            playerId = localPlayerId ?: "",
            timestamp = ServerValue.TIMESTAMP,
            // Store normalized coordinates. The 'mazeX' and 'mazeY' fields in PaintAction are repurposed for this.
            mazeX = (normalizedX * 10000).toInt(),
            mazeY = (normalizedY * 10000).toInt()
        )
        paintRef.setValue(paintAction).addOnFailureListener { e ->
            Log.e(TAG, "Failed to send paint action", e)
        }
    }
    
    /** Checks if currently connected to an active game. */
    private fun isConnected(): Boolean {
        return currentGameId != null && localPlayerId != null
    }

    /** Sets up listener for real-time paint actions from other players. */
    private fun setupPaintListener() {
        if (paintRef == null) return
        
        paintListener?.let { listener ->
             try { paintRef?.removeEventListener(listener) } catch (e: Exception) { Log.w(TAG, "Error removing old paint listener", e) }
        }
        paintListener = null
        
        paintListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                processPaintAction(snapshot)
            }
            
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                processPaintAction(snapshot)
            }
            
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Paint listener cancelled for $currentGameId", error.toException())
                 onDatabaseError?.invoke("Paint listener error: ${error.message}")
            }
        }
        
        paintRef!!.orderByChild("timestamp").startAt(System.currentTimeMillis().toDouble())
                .addChildEventListener(paintListener!!)
        Log.d(TAG, "Added paint action listener for $currentGameId (listening from now onwards).")
    }
    
    /** 
     * Processes incoming paint actions from Firebase.
     * Filters out own actions and applies remote paint using normalized coordinates.
     */
    private fun processPaintAction(snapshot: DataSnapshot) {
        try {
            val paintAction = snapshot.getValue(PaintAction::class.java)

            if (paintAction != null) {
                if (paintAction.playerId != localPlayerId) {
                    // Convert stored integers back to normalized floats
                    val normalizedX = paintAction.mazeX / 10000f
                    val normalizedY = paintAction.mazeY / 10000f

                    Log.v(TAG, "Received normalized paint from ${paintAction.playerId} at ($normalizedX,$normalizedY) with color #${paintAction.color.toString(16)} in game $currentGameId")
                    // The 'x' and 'y' in onPaintAction are now legacy, we pass 0,0 and use normalized coords.
                    updateListener?.onPaintAction(0, 0, paintAction.color, normalizedX, normalizedY)
                }
            } else {
                 Log.w(TAG, "Received incomplete paint action in $currentGameId: $snapshot")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process paint action snapshot in $currentGameId: $snapshot", e)
            onDatabaseError?.invoke("Error processing paint action")
        }
    }

    /** Submits this player's rematch vote (yes/no) to Firebase. */
    fun sendRematchAnswer(wantRematch: Boolean) {
        Log.d(TAG, "sendRematchAnswer called. wantRematch=$wantRematch, currentGameId=$currentGameId, localPlayerId=$localPlayerId")
        if (currentGameId == null || localPlayerId == null) {
            Log.w(TAG, "sendRematchAnswer: currentGameId or localPlayerId is null. Aborting.")
            return
        }
        if (rematchRef == null) rematchRef = gameRef?.child(REMATCH_NODE)
        Log.d(TAG, "Sending rematch answer ($wantRematch) for player $localPlayerId in game $currentGameId")
        rematchRef?.child(localPlayerId!!)?.setValue(wantRematch)
           ?.addOnSuccessListener { updateLastActivityTimestamp() }
           ?.addOnFailureListener { e -> 
                Log.w(TAG, "Failed to send rematch answer for $localPlayerId in $currentGameId", e) 
                onDatabaseError?.invoke("Rematch answer failed: ${e.message}")
            }
    }

    /** Clears all rematch votes from Firebase (typically called by host). */
    fun clearRematchAnswers() {
        Log.d(TAG, "clearRematchAnswers called for game $currentGameId")
        Log.d(TAG, "Clearing rematch answers for game $currentGameId")
        rematchRef?.removeValue()
            ?.addOnFailureListener { e -> 
                Log.w(TAG, "Failed to clear rematch answers for $currentGameId", e)
            }
    }

    private var rematchListener: ValueEventListener? = null
    
    /**
     * Sets up listeners for rematch voting coordination.
     * Monitors player votes and triggers rematch start when all players agree.
     */
    fun setupRematchListener() {
        Log.d(TAG, "setupRematchListener called for game $currentGameId")
        if (rematchRef == null) rematchRef = gameRef?.child(REMATCH_NODE)
        if (rematchInProgressRef == null) rematchInProgressRef = gameRef?.child("rematchInProgress")
        
        rematchListener?.let { listener ->
             try { rematchRef?.removeEventListener(listener) } catch (e: Exception) { Log.w(TAG, "Error removing old rematch listener", e) }
        }
        rematchListener = null
        rematchInProgressListener?.let { listener ->
            try { rematchInProgressRef?.removeEventListener(listener) } catch (e: Exception) { Log.w(TAG, "Error removing old rematchInProgress listener", e) }
        }
        rematchInProgressListener = null
        Log.d(TAG, "Setting up rematch listener for game $currentGameId")

        rematchInProgressRef?.setValue(false)?.addOnCompleteListener {
            Log.d(TAG, "rematchInProgress explicitly set to FALSE before attaching rematch listeners for $currentGameId")
            playersRef?.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    expectedRematchCount = snapshot.children.filter {
                        it.child("active").getValue(Boolean::class.java) == true
                    }.count().toLong()
                    Log.d(TAG, "setupRematchListener: expectedRematchCount updated to $expectedRematchCount for game $currentGameId (from active players)")
                    attachRematchListener()
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "setupRematchListener: failed to get player count, using fallback expectedRematchCount=2", error.toException())
                    expectedRematchCount = 2L
                    attachRematchListener()
                }
            })
        }
    }

    /** Attaches Firebase listeners for rematch vote monitoring and coordination. */
    private fun attachRematchListener() {
        rematchListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "Rematch listener onDataChange: ${snapshot.childrenCount} answers received.")
                val currentPlayers = expectedRematchCount
                Log.d(TAG, "Rematch listener: using expectedRematchCount=$expectedRematchCount as currentPlayers, snapshot.childrenCount=${snapshot.childrenCount}")
                if (currentPlayers > 0 && snapshot.childrenCount >= currentPlayers) {
                    Log.d(TAG, "All rematch answers received for $currentGameId (${snapshot.childrenCount}/$currentPlayers)")
                    var allYes = true
                    snapshot.children.forEach {
                        val ans = it.getValue(Boolean::class.java) ?: false
                        if (!ans) allYes = false
                    }
                    Log.d(TAG, "Rematch listener: allYes=$allYes. Invoking onRematchDecision callback.")
                    if (allYes) {
                        Log.d(TAG, "Setting rematchInProgress to TRUE after all players answered YES for $currentGameId")
                        rematchInProgressRef?.setValue(true)?.addOnCompleteListener {
                            Log.d(TAG, "rematchInProgress explicitly set to TRUE after allYes for $currentGameId")
                        }
                    }
                    onRematchDecision?.invoke(allYes)
                    rematchRef?.removeEventListener(this)
                    rematchListener = null
                    Log.d(TAG, "Removed rematch listener for $currentGameId after decision.")
                } else {
                    Log.v(TAG, "Waiting for rematch answers in $currentGameId (${snapshot.childrenCount}/$currentPlayers received)")
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Rematch listener cancelled for $currentGameId", error.toException())
                onDatabaseError?.invoke("Rematch listener error: ${error.message}")
            }
        }
        rematchRef?.addValueEventListener(rematchListener!!)

        rematchInProgressListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val inProgress = snapshot.getValue(Boolean::class.java) ?: false
                Log.d(TAG, "rematchInProgressListener: rematchInProgress=$inProgress for $currentGameId")
                if (inProgress) {
                    if (expectedRematchCount > 0) {
                        rematchRef?.addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(rematchSnap: DataSnapshot) {
                                val yesCount = rematchSnap.children.count { it.getValue(Boolean::class.java) == true }
                                if (yesCount.toLong() == expectedRematchCount) {
                                    Log.d(TAG, "rematchInProgressListener: All players answered YES, proceeding with rematch for $currentGameId")
                                    onRematchStartSignal?.invoke()
                                    if (localPlayerId == "player0") {
                                        Handler(android.os.Looper.getMainLooper()).postDelayed({
                                            clearRematchAnswers()
                                            rematchInProgressRef?.setValue(false)
                                            Log.d(TAG, "rematchInProgress explicitly set to FALSE after rematch start for $currentGameId")
                                        }, 2000)
                                    }
                                } else {
                                    Log.w(TAG, "rematchInProgressListener: inProgress==true but not all players answered YES ($yesCount/$expectedRematchCount) for $currentGameId. Ignoring trigger.")
                                }
                            }
                            override fun onCancelled(error: DatabaseError) {
                                Log.w(TAG, "rematchInProgressListener: failed to check rematch answers for $currentGameId", error.toException())
                            }
                        })
                    } else {
                        Log.w(TAG, "rematchInProgressListener: inProgress==true but expectedRematchCount==0 for $currentGameId. Ignoring trigger.")
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "rematchInProgressListener cancelled for $currentGameId", error.toException())
            }
        }
        rematchInProgressRef?.addValueEventListener(rematchInProgressListener!!)
    }

    /**
     * Resets all player states in Firebase for rematch.
     * Overwrites existing player data with fresh initial states.
     */
    fun resetAllPlayerStatesFirebase(initialStates: Map<String, PlayerState>) {
        Log.d(TAG, "resetAllPlayerStatesFirebase called for game $currentGameId. initialStates=$initialStates")
        if (playersRef == null) {
            Log.e(TAG, "Cannot reset player states, playersRef is null for $currentGameId")
            onDatabaseError?.invoke("Cannot reset players (no reference)")
            return
        }
        Log.d(TAG, "Resetting player states in Firebase for players: ${initialStates.keys} in game $currentGameId")
        playersRef?.updateChildren(initialStates as Map<String, Any?>)
            ?.addOnSuccessListener { Log.i(TAG, "Successfully reset player states in Firebase for $currentGameId.") }
            ?.addOnFailureListener { e -> 
                 Log.e(TAG, "Failed to reset player states in Firebase for $currentGameId.", e)
                 onDatabaseError?.invoke("Failed to reset players: ${e.message}")
            }
    }

    /** Removes all paint actions from Firebase (used for rematches). */
    fun clearPaintActions() {
        Log.d(TAG, "Clearing paint actions for game $currentGameId")
        paintRef?.removeValue()?.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Failed to clear paint actions for $currentGameId", task.exception)
            }
        }
    }

    /** Returns the current authenticated user's Firebase UID. */
    fun getCurrentUserUid(): String? {
        return auth.currentUser?.uid
    }

    /** Updates the game's last activity timestamp for cleanup monitoring. */
    private fun updateLastActivityTimestamp() {
        gameRef?.child(LAST_ACTIVITY_NODE)?.setValue(ServerValue.TIMESTAMP)
            ?.addOnFailureListener { e ->
                Log.w(TAG, "Failed to update lastActivityAt for game $currentGameId", e)
            }
    }

    /**
     * Removes old, inactive, or abandoned games from Firebase.
     * Scans recent games and deletes those past TTL or with no active players.
     * Helps maintain database cleanliness and performance.
     */
    private fun performStaleGameCleanup() {
        Log.d(TAG, "Performing stale game cleanup...")
        val gamesQuery = database.getReference(GAMES_NODE)
            .orderByChild(CREATED_AT_NODE)
            .limitToFirst(MAX_GAMES_TO_SCAN_FOR_CLEANUP)

        gamesQuery.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    Log.d(TAG, "No games found to scan for cleanup.")
                    return
                }
                val currentTime = System.currentTimeMillis()
                var gamesCleanedCount = 0

                snapshot.children.forEach { gameSnapshot ->
                    val gameId = gameSnapshot.key
                    if (gameId == null) return@forEach

                    val createdAt = gameSnapshot.child(CREATED_AT_NODE).getValue(Long::class.java) ?: currentTime
                    val lastActivityAt = gameSnapshot.child(LAST_ACTIVITY_NODE).getValue(Long::class.java) ?: createdAt

                    var shouldDelete = false
                    var reason = ""
                    
                    val timeSinceLastActivity = currentTime - lastActivityAt

                    // A game is stale if it's been inactive for the full TTL (e.g., 3 hours)
                    if (timeSinceLastActivity > STALE_GAME_TTL_MS) {
                        shouldDelete = true
                        reason = "Stale by TTL (lastActivityAt: ${Date(lastActivityAt)})"
                    }
                    // A game is also considered abandoned if it's past the grace period (e.g. 10 mins)
                    // and hasn't seen activity for that same amount of time. This catches games
                    // that were created but never properly left.
                    else if (timeSinceLastActivity > INACTIVE_GRACE_PERIOD_MS) {
                         val playersNode = gameSnapshot.child("players")
                         // Also check if it's empty, to clean up games that were left properly.
                         if (!playersNode.exists() || !playersNode.hasChildren()) {
                            shouldDelete = true
                            reason = "Empty and inactive past grace period (lastActivityAt: ${Date(lastActivityAt)})"
                         }
                    }

                    if (shouldDelete) {
                        Log.i(TAG, "performStaleGameCleanup: Removing game $gameId. Reason: $reason")
                        database.getReference(GAMES_NODE).child(gameId).removeValue()
                            .addOnSuccessListener { gamesCleanedCount++ }
                            .addOnFailureListener { e ->
                                Log.w(TAG, "performStaleGameCleanup: Failed to remove game $gameId", e)
                            }
                    }
                }
                if (gamesCleanedCount > 0) {
                    Log.i(TAG, "performStaleGameCleanup: Finished. Cleaned $gamesCleanedCount games.")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "performStaleGameCleanup: Query cancelled.", error.toException())
                onDatabaseError?.invoke("Stale game cleanup failed: ${error.message}")
            }
        })
    }

    /**
     * Sends the local player's current state (position, ink, mode) to Firebase.
     * This is called frequently during gameplay to keep clients synchronized.
     * Uses a map to only send the fields that change, improving efficiency.
     */
    fun updatePlayerState(normX: Float, normY: Float, ink: Float, mode: Int) {
        val gameId = currentGameId ?: return
        val playerId = localPlayerId ?: return

        val stateUpdate = mapOf(
            "normX" to normX,
            "normY" to normY,
            "ink" to ink,
            "mode" to mode
        )

        database.getReference(GAMES_NODE).child(gameId).child(PLAYERS_NODE).child(playerId)
            .updateChildren(stateUpdate)
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to update player state for $playerId (Ask Gemini)", e)
                onDatabaseError?.invoke("Update failed: ${e.message}")
            }
    }

    /**
     * @param mazeY The Y-coordinate in the maze grid system (optional).
     */
    fun sendPaintAction(x: Int, y: Int, color: Int, mazeX: Int = -1, mazeY: Int = -1) {
        val gameId = currentGameId ?: return
        val paintRef = database.getReference(GAMES_NODE).child(gameId).child(PAINT_NODE).push()
        val paintAction = PaintAction(
            x = x,
            y = y,
            color = color,
            playerId = localPlayerId ?: "",
            timestamp = ServerValue.TIMESTAMP,
            mazeX = mazeX,
            mazeY = mazeY
        )
        paintRef.setValue(paintAction).addOnFailureListener { e ->
            Log.e(TAG, "Failed to send paint action", e)
        }
    }
} 

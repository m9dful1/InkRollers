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

// Data class to hold game settings read by clients
data class GameSettings(val durationMs: Long, val complexity: String)

class MultiplayerManager {

    private val database = Firebase.database("https://inkrollers-13595-default-rtdb.firebaseio.com/")
    private var gameRef: DatabaseReference? = null
    private var playersRef: DatabaseReference? = null
    private var childListener: ChildEventListener? = null
    private var initSnapshotListener: ValueEventListener? = null
    private var paintRef: DatabaseReference? = null
    private var rematchRef: DatabaseReference? = null

    // Add companion object back
    companion object {
        private const val TAG = "MultiplayerManager"
        private const val GAMES_NODE = "games"
        private const val PAINT_NODE = "paint"
        private const val REMATCH_NODE = "rematchRequests"
    }

    // Callback for database error notifications
    var onDatabaseError: ((String) -> Unit)? = null

    // Number of players in this game (used for rematch decision)
    private var expectedRematchCount: Long = 0L

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
        // Test Firebase connectivity
        testFirebaseConnection()
    }

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

    fun hostGame(initialPlayerState: PlayerState, durationMs: Long, complexity: String, callback: (success: Boolean, gameId: String?, gameSettings: GameSettings?) -> Unit) {
        clearListeners() // Clear any previous listeners
        
        // Ensure user is authenticated before proceeding
        if (auth.currentUser == null) {
            Log.e(TAG, "Cannot host game: User not authenticated")
            onDatabaseError?.invoke("Authentication required to host game")
            callback(false, null, null)
            return
        }
        Log.d(TAG, "User authenticated with UID: ${auth.currentUser?.uid}, proceeding with hostGame")
        
        currentGameId = generateGameId()
        gameRef = database.getReference(GAMES_NODE).child(currentGameId!!)
        playersRef = gameRef?.child("players")
        paintRef = gameRef?.child(PAINT_NODE)

        Log.d(TAG, "Hosting game with ID: $currentGameId")

        // Set host as player0
        localPlayerId = "player0"
        
        // Generate a maze seed for this game
        mazeSeed = System.currentTimeMillis()
        // Set the seed in the player state
        initialPlayerState.mazeSeed = mazeSeed
        Log.d(TAG, "Generated maze seed: $mazeSeed")
        
        createGameAfterConnectionTest(initialPlayerState, durationMs, complexity, callback)
    }
    
    private fun createGameAfterConnectionTest(initialPlayerState: PlayerState, durationMs: Long, complexity: String, callback: (success: Boolean, gameId: String?, gameSettings: GameSettings?) -> Unit) {
        try {
            // Use a map to set the initial game structure with player0
            val initialGameData = mapOf(
                "players" to mapOf(
                    localPlayerId!! to initialPlayerState
                ),
                "mazeSeed" to mazeSeed, // Store the seed at game level too
                "matchDurationMs" to durationMs, // Store match duration
                "mazeComplexity" to complexity,   // Store maze complexity
                "createdAt" to ServerValue.TIMESTAMP, // Optional: track game creation time
                "started" to false, // Explicitly set initial started state
                "playerCount" to 1L // Set initial player count
            )

            Log.d(TAG, "Attempting to write game data to Firebase: $currentGameId")
            Log.d(TAG, "Complete game data structure: $initialGameData")
            
            // Create game settings for the host too for consistency
            gameSettings = GameSettings(durationMs, complexity)
            
            // Write directly to the game node
            val setValueTask = gameRef?.setValue(initialGameData)
            
            setValueTask?.addOnSuccessListener {
                Log.i(TAG, "Game $currentGameId write initiated successfully (listener pending verification).")
                
                // Verify the game exists in the database to confirm it was written
                database.getReference("$GAMES_NODE/$currentGameId").addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            val storedSeed = snapshot.child("mazeSeed").getValue(Long::class.java)
                            if (storedSeed == mazeSeed) {
                                Log.d(TAG, "Verified game data was written correctly (mazeSeed matches)")
                                setupFirebaseListeners() // Start listening for other players
                                callback(true, currentGameId, gameSettings)
                            } else {
                                Log.e(TAG, "Game data verification failed - mazeSeed doesn't match: stored=$storedSeed, local=$mazeSeed")
                                // Don't call leaveGame here as the game might partially exist
                                onDatabaseError?.invoke("Game data mismatch after write")
                                callback(false, null, null)
                            }
                        } else {
                            Log.e(TAG, "ERROR: Game node $currentGameId does NOT exist after successful write callback.")
                            Log.e(TAG, "This strongly indicates a database permission/rule issue or data inconsistency.")
                            onDatabaseError?.invoke("Game creation failed verification")
                            // Don't call leaveGame here as the write might eventually appear
                            callback(false, null, null)
                        }
                    }
                    
                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Game data verification read failed", error.toException())
                        Log.e(TAG, "ERROR: Could not read back game data ($currentGameId). Database permissions issue?")
                        onDatabaseError?.invoke("Could not verify game creation: ${error.message}")
                        // Don't call leaveGame here
                        callback(false, null, null)
                    }
                })
            }?.addOnFailureListener { exception ->
                Log.e(TAG, "Firebase setValue operation failed for game $currentGameId", exception)
                Log.e(TAG, "ERROR: Could not create game. Likely a database permissions issue.")
                onDatabaseError?.invoke("Could not create game: ${exception.message}")
                // Don't call leaveGame here as we didn't successfully create it
                callback(false, null, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in createGameAfterConnectionTest", e)
            Log.e(TAG, "ERROR: Unexpected exception during game creation.")
            onDatabaseError?.invoke("Unexpected error creating game: ${e.message}")
            callback(false, null, null)
        }
    }

    // Simple random game ID generator
    private fun generateGameId(length: Int = 6): String {
        val allowedChars = ('A'..'Z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random(Random) }
            .joinToString("")
    }

    fun joinGame(gameId: String?, initialPlayerState: PlayerState, callback: (success: Boolean, playerId: String?, gameSettings: GameSettings?) -> Unit) {
        clearListeners()
        
        // Ensure user is authenticated before proceeding
        if (auth.currentUser == null) {
            Log.e(TAG, "Cannot join game: User not authenticated")
            onDatabaseError?.invoke("Authentication required to join game")
            callback(false, null, null)
            return
        }
        Log.d(TAG, "User authenticated with UID: ${auth.currentUser?.uid}, proceeding with joinGame")

        if (gameId == null) {
            // Find a random available game
            findRandomAvailableGame { randomGameId ->
                if (randomGameId != null) {
                    Log.i(TAG, "Found random game to join: $randomGameId")
                    // Call joinGame again with the found ID
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

        // Rest of the existing method for joining a specific game
        val potentialGameRef = database.getReference(GAMES_NODE).child(gameId)
        
        Log.d(TAG, "Attempting to join game: $gameId")

        // First, check the game itself to get the maze seed and validate the game exists
        potentialGameRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    Log.w(TAG, "Game $gameId does not exist.")
                    onDatabaseError?.invoke("Game ID $gameId not found")
                    callback(false, null, null)
                    return
                }

                // Get the maze seed from the game
                val gameMazeSeed = snapshot.child("mazeSeed").getValue(Long::class.java)
                // Get game settings
                val duration = snapshot.child("matchDurationMs").getValue(Long::class.java) ?: 180000L // Default 3 mins
                val complexity = snapshot.child("mazeComplexity").getValue(String::class.java) ?: HomeActivity.COMPLEXITY_HIGH
                gameSettings = GameSettings(duration, complexity)

                if (gameMazeSeed != null) {
                    mazeSeed = gameMazeSeed
                    initialPlayerState.mazeSeed = gameMazeSeed  // Use the host's seed
                    Log.d(TAG, "Retrieved maze seed from host: $mazeSeed")
                } else {
                    // Fallback if no seed found
                    Log.w(TAG, "No maze seed found in game data for $gameId, using local time")
                    mazeSeed = System.currentTimeMillis()
                    initialPlayerState.mazeSeed = mazeSeed
                }
                
                // Now check players to determine the next available ID
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

                // pick next available slot
                var nextIndex = 1
                while (playersNodeSnapshot.hasChild("player$nextIndex")) {
                    nextIndex++
                }
                val assignedId = "player$nextIndex"
                Log.i(TAG, "Joining game $gameId as $assignedId")

                // cache refs for this client
                currentGameId = gameId
                gameRef = potentialGameRef
                playersRef = potentialGameRef.child("players")
                paintRef = potentialGameRef.child(PAINT_NODE)
                localPlayerId = assignedId

                // add our state
                playersRef!!.child(assignedId).setValue(initialPlayerState)
                    .addOnSuccessListener {
                        Log.i(TAG, "Added $assignedId to Firebase game $gameId.")
                        setupFirebaseListeners()
                        // After both players (host and this joiner) are present, start listening for rematch decisions
                        setupRematchListener()
                        callback(true, assignedId, gameSettings)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to add $assignedId to game $gameId", e)
                        onDatabaseError?.invoke("Failed to join game: ${e.message}")
                        leaveGame() // Clean up if joining failed
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
     * Finds a random available game that has room for more players.
     * @param callback Called with the game ID if found, or null if no games available
     */
    private fun findRandomAvailableGame(callback: (String?) -> Unit) {
        val gamesRef = database.getReference(GAMES_NODE)
        
        Log.d(TAG, "Searching for available games...")
        
        gamesRef.orderByChild("createdAt").limitToLast(20) // Look at recent games
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
                    
                    for (gameSnapshot in snapshot.children.reversed()) { // Check newest first
                        val gameId = gameSnapshot.key ?: continue
                        
                        // Log the raw game data for debugging
                        Log.v(TAG, "Examining game: $gameId -> ${gameSnapshot.value}") // Use verbose logging
                        
                        // Check if game has players node and fewer than maxPlayers
                        val playersSnapshot = gameSnapshot.child("players")
                        if (!playersSnapshot.exists()) {
                            Log.v(TAG, "Game $gameId skipped: no players node")
                            skippedCount++
                            continue
                        }
                        
                        val playerCount = playersSnapshot.childrenCount
                        val maxPlayers = 4
                        
                        if (playerCount == 0L || playerCount >= maxPlayers) { // Skip empty or full games
                            Log.v(TAG, "Game $gameId skipped: Player count $playerCount (max $maxPlayers)")
                            skippedCount++
                            continue
                        }
                        
                        // Check if game has already started
                        val started = gameSnapshot.child("started").getValue(Boolean::class.java)
                        if (started == true) {
                            Log.v(TAG, "Game $gameId skipped: already started")
                            skippedCount++
                            continue
                        }
                        
                        // Game is available
                        availableGames.add(gameId)
                        Log.d(TAG, "Found potentially available game: $gameId with $playerCount players")
                    }
                    
                    Log.d(TAG, "Search result: ${availableGames.size} potentially available games, $skippedCount skipped")
                    
                    // Select a random game from available ones
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

    fun updateLocalPlayerState(newState: PlayerState) {
        if (localPlayerId == null || playersRef == null) return
        // Push newState to Firebase under playersRef.child(localPlayerId!!)
        playersRef?.child(localPlayerId!!)?.setValue(newState)?.addOnFailureListener { e ->
            Log.w(TAG, "Failed to update player state for $localPlayerId", e)
            onDatabaseError?.invoke("Update failed: ${e.message}")
        } 
        // Log.v(TAG, "Updating local player state for $localPlayerId") // Don't log every update
    }

    /**
     * Updates only the specified fields for the local player using updateChildren.
     * @param updateMap A map where keys are the field names in PlayerState (e.g., "normX", "ink")
     *                  and values are the new values for those fields.
     */
    fun updateLocalPlayerPartialState(updateMap: Map<String, Any>) {
        if (localPlayerId == null || playersRef == null) return
        playersRef?.child(localPlayerId!!)?.updateChildren(updateMap)?.addOnFailureListener { e ->
            Log.w(TAG, "Failed to perform partial update for $localPlayerId with keys ${updateMap.keys}", e)
            onDatabaseError?.invoke("Partial update failed: ${e.message}")
        }
        Log.v(TAG, "Updating partial state for $localPlayerId: ${updateMap.keys}") // Verbose log
    }

    fun setupFirebaseListeners() {
        if (playersRef == null) return
        clearListeners() // Ensure no old listeners are active

        Log.d(TAG, "Setting up Firebase listeners for game: $currentGameId")

        /* 1️⃣ one-shot full snapshot */
        initSnapshotListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "Initial player snapshot received for $currentGameId. Processing ${snapshot.childrenCount} players.")
                snapshot.children.forEach { handlePlayerData(it) }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Initial player snapshot cancelled for $currentGameId", error.toException())
                onDatabaseError?.invoke("Initial load error: ${error.message}")
            }
        }
        playersRef!!.addListenerForSingleValueEvent(initSnapshotListener!!)
        Log.d(TAG, "Added single value event listener for initial player state.")

        /* 2️⃣ incremental updates */
        childListener = object : ChildEventListener {
            override fun onChildAdded(snap: DataSnapshot, prev: String?) {
                Log.v(TAG, "Child added: ${snap.key} in $currentGameId") // Verbose
                handlePlayerData(snap)
            }
            override fun onChildChanged(snap: DataSnapshot, prev: String?) {
                 Log.v(TAG, "Child changed: ${snap.key} in $currentGameId") // Verbose
                 handlePlayerData(snap)
            }
            override fun onChildRemoved(snap: DataSnapshot) {
                 val playerId = snap.key
                 if (playerId != null) { // Handle local player removal too for cleanup
                    Log.d(TAG, "Player removed via child event: $playerId in $currentGameId")
                    updateListener?.onPlayerRemoved(playerId)
                 }
            }
            override fun onChildMoved(snap: DataSnapshot, prev: String?) {
                 // Not typically used here
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Player child listener cancelled for $currentGameId", error.toException())
                onDatabaseError?.invoke("Player listener error: ${error.message}")
                updateListener = null
            }
        }
        playersRef!!.addChildEventListener(childListener!!)
        Log.d(TAG, "Added child event listener for player updates.")

        /* 3️⃣ Listen for paint actions */
        setupPaintListener()

        // Listen for number of connected players (to trigger pre-match)
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

        // Listen for match start signal from host
        startRef = gameRef?.child("started")
        startListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val started = snapshot.getValue(Boolean::class.java) ?: false
                Log.d(TAG, "Match start signal received: started=$started for game $currentGameId")
                if (started) {
                    onMatchStartRequested?.invoke()
                    // Detach listener after triggering once
                    startRef?.removeEventListener(this)
                    startListener = null // Avoid memory leak
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
    
    private fun handlePlayerData(snapshot: DataSnapshot) {
         val playerId = snapshot.key
         if (playerId == null) {
             Log.w(TAG, "handlePlayerData received null playerId in game $currentGameId")
             return // Ignore invalid data
         }
         try {
             val playerState = snapshot.getValue(PlayerState::class.java)
             if (playerState != null) {
                 // Log slightly differently for local vs remote for clarity
                 val logPrefix = if (playerId == localPlayerId) "Processing LOCAL" else "Received REMOTE"
                 // Log normalized coordinates now
                 Log.v(TAG, "$logPrefix state for $playerId in $currentGameId: NormPos=(${playerState.normX}, ${playerState.normY}), Ink=${playerState.ink}, Mode=${playerState.mode}, Active=${playerState.active}") // Verbose
                 updateListener?.onPlayerStateChanged(playerId, playerState)
             } else {
                 Log.w(TAG, "Received null player state for $playerId in game $currentGameId")
             }
         } catch (e: DatabaseException) {
             Log.e(TAG, "Failed to parse player state for $playerId in game $currentGameId", e)
             onDatabaseError?.invoke("Error parsing player state for $playerId")
         }
    }

    fun leaveGame() {
        val gameIdToLeave = currentGameId
        val playerToRemove = localPlayerId
        Log.d(TAG, "Leaving game: $gameIdToLeave as player $playerToRemove")
        
        val gameToRemoveRef = gameRef // Capture the reference before clearing
        
        // Set local player 'active' status to false first (optional, but good practice)
        // Use a map for potential atomic update if other fields were involved
        val updates = mapOf("active" to false)
        if (playerToRemove != null && playersRef != null) {
             playersRef?.child(playerToRemove)?.updateChildren(updates)
                 ?.addOnCompleteListener { task -> 
                     Log.d(TAG, "Marked player $playerToRemove inactive in game $gameIdToLeave. Success: ${task.isSuccessful}")
                     // Proceed with game removal check after marking inactive
                     checkAndRemoveGameIfEmpty(gameToRemoveRef)
                 }
        } else {
            // If we can't mark inactive, still check if game should be removed
            checkAndRemoveGameIfEmpty(gameToRemoveRef)
        }
        
        // Clean up local state and listeners immediately
        clearListenersAndResetState()
    }
    
    private fun checkAndRemoveGameIfEmpty(gameToRemoveRef: DatabaseReference?) {
        val gameId = gameToRemoveRef?.key
        Log.d(TAG, "Checking if game $gameId should be removed...")
        gameToRemoveRef?.child("players")?.addListenerForSingleValueEvent(object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var activePlayerFound = false
                if (snapshot.exists()) {
                    for (playerSnap in snapshot.children) {
                        val isActive = playerSnap.child("active").getValue(Boolean::class.java) ?: false
                        if (isActive) {
                            activePlayerFound = true
                            break
                        }
                    }
                }
                
                if (!activePlayerFound) {
                    Log.i(TAG, "No active players remaining in game $gameId. Removing game node.")
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
        // Don't clear listeners attached to MainActivity
        // onDatabaseError = null 
        // onRematchDecision = null
        // onPlayerCountChanged = null
        // onMatchStartRequested = null
        
        Log.d(TAG, "Local MultiplayerManager state reset.")
    }
    
    private fun clearListeners() {
        // Remove child listener
        childListener?.let { listener ->
            try { playersRef?.removeEventListener(listener) } catch (e: Exception) { Log.w(TAG, "Error removing child listener", e) }
            Log.d(TAG, "Removed child listener.")
        }
        childListener = null // Clear reference

        initSnapshotListener = null // Clear reference for single value listener

        // Remove paint listener
        paintListener?.let { listener ->
            try { paintRef?.removeEventListener(listener) } catch (e: Exception) { Log.w(TAG, "Error removing paint listener", e) }
        }
        paintListener = null

        // Remove player count listener
        playerCountListener?.let { listener -> 
             try { playersRef?.removeEventListener(listener) } catch (e: Exception) { Log.w(TAG, "Error removing player count listener", e) } 
        }
        playerCountListener = null

        // Remove match start listener
        startListener?.let { listener -> 
            try { startRef?.removeEventListener(listener) } catch (e: Exception) { Log.w(TAG, "Error removing start listener", e) }
        }
        startListener = null
        // Keep startRef itself until state is reset

        Log.d(TAG, "Detached Firebase listeners (player, paint, count, start). Rematch listener managed separately.")
    }

    /**
     * Fetches the current state of all players in the game.
     * @param callback Called with a map of player ID to PlayerState.
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
                            playerStates[playerId] = null // Indicate parse failure
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

    /** Host invokes this to signal all clients to begin countdown */
    fun sendMatchStart() {
        if (gameRef == null) return
        Log.d(TAG, "Host sending match start signal for game $currentGameId")
        // Store the player count at match start
        playersRef?.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val playerCount = snapshot.childrenCount
                Log.d(TAG, "sendMatchStart: Storing playerCount=$playerCount in game node for $currentGameId")
                gameRef!!.child("playerCount").setValue(playerCount)
                    .addOnCompleteListener {
                        // Write a synchronized startTime (2 seconds in the future)
                        val startTime = System.currentTimeMillis() + 2000L
                        val updates = mapOf(
                            "started" to true,
                            "startTime" to startTime
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
                // Fallback: still try to start
                val startTime = System.currentTimeMillis() + 2000L
                val updates = mapOf(
                    "started" to true,
                    "startTime" to startTime
                )
                gameRef!!.updateChildren(updates)
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Failed to send match start for $currentGameId", e)
                        onDatabaseError?.invoke("Failed to send start signal: "+e.message)
                    }
            }
        })
    }

    /**
     * Send a paint action to Firebase when a player paints at a specific location
     * @param x Screen X coordinate (for local rendering)
     * @param y Screen Y coordinate (for local rendering)
     * @param color The paint color
     * @param normalizedX Optional normalized X coordinate (0-1) relative to maze width
     * @param normalizedY Optional normalized Y coordinate (0-1) relative to maze height
     */
    fun sendPaintAction(x: Int, y: Int, color: Int, normalizedX: Float? = null, normalizedY: Float? = null) {
        if (paintRef == null || !isConnected()) return
        // Use push() to ensure each paint action is stored as a unique child
        val paintData = hashMapOf<String, Any>(
            // "x" to x, // Don't need absolute X anymore
            // "y" to y, // Don't need absolute Y anymore
            "color" to color,
            "timestamp" to ServerValue.TIMESTAMP,
            "player" to (localPlayerId ?: "unknown")
        ).apply {
            normalizedX?.let { put("normalizedX", it) }
            normalizedY?.let { put("normalizedY", it) }
        }
        // Only send if we have normalized coordinates
        if (normalizedX != null && normalizedY != null) {
            paintRef?.push()?.setValue(paintData)
                ?.addOnFailureListener { e ->
                    Log.w(TAG, "Failed to send paint action for $localPlayerId in $currentGameId", e)
                    // Maybe throttle this error? Could spam if network is bad.
                    // onDatabaseError?.invoke("Paint failed: ${e.message}") 
                }
        } else {
             Log.v(TAG, "Skipping paint action send: missing normalized coordinates")
        }
    }
    
    // Check if we're connected to a game
    private fun isConnected(): Boolean {
        return currentGameId != null && localPlayerId != null
    }

    private fun setupPaintListener() {
        if (paintRef == null) return
        
        // Detach existing listener if any
        paintListener?.let { listener ->
             try { paintRef?.removeEventListener(listener) } catch (e: Exception) { Log.w(TAG, "Error removing old paint listener", e) }
        }
        paintListener = null
        
        paintListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                // Process new paint action
                processPaintAction(snapshot)
            }
            
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                // Process updated paint action (should be rare)
                processPaintAction(snapshot)
            }
            
            override fun onChildRemoved(snapshot: DataSnapshot) {
                // Not handling paint removal currently
            }
            
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                // Not relevant for paint actions
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Paint listener cancelled for $currentGameId", error.toException())
                 onDatabaseError?.invoke("Paint listener error: ${error.message}")
            }
        }
        
        // Listen for paint actions AFTER the current time to avoid processing old ones on join
        paintRef!!.orderByChild("timestamp").startAt(System.currentTimeMillis().toDouble())
                .addChildEventListener(paintListener!!)
        Log.d(TAG, "Added paint action listener for $currentGameId (listening from now onwards).")
    }
    
    // Process a paint action received from Firebase
    private fun processPaintAction(snapshot: DataSnapshot) {
        try {
            // val x = snapshot.child("x").getValue(Int::class.java) // No longer using absolute X
            // val y = snapshot.child("y").getValue(Int::class.java) // No longer using absolute Y
            val color = snapshot.child("color").getValue(Int::class.java)
            val player = snapshot.child("player").getValue(String::class.java)
            
            // Try to get normalized coordinates (stored as Double) then cast to Float
            val dnX = snapshot.child("normalizedX").getValue(Double::class.java)
            val dnY = snapshot.child("normalizedY").getValue(Double::class.java)
            
            if (color != null && player != null) {
                // Don't process our own paint actions to avoid double-painting
                if (player != localPlayerId) {
                    if (dnX != null && dnY != null) {
                        val normalizedX = dnX.toFloat()
                        val normalizedY = dnY.toFloat()
                        // Use normalized coordinates for better cross-device sync
                        Log.v(TAG, "Received normalized paint from $player at ($normalizedX,$normalizedY) with color #${color.toString(16)} in game $currentGameId") // Verbose
                        updateListener?.onPaintAction(0, 0, color, normalizedX, normalizedY) // Pass 0,0 for x,y as they aren't used
                    } else {
                        // Log if we received an action without normalized coords (shouldn't happen often)
                        Log.w(TAG, "Received paint action from $player without normalized coordinates in $currentGameId. Ignoring.")
                    }
                }
            } else {
                 Log.w(TAG, "Received incomplete paint action in $currentGameId: $snapshot")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process paint action snapshot in $currentGameId: $snapshot", e)
            onDatabaseError?.invoke("Error processing paint action")
        }
    }

    /**
     * Send this player's rematch answer (true=Yes, false=No) to Firebase.
     */
    fun sendRematchAnswer(wantRematch: Boolean) {
        Log.d(TAG, "sendRematchAnswer called. wantRematch=$wantRematch, currentGameId=$currentGameId, localPlayerId=$localPlayerId")
        if (currentGameId == null || localPlayerId == null) {
            Log.w(TAG, "sendRematchAnswer: currentGameId or localPlayerId is null. Aborting.")
            return
        }
        if (rematchRef == null) rematchRef = gameRef?.child(REMATCH_NODE)
        Log.d(TAG, "Sending rematch answer ($wantRematch) for player $localPlayerId in game $currentGameId")
        rematchRef?.child(localPlayerId!!)?.setValue(wantRematch)
           ?.addOnFailureListener { e -> 
                Log.w(TAG, "Failed to send rematch answer for $localPlayerId in $currentGameId", e) 
                onDatabaseError?.invoke("Rematch answer failed: ${e.message}")
            }
    }

    /**
     * Clears the rematch answers node in Firebase.
     * Only the host should ideally call this during restart logic.
     */
    fun clearRematchAnswers() {
        Log.d(TAG, "clearRematchAnswers called for game $currentGameId")
        Log.d(TAG, "Clearing rematch answers for game $currentGameId")
        rematchRef?.removeValue()
            ?.addOnFailureListener { e -> 
                Log.w(TAG, "Failed to clear rematch answers for $currentGameId", e)
                // Don't necessarily notify user for cleanup failures
            }
    }

    // Listener for rematch answers
    private var rematchListener: ValueEventListener? = null
    /**
     * Listen for all players' rematch answers and invoke callback when decisions are in.
     * This listener should be setup *once* per match, ideally after game creation/join
     * and re-attached in restartMatch.
     */
    fun setupRematchListener() {
        Log.d(TAG, "setupRematchListener called for game $currentGameId")
        if (rematchRef == null) rematchRef = gameRef?.child(REMATCH_NODE)
        if (rematchInProgressRef == null) rematchInProgressRef = gameRef?.child("rematchInProgress")
        // Remove previous listener if any to avoid duplicates
        rematchListener?.let { listener ->
             try { rematchRef?.removeEventListener(listener) } catch (e: Exception) { Log.w(TAG, "Error removing old rematch listener", e) }
        }
        rematchListener = null
        rematchInProgressListener?.let { listener ->
            try { rematchInProgressRef?.removeEventListener(listener) } catch (e: Exception) { Log.w(TAG, "Error removing old rematchInProgress listener", e) }
        }
        rematchInProgressListener = null
        Log.d(TAG, "Setting up rematch listener for game $currentGameId")

        // Always clear rematchInProgress before attaching listeners to avoid stale triggers
        rematchInProgressRef?.setValue(false)?.addOnCompleteListener {
            Log.d(TAG, "rematchInProgress explicitly set to FALSE before attaching rematch listeners for $currentGameId")
            // Always fetch the number of *active* players from the players node
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

        // Listen for rematchInProgress flag to coordinate reset
        rematchInProgressListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val inProgress = snapshot.getValue(Boolean::class.java) ?: false
                Log.d(TAG, "rematchInProgressListener: rematchInProgress=$inProgress for $currentGameId")
                if (inProgress) {
                    // Defensive: Only allow if all players have answered YES
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
     * Overwrites the state for all specified players in Firebase.
     * Used to reset players to initial state for rematches.
     * @param initialStates Map of player ID -> initial PlayerState object.
     */
    fun resetAllPlayerStatesFirebase(initialStates: Map<String, PlayerState>) {
        Log.d(TAG, "resetAllPlayerStatesFirebase called for game $currentGameId. initialStates=$initialStates")
        if (playersRef == null) {
            Log.e(TAG, "Cannot reset player states, playersRef is null for $currentGameId")
            onDatabaseError?.invoke("Cannot reset players (no reference)")
            return
        }
        Log.d(TAG, "Resetting player states in Firebase for players: ${initialStates.keys} in game $currentGameId")
        // Use updateChildren for potentially better efficiency than individual setValue calls
        playersRef?.updateChildren(initialStates as Map<String, Any?>)
            ?.addOnSuccessListener { Log.i(TAG, "Successfully reset player states in Firebase for $currentGameId.") }
            ?.addOnFailureListener { e -> 
                 Log.e(TAG, "Failed to reset player states in Firebase for $currentGameId.", e)
                 onDatabaseError?.invoke("Failed to reset players: ${e.message}")
            }
    }

    /**
     * Clears all paint actions in the current game (used for rematch).
     * Only host should call this.
     */
    fun clearPaintActions() {
        Log.d(TAG, "Clearing paint actions for game $currentGameId")
        paintRef?.removeValue()?.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Failed to clear paint actions for $currentGameId", task.exception)
            }
        }
    }

    /** Returns the current user's Firebase UID, or null if not authenticated. */
    fun getCurrentUserUid(): String? {
        return auth.currentUser?.uid
    }
} 

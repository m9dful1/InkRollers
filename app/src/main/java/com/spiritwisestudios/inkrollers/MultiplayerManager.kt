package com.spiritwisestudios.inkrollers

import android.util.Log
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.database.*
import com.google.firebase.database.ChildEventListener
import kotlin.random.Random

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

    // Add companion object back
    companion object {
        private const val TAG = "MultiplayerManager"
        private const val GAMES_NODE = "games"
        private const val PAINT_NODE = "paint"
        private const val REMATCH_NODE = "rematchRequests"
    }

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
                    Log.w(TAG, "Firebase NOT connected - check network/database rules")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase connection check cancelled", error.toException())
            }
        })
    }

    fun hostGame(initialPlayerState: PlayerState, durationMs: Long, complexity: String, callback: (success: Boolean, gameId: String?, gameSettings: GameSettings?) -> Unit) {
        clearListeners() // Clear any previous listeners
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
        
        try {
            // First, do a simple test write to the database
            database.getReference(".info/serverTimeOffset").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    Log.d(TAG, "Successfully connected to Firebase, server time offset: ${snapshot.getValue(Long::class.java)}")
                    
                    // Continue with game creation
                    createGameAfterConnectionTest(initialPlayerState, durationMs, complexity, callback)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to connect to Firebase: ${error.message}", error.toException())
                    callback(false, null, null)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Firebase connection test", e)
            callback(false, null, null)
        }
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
                "started" to false // Explicitly set initial started state
            )

            Log.d(TAG, "Attempting to write game data to Firebase: $currentGameId")
            
            // Create game settings for the host too for consistency
            gameSettings = GameSettings(durationMs, complexity)
            
            // Try a simpler approach - write directly to the game node
            val setValueTask = gameRef?.setValue(initialGameData)
            
            setValueTask?.addOnSuccessListener {
                Log.i(TAG, "Game $currentGameId created successfully.")
                
                // Check if the data was actually written by reading it back
                gameRef?.child("mazeSeed")?.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val storedSeed = snapshot.getValue(Long::class.java)
                        if (storedSeed == mazeSeed) {
                            Log.d(TAG, "Verified game data was written correctly (mazeSeed matches)")
                            setupFirebaseListeners() // Start listening for other players
                            setupRematchListener()
                            callback(true, currentGameId, gameSettings)
                        } else {
                            Log.e(TAG, "Game data verification failed - mazeSeed doesn't match: stored=$storedSeed, local=$mazeSeed")
                            callback(false, null, null)
                        }
                    }
                    
                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Game data verification failed", error.toException())
                        callback(false, null, null)
                    }
                })
            }?.addOnFailureListener { exception ->
                Log.e(TAG, "Firebase operation failed in createGameAfterConnectionTest", exception)
                callback(false, null, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in createGameAfterConnectionTest", e)
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
        
        if (gameId == null) {
            // Find a random available game
            findRandomAvailableGame { randomGameId ->
                if (randomGameId != null) {
                    Log.i(TAG, "Found random game to join: $randomGameId")
                    // Call joinGame again with the found ID
                    joinGame(randomGameId, initialPlayerState, callback)
                } else {
                    Log.w(TAG, "No available games found to join")
                    callback(false, null, null)
                }
            }
            return
        }
        
        Log.d(TAG, "joinGame(): requested gameId=$gameId")

        val potentialGameRef = database.getReference(GAMES_NODE).child(gameId)
        
        Log.d(TAG, "Attempting to join game: $gameId")

        // First, check the game itself to get the maze seed and validate the game exists
        potentialGameRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    Log.w(TAG, "Game $gameId does not exist.")
                    callback(false, null, null)
                    return
                }

                // Get the maze seed from the game
                val gameMazeSeed = snapshot.child("mazeSeed").getValue(Long::class.java)
                // Get game settings
                val duration = snapshot.child("matchDurationMs").getValue(Long::class.java) ?: 180000L // Default 3 mins
                val complxty = snapshot.child("mazeComplexity").getValue(String::class.java) ?: HomeActivity.COMPLEXITY_HIGH
                gameSettings = GameSettings(duration, complxty)

                if (gameMazeSeed != null) {
                    mazeSeed = gameMazeSeed
                    initialPlayerState.mazeSeed = gameMazeSeed  // Use the host's seed
                    Log.d(TAG, "Retrieved maze seed from host: $mazeSeed")
                } else {
                    // Fallback if no seed found
                    Log.w(TAG, "No maze seed found in game data, using local time")
                    mazeSeed = System.currentTimeMillis()
                    initialPlayerState.mazeSeed = mazeSeed
                }
                
                // Now check players to determine the next available ID
                val playersNodeSnapshot = snapshot.child("players")
                if (!playersNodeSnapshot.exists()) {
                    Log.w(TAG, "Game $gameId exists but has no players node")
                    callback(false, null, null)
                    return
                }

                val playerCount = playersNodeSnapshot.childrenCount
                val maxPlayers = 4
                if (playerCount >= maxPlayers) {
                    Log.w(TAG, "Game $gameId full ($playerCount players).")
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
                        Log.i(TAG, "Added $assignedId to Firebase.")
                        setupFirebaseListeners()
                        // start listening for rematch decisions
                        setupRematchListener()
                        callback(true, assignedId, gameSettings)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to add $assignedId", e)
                        leaveGame()
                        callback(false, null, null)
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "joinGame() cancelled: ${error.message}", error.toException())
                callback(false, null, null)
            }
        })
    }

    fun updateLocalPlayerState(newState: PlayerState) {
        if (localPlayerId == null || playersRef == null) return
        // Push newState to Firebase under playersRef.child(localPlayerId!!)
        playersRef?.child(localPlayerId!!)?.setValue(newState)?.addOnFailureListener { e ->
            Log.w(TAG, "Failed to update player state for $localPlayerId", e)
            // Maybe implement retry or notify user?
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
        }
        Log.d(TAG, "Updating partial state for $localPlayerId: ${updateMap.keys}")
    }

    private fun setupFirebaseListeners() {
        if (playersRef == null) return
        clearListeners() // Ensure no old listeners are active

        Log.d(TAG, "Setting up Firebase listeners for game: $currentGameId")

        /* 1️⃣ one-shot full snapshot */
        initSnapshotListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "Initial snapshot received. Processing ${snapshot.childrenCount} players.")
                snapshot.children.forEach { handlePlayerData(it) }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Initial snapshot cancelled", error.toException())
            }
        }
        playersRef!!.addListenerForSingleValueEvent(initSnapshotListener!!)
        Log.d(TAG, "Added single value event listener for initial state.")

        /* 2️⃣ incremental updates */
        childListener = object : ChildEventListener {
            override fun onChildAdded(snap: DataSnapshot, prev: String?) {
                // Check if this player was already processed by the initial snapshot
                // This might happen in race conditions, though less likely with separate listeners.
                // A simple check could be to see if the listener already exists in a local map,
                // but for now, handlePlayerData should be idempotent enough.
                Log.d(TAG, "Child added: ${snap.key}")
                handlePlayerData(snap) // Handle add even if potentially seen in initial snapshot
            }
            override fun onChildChanged(snap: DataSnapshot, prev: String?) {
                 Log.d(TAG, "Child changed: ${snap.key}")
                 handlePlayerData(snap)
            }
            override fun onChildRemoved(snap: DataSnapshot) {
                 val playerId = snap.key
                 if (playerId != null && playerId != localPlayerId) {
                    Log.d(TAG, "Player removed via child event: $playerId")
                    updateListener?.onPlayerRemoved(playerId)
                 }
            }
            override fun onChildMoved(snap: DataSnapshot, prev: String?) {
                 Log.d(TAG, "Child moved: ${snap.key} (Not typically used here)")
                 // Handle if needed, e.g., reordering logic
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Child listener cancelled", error.toException())
                // Consider informing the user or attempting to reconnect
                updateListener = null
            }
        }
        playersRef!!.addChildEventListener(childListener!!)
        Log.d(TAG, "Added child event listener for incremental updates.")

        /* 3️⃣ Listen for paint actions */
        setupPaintListener()

        // Determine expected rematch participants count
        playersRef?.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                expectedRematchCount = snapshot.childrenCount
                Log.d(TAG, "Expected rematch count: $expectedRematchCount")
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Listen for number of connected players (to trigger pre-match)
        playerCountListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onPlayerCountChanged?.invoke(snapshot.childrenCount.toInt())
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        playersRef!!.addValueEventListener(playerCountListener!!)

        // Listen for match start signal from host
        startRef = gameRef?.child("started")
        startListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val started = snapshot.getValue(Boolean::class.java) ?: false
                if (started) {
                    onMatchStartRequested?.invoke()
                    startRef?.removeEventListener(this)
                    onMatchStartRequested = null
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        startRef?.addValueEventListener(startListener!!)
    }
    
    private fun handlePlayerData(snapshot: DataSnapshot) {
         val playerId = snapshot.key
         if (playerId == null) {
             Log.w(TAG, "handlePlayerData received null playerId")
             return // Ignore invalid data
         }
         try {
             val playerState = snapshot.getValue(PlayerState::class.java)
             if (playerState != null) {
                 // Log slightly differently for local vs remote for clarity
                 val logPrefix = if (playerId == localPlayerId) "Processing LOCAL" else "Received REMOTE"
                 // Log normalized coordinates now
                 Log.d(TAG, "$logPrefix state for $playerId: NormPos=(${playerState.normX}, ${playerState.normY}), Active=${playerState.active}")
                 updateListener?.onPlayerStateChanged(playerId, playerState)
             } else {
                 Log.w(TAG, "Received null player state for $playerId")
             }
         } catch (e: DatabaseException) {
             Log.e(TAG, "Failed to parse player state for $playerId", e)
         }
    }

    fun leaveGame() {
        Log.d(TAG, "Leaving game: $currentGameId")
        val gameToRemoveRef = gameRef // Capture the reference before clearing
        // Set local player 'active' status to false first (optional, but good practice)
        if (localPlayerId != null && playersRef != null) {
             playersRef?.child(localPlayerId!!)?.child("active")?.setValue(false)
                 ?.addOnCompleteListener { /* No-op or log */ }
        }
        // Remove the entire game node from Firebase
        gameToRemoveRef?.removeValue()
            ?.addOnSuccessListener { Log.i(TAG, "Successfully removed game $currentGameId from Firebase.") }
            ?.addOnFailureListener { e -> Log.w(TAG, "Failed to remove game $currentGameId from Firebase.", e) }
        // Clean up local state and listeners
        clearListenersAndResetState()
    }
    
    private fun clearListenersAndResetState() {
        clearListeners()
        gameRef = null
        playersRef = null
        localPlayerId = null
        currentGameId = null
        Log.d(TAG, "Listeners cleared and state reset.")
    }
    
    private fun clearListeners() {
        // Remove child listener
        childListener?.let { listener ->
            playersRef?.removeEventListener(listener)
            Log.d(TAG, "Removed child listener.")
        }
        childListener = null // Clear reference

        // Although addListenerForSingleValueEvent doesn't strictly require manual removal
        // if the listener object itself goes out of scope, it's good practice
        // to nullify the reference if we are explicitly managing listeners.
        // No need to call removeEventListener for the initSnapshotListener as it detaches automatically.
        initSnapshotListener = null // Clear reference
        // initSnapshotListener?.let { playersRef?.removeEventListener(it) } // Not needed for single value event

        // Remove paint listener
        paintListener?.let { listener ->
            paintRef?.removeEventListener(listener)
        }
        paintListener = null

        // Remove player count listener
        playerCountListener?.let { playersRef?.removeEventListener(it) }
        playerCountListener = null

        // Remove match start listener
        startListener?.let { startRef?.removeEventListener(it) }
        startListener = null
        startRef = null

        Log.d(TAG, "Firebase listeners cleared.")
    }

    /** Host invokes this to signal all clients to begin countdown */
    fun sendMatchStart() {
        if (gameRef == null) return
        gameRef!!.child("started").setValue(true)
            .addOnFailureListener { e -> Log.w(TAG, "Failed to send match start", e) }
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
        val paintData = hashMapOf(
            "x" to x,
            "y" to y,
            "color" to color,
            "timestamp" to ServerValue.TIMESTAMP,
            "player" to (localPlayerId ?: "unknown")
        ).apply {
            normalizedX?.let { put("normalizedX", it) }
            normalizedY?.let { put("normalizedY", it) }
        }
        paintRef?.push()?.setValue(paintData)
            ?.addOnFailureListener { e ->
                Log.w(TAG, "Failed to send paint action at ($x,$y)", e)
            }
    }
    
    // Check if we're connected to a game
    private fun isConnected(): Boolean {
        return currentGameId != null && localPlayerId != null
    }

    private fun setupPaintListener() {
        if (paintRef == null) return
        
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
                Log.w(TAG, "Paint listener cancelled", error.toException())
            }
        }
        
        paintRef!!.addChildEventListener(paintListener!!)
        Log.d(TAG, "Added paint action listener.")
    }
    
    // Process a paint action received from Firebase
    private fun processPaintAction(snapshot: DataSnapshot) {
        try {
            val x = snapshot.child("x").getValue(Int::class.java)
            val y = snapshot.child("y").getValue(Int::class.java)
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
                        Log.d(TAG, "Received normalized paint from $player at ($normalizedX,$normalizedY) with color #${color.toString(16)}")
                        updateListener?.onPaintAction(x ?: 0, y ?: 0, color, normalizedX, normalizedY)
                    } else if (x != null && y != null) {
                        // Fallback to absolute coordinates
                        Log.d(TAG, "Received paint from $player at ($x,$y) with color #${color.toString(16)}")
                        updateListener?.onPaintAction(x, y, color)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process paint action", e)
        }
    }

    /**
     * Send this player's rematch answer (true=Yes, false=No) to Firebase.
     */
    fun sendRematchAnswer(wantRematch: Boolean) {
        if (currentGameId == null) return
        if (rematchRef == null) rematchRef = gameRef?.child(REMATCH_NODE)
        rematchRef?.child(localPlayerId!!)?.setValue(wantRematch)
    }

    /**
     * Clears the rematch answers node in Firebase.
     */
    fun clearRematchAnswers() {
        rematchRef?.removeValue()
            ?.addOnFailureListener { e -> Log.w(TAG, "Failed to clear rematch answers", e) }
    }

    /**
     * Listen for all players' rematch answers and invoke callback when decisions are in.
     */
    fun setupRematchListener() {
        if (rematchRef == null) rematchRef = gameRef?.child(REMATCH_NODE)
        // One-time listener for full snapshot
        rematchRef?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Expect rematch answers from all connected players
                if (expectedRematchCount > 0 && snapshot.childrenCount >= expectedRematchCount) {
                    // All have answered
                    var allYes = true
                    snapshot.children.forEach {
                        val ans = it.getValue(Boolean::class.java) ?: false
                        if (!ans) allYes = false
                    }
                    onRematchDecision?.invoke(allYes)
                    // Clean up listener
                    rematchRef?.removeEventListener(this)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Rematch listener cancelled", error.toException())
            }
        })
    }

    /**
     * Overwrites the state for all specified players in Firebase.
     * Used to reset players to initial state for rematches.
     * @param initialStates Map of player ID -> initial PlayerState object.
     */
    fun resetAllPlayerStatesFirebase(initialStates: Map<String, PlayerState>) {
        if (playersRef == null) {
            Log.e(TAG, "Cannot reset player states, playersRef is null")
            return
        }
        Log.d(TAG, "Resetting player states in Firebase for players: ${initialStates.keys}")
        // Use updateChildren for potentially better efficiency than individual setValue calls
        playersRef?.updateChildren(initialStates as Map<String, Any?>)
            ?.addOnSuccessListener { Log.i(TAG, "Successfully reset player states in Firebase.") }
            ?.addOnFailureListener { e -> Log.e(TAG, "Failed to reset player states in Firebase.", e) }
    }

    /**
     * Clears all paint actions in the current game (used for rematch).
     */
    fun clearPaintActions() {
        paintRef?.removeValue()?.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Failed to clear paint actions", task.exception)
            }
        }
    }

    /**
     * Finds a random available game that has room for more players.
     * @param callback Called with the game ID if found, or null if no games available
     */
    private fun findRandomAvailableGame(callback: (String?) -> Unit) {
        val gamesRef = database.getReference(GAMES_NODE)
        
        Log.d(TAG, "Searching for available games...")
        
        gamesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    if (!snapshot.exists()) {
                        Log.d(TAG, "No games exist in the database")
                        callback(null)
                        return
                    }
                    
                    Log.d(TAG, "Found ${snapshot.childrenCount} games total")
                    val availableGames = mutableListOf<String>()
                    var skippedCount = 0
                    
                    for (gameSnapshot in snapshot.children) {
                        val gameId = gameSnapshot.key ?: continue
                        
                        // Log the raw game data for debugging
                        Log.d(TAG, "Examining game: $gameId")
                        
                        // Check if game has players node and fewer than maxPlayers
                        val playersSnapshot = gameSnapshot.child("players")
                        if (!playersSnapshot.exists()) {
                            Log.d(TAG, "Game $gameId skipped: no players node")
                            skippedCount++
                            continue
                        }
                        
                        val playerCount = playersSnapshot.childrenCount
                        val maxPlayers = 4
                        
                        if (playerCount >= maxPlayers) {
                            Log.d(TAG, "Game $gameId skipped: full with $playerCount players")
                            skippedCount++
                            continue
                        }
                        
                        // Check if game has already started
                        val started = gameSnapshot.child("started").getValue(Boolean::class.java)
                        if (started == true) {
                            Log.d(TAG, "Game $gameId skipped: already started")
                            skippedCount++
                            continue
                        }
                        
                        // Game is available
                        availableGames.add(gameId)
                        Log.d(TAG, "Found available game: $gameId with $playerCount players")
                    }
                    
                    Log.d(TAG, "Search result: ${availableGames.size} available games, $skippedCount skipped")
                    
                    // Select a random game from available ones
                    if (availableGames.isNotEmpty()) {
                        val randomGame = availableGames.random()
                        Log.d(TAG, "Selected random game: $randomGame")
                        callback(randomGame)
                    } else {
                        Log.d(TAG, "No available games found")
                        callback(null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error finding random game", e)
                    callback(null)
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "findRandomAvailableGame() cancelled: ${error.message}", error.toException())
                callback(null)
            }
        })
    }
} 
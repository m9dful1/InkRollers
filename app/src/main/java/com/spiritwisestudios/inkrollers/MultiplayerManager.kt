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

// Data class to hold game settings read by clients
data class GameSettings(val durationMs: Long, val complexity: String, val gameMode: String)

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
        internal const val GAMES_NODE = "games"
        internal const val GAMES_LIST_NODE = "gamesList"
        private const val PAINT_NODE = "paint"
        private const val REMATCH_NODE = "rematchRequests"
        internal const val LAST_ACTIVITY_NODE = "lastActivityAt"
        internal const val CREATED_AT_NODE = "createdAt"
        private const val STALE_GAME_TTL_MS = 3 * 60 * 60 * 1000L // 3 hours
        private const val INACTIVE_GRACE_PERIOD_MS = 10 * 60 * 1000L // 10 minutes
        private const val MAX_GAMES_TO_SCAN_FOR_CLEANUP = 10
    }

    // Callback for database error notifications
    var onDatabaseError: ((String) -> Unit)? = null

    // Number of players in this game (used for rematch decision)
    private var expectedRematchCount: Long = 0L

    // New callbacks for pre-match flow: player count and match start requests
    var onPlayerCountChanged: ((Int) -> Unit)? = null
    var onMatchStartRequested: (() -> Unit)? = null

    private var playerCountListener: ChildEventListener? = null
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
        
        // Log current authentication state
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "User is authenticated with UID: ${currentUser.uid}")
            Log.d(TAG, "User is anonymous: ${currentUser.isAnonymous}")
            Log.d(TAG, "User provider data: ${currentUser.providerData}")
        } else {
            Log.w(TAG, "User is NOT authenticated")
        }
        
        val connectedRef = database.getReference(".info/connected")
        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                     Log.d(TAG, "Firebase connection established")
                     
                     // Test a simple read operation to check permissions
                     testFirebasePermissions()
                } else {
                    Log.w(TAG, "Firebase NOT connected - waiting for connection...")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase connection check cancelled", error.toException())
                Log.e(TAG, "DatabaseError code: ${error.code}, message: ${error.message}")
                Log.e(TAG, "DatabaseError details: ${error.details}")
                onDatabaseError?.invoke("Connection check error: ${error.message} (Code: ${error.code})")
            }
        })
    }
    
    /** Tests Firebase read/write permissions with detailed error logging. */
    private fun testFirebasePermissions() {
        Log.d(TAG, "Testing Firebase permissions...")
        
        val testRef = database.getReference("games").limitToLast(1)
        testRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "Firebase read permission test PASSED")
                Log.d(TAG, "Test read returned ${snapshot.childrenCount} items")
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase read permission test FAILED")
                Log.e(TAG, "Permission error code: ${error.code}")
                Log.e(TAG, "Permission error message: ${error.message}")
                Log.e(TAG, "Permission error details: ${error.details}")
                
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    Log.e(TAG, "User WAS authenticated during permission test - UID: ${currentUser.uid}")
                } else {
                    Log.e(TAG, "User was NOT authenticated during permission test!")
                }
                
                onDatabaseError?.invoke("Permission test failed: ${error.message} (Code: ${error.code}). Check authentication and Firebase rules.")
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

    fun hostGame(initialPlayerState: PlayerState, durationMs: Long, complexity: String, gameMode: String, isPrivate: Boolean, callback: (success: Boolean, gameId: String?, gameSettings: GameSettings?) -> Unit) {
        clearListeners() // Clear any previous listeners
        
        // Ensure user is authenticated before proceeding
        if (auth.currentUser == null) {
            Log.e(TAG, "Cannot host game: User not authenticated")
            onDatabaseError?.invoke("Authentication required to host game")
            callback(false, null, null)
            return
        }
        Log.d(TAG, "User authenticated with UID: ${auth.currentUser?.uid}, proceeding with hostGame")
        
        // Host should perform cleanup
        performStaleGameCleanup()

        currentGameId = generateGameId()
        gameRef = database.getReference(GAMES_NODE).child(currentGameId!!)
        playersRef = gameRef?.child("players")
        paintRef = gameRef?.child(PAINT_NODE)

        Log.d(TAG, "Hosting game with ID: $currentGameId")

        // Set host as player0
        localPlayerId = "player0"
        
        // Generate a maze seed for this game
        mazeSeed = System.currentTimeMillis()
        Log.d(TAG, "Generated maze seed: $mazeSeed")
        
        createGameAfterConnectionTest(initialPlayerState, durationMs, complexity, gameMode, isPrivate, callback)
    }
    
    private fun createGameAfterConnectionTest(initialPlayerState: PlayerState, durationMs: Long, complexity: String, gameMode: String, isPrivate: Boolean, callback: (success: Boolean, gameId: String?, gameSettings: GameSettings?) -> Unit) {
        try {
            // Use a map to set the initial game structure with player0
            val initialGameData = mapOf(
                "players" to mapOf(
                    localPlayerId!! to initialPlayerState
                ),
                "mazeSeed" to mazeSeed, // Store the seed at game level too
                "matchDurationMs" to durationMs, // Store match duration
                "mazeComplexity" to complexity,   // Store maze complexity
                "gameMode" to gameMode,
                "isPrivate" to isPrivate, // Store private match status
                CREATED_AT_NODE to ServerValue.TIMESTAMP, // Use constant
                LAST_ACTIVITY_NODE to ServerValue.TIMESTAMP, // Add last activity
                "started" to false, // Explicitly set initial started state
                "playerCount" to 1L // Set initial player count
            )

            Log.d(TAG, "Attempting to write game data to Firebase: $currentGameId")
            Log.d(TAG, "Complete game data structure: $initialGameData")
            
            // Create game settings for the host too for consistency
            gameSettings = GameSettings(durationMs, complexity, gameMode)
            
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
                                // Add to public gamesList for discovery
                                addToGamesList(currentGameId!!, isPrivate, 1L, false)
                                updateLastActivityTimestamp()
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

    fun joinGame(initialPlayerState: PlayerState, gameId: String?, callback: (success: Boolean, gameId: String?, gameSettings: GameSettings?) -> Unit) {
        clearListeners() // Clear any previous listeners
        
        // Ensure user is authenticated before proceeding
        if (auth.currentUser == null) {
            Log.e(TAG, "Cannot join game: User not authenticated")
            onDatabaseError?.invoke("Authentication required to join game")
            callback(false, null, null)
            return
        }
        Log.d(TAG, "User authenticated with UID: ${auth.currentUser?.uid}, proceeding with joinGame")
        
        if (gameId.isNullOrBlank()) {
            findRandomAvailableGame(initialPlayerState, callback)
        } else {
            // Attempt to join a specific game by ID
            Log.d(TAG, "joinGame(): requested specific gameId=$gameId")
            attemptToJoinGame(gameId, initialPlayerState, null, callback)
        }
    }
    
    // Find an available public game
    private fun findRandomAvailableGame(initialPlayerState: PlayerState, callback: (success: Boolean, gameId: String?, gameSettings: GameSettings?) -> Unit) {
        Log.d(TAG, "Searching for available games in gamesList...")

        val gamesListRef = database.getReference(GAMES_LIST_NODE)
        // Query for recent, non-private, not-started games with 1 player
        gamesListRef.orderByChild(CREATED_AT_NODE)
            .limitToLast(20) // Look at the last 20 created games
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val availableGames = mutableListOf<String>()
                    var skippedCount = 0
                    
                    if (snapshot.exists()) {
                        Log.d(TAG, "Found ${snapshot.childrenCount} recent games to check")
                        
                        // Iterate in reverse to get newest games first
                        val children = snapshot.children.toList().reversed()
                        
                        for (gameSnapshot in children) {
                            val isPrivate = gameSnapshot.child("isPrivate").getValue(Boolean::class.java) ?: false
                            val playerCount = gameSnapshot.child("playerCount").getValue(Long::class.java) ?: 0L
                            val started = gameSnapshot.child("started").getValue(Boolean::class.java) ?: false
                            
                            Log.v(TAG, "Examining gamesList entry: ${gameSnapshot.key} -> ${gameSnapshot.value}")
                            
                            if (!isPrivate && playerCount == 1L && !started) {
                                gameSnapshot.key?.let {
                                    Log.d(TAG, "Found available public game: $it with $playerCount players")
                                    availableGames.add(it)
                                }
                            } else {
                                skippedCount++
                            }
                        }
                    } else {
                        Log.d(TAG, "No recent games found in gamesList.")
                    }
                    
                    Log.d(TAG, "Search result: ${availableGames.size} potentially available public games, $skippedCount skipped")
                    
                    if (availableGames.isNotEmpty()) {
                        // The list is already sorted by newest first, so no need to shuffle.
                        Log.d(TAG, "Attempting to join one of ${availableGames.size} games...")
                        attemptToJoinGame(availableGames.first(), initialPlayerState, availableGames, callback)
                    } else {
                        Log.d(TAG, "No available games found after checking.")
                        onDatabaseError?.invoke("No available games to join.")
                        callback(false, null, null)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error finding random game", error.toException())
                    onDatabaseError?.invoke("Error finding game: ${error.message}")
                    callback(false, null, null)
                }
            })
    }

    // New recursive function to attempt joining a game
    private fun attemptToJoinGame(gameId: String, initialPlayerState: PlayerState, availableGames: List<String>?, callback: (success: Boolean, gameId: String?, gameSettings: GameSettings?) -> Unit) {
        Log.d(TAG, "Attempting to join game: $gameId")
        val ref = database.getReference("$GAMES_NODE/$gameId")
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val isStarted = snapshot.child("started").getValue(Boolean::class.java) ?: false
                    val playersNode = snapshot.child("players")
                    val playerCount = playersNode.childrenCount
                    
                    if (isStarted) {
                        Log.w(TAG, "Game $gameId has already started and cannot accept new players.")
                        handleJoinFailure(initialPlayerState, availableGames, callback, "Game has already started")
                        return
                    }

                    if (playerCount >= 2) {
                        Log.w(TAG, "Game $gameId is full ($playerCount players) and cannot be joined.")
                        handleJoinFailure(initialPlayerState, availableGames, callback, "Game is full")
                        return
                    }
                    
                    // Successfully found a game to join, proceed
                    Log.i(TAG, "Successfully joining game $gameId")
                    currentGameId = gameId
                    gameRef = ref
                    playersRef = gameRef?.child("players")
                    paintRef = gameRef?.child(PAINT_NODE)
                    localPlayerId = "player1" // Joiner is always player1

                    // Read game settings for the client
                    val duration = snapshot.child("matchDurationMs").getValue(Long::class.java) ?: 60000L
                    val complexity = snapshot.child("mazeComplexity").getValue(String::class.java) ?: HomeActivity.COMPLEXITY_MEDIUM
                    val mode = snapshot.child("gameMode").getValue(String::class.java) ?: HomeActivity.GAME_MODE_COVERAGE
                    gameSettings = GameSettings(duration, complexity, mode)
                    
                    mazeSeed = snapshot.child("mazeSeed").getValue(Long::class.java) ?: System.currentTimeMillis()

                    // Add player1 to the game
                    playersRef?.child(localPlayerId!!)?.setValue(initialPlayerState)?.addOnSuccessListener {
                        Log.d(TAG, "Successfully added player1 to game $gameId")

                        // Update player count atomically
                        gameRef?.child("playerCount")?.setValue(ServerValue.increment(1))
                        updateLastActivityTimestamp()
                        
                        setupFirebaseListeners()
                        callback(true, currentGameId, gameSettings)
                    }?.addOnFailureListener { e ->
                        Log.e(TAG, "Failed to add player1 to game $gameId", e)
                        handleJoinFailure(initialPlayerState, availableGames, callback, "Database write failed: ${e.message}")
                    }
                } else {
                    Log.w(TAG, "Game with ID $gameId does not exist.")
                    handleJoinFailure(initialPlayerState, availableGames, callback, "Game does not exist")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error joining game $gameId", error.toException())
                handleJoinFailure(initialPlayerState, availableGames, callback, "Database error: ${error.message}")
            }
        })
    }
    
    private fun handleJoinFailure(
        initialPlayerState: PlayerState,
        availableGames: List<String>?,
        callback: (success: Boolean, gameId: String?, gameSettings: GameSettings?) -> Unit,
        reason: String
    ) {
        val remainingGames = availableGames?.drop(1)
        if (remainingGames != null && remainingGames.isNotEmpty()) {
            Log.d(TAG, "Join failed for reason: '$reason'. Trying next available game. ${remainingGames.size} remaining.")
            // Try the next game in the list
            attemptToJoinGame(remainingGames.first(), initialPlayerState, remainingGames, callback)
        } else {
            // No more games to try, or it was a specific game join that failed
            Log.e(TAG, "Failed to join game. Reason: $reason. No other games to try.")
            if (availableGames != null) { // This means it was a random join attempt
                onDatabaseError?.invoke("Failed to join any random game.")
            } else { // This was a specific join attempt
                onDatabaseError?.invoke("Failed to join game: $reason")
            }
            callback(false, null, null)
        }
    }

    /**
     * Adds a game to the public gamesList for discovery
     */
    private fun addToGamesList(gameId: String, isPrivate: Boolean, playerCount: Long, started: Boolean) {
        val gamesListRef = database.getReference(GAMES_LIST_NODE).child(gameId)
        val gamesListData = mapOf(
            CREATED_AT_NODE to ServerValue.TIMESTAMP,
            "isPrivate" to isPrivate,
            "playerCount" to playerCount,
            "started" to started
        )
        
        gamesListRef.setValue(gamesListData).addOnFailureListener { e ->
            Log.w(TAG, "Failed to add game $gameId to gamesList", e)
        }
    }

    /**
     * Updates the player count in gamesList when players join/leave
     */
    private fun updateGamesListPlayerCount(gameId: String, playerCount: Long) {
        val gamesListRef = database.getReference(GAMES_LIST_NODE).child(gameId)
        gamesListRef.child("playerCount").setValue(playerCount).addOnFailureListener { e ->
            Log.w(TAG, "Failed to update player count in gamesList for $gameId", e)
        }
    }

    /**
     * Updates the started status in gamesList when a game starts
     */
    private fun updateGamesListStarted(gameId: String, started: Boolean) {
        val gamesListRef = database.getReference(GAMES_LIST_NODE).child(gameId)
        gamesListRef.child("started").setValue(started).addOnFailureListener { e ->
            Log.w(TAG, "Failed to update started status in gamesList for $gameId", e)
        }
    }

    /**
     * Removes a game from gamesList when it's no longer discoverable
     */
    private fun removeFromGamesList(gameId: String) {
        val gamesListRef = database.getReference(GAMES_LIST_NODE).child(gameId)
        gamesListRef.removeValue().addOnFailureListener { e ->
            Log.w(TAG, "Failed to remove game $gameId from gamesList", e)
        }
    }

    fun updateLocalPlayerState(newState: PlayerState) {
        if (localPlayerId == null || playersRef == null) return
        // Push newState to Firebase under playersRef.child(localPlayerId!!)
        playersRef?.child(localPlayerId!!)?.setValue(newState)?.addOnFailureListener { e ->
            Log.w(TAG, "Failed to update player state for $localPlayerId", e)
            onDatabaseError?.invoke("Update failed: ${e.message}")
        }
        updateLastActivityTimestamp() // Player state updated
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
        updateLastActivityTimestamp() // Player partial state updated
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
        playerCountListener = object : ChildEventListener {
            private fun updateCount(snapshot: DataSnapshot) {
                Log.d(TAG, "Player count changed to ${snapshot.childrenCount} for game $currentGameId")
                onPlayerCountChanged?.invoke(snapshot.childrenCount.toInt())
            }
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                // To get the total count, we need to query the parent
                snapshot.ref.parent?.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(parentSnapshot: DataSnapshot) { updateCount(parentSnapshot) }
                    override fun onCancelled(error: DatabaseError) { Log.w(TAG, "Could not get parent for count update on child_added", error.toException()) }
                })
            }
            override fun onChildRemoved(snapshot: DataSnapshot) {
                // To get the total count, we need to query the parent
                snapshot.ref.parent?.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(parentSnapshot: DataSnapshot) { updateCount(parentSnapshot) }
                    override fun onCancelled(error: DatabaseError) { Log.w(TAG, "Could not get parent for count update on child_removed", error.toException()) }
                })
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) { /* No-op, we don't care about state changes for the count */ }
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) { /* No-op */ }
            override fun onCancelled(error: DatabaseError) {
                 Log.w(TAG, "Player count listener cancelled for $currentGameId", error.toException())
            }
        }
        playersRef!!.addChildEventListener(playerCountListener!!)
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
                        // Write a synchronized startTime (4 seconds in the future to align with countdown end)
                        val startTime = System.currentTimeMillis() + 4000L
                        val updates = mapOf(
                            "started" to true,
                            "startTime" to startTime,
                            LAST_ACTIVITY_NODE to ServerValue.TIMESTAMP // Host started match
                        )
                        gameRef!!.updateChildren(updates)
                            .addOnSuccessListener {
                                // Update gamesList to mark game as started
                                currentGameId?.let { gameId ->
                                    updateGamesListStarted(gameId, true)
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.w(TAG, "Failed to send match start for $currentGameId", e)
                                onDatabaseError?.invoke("Failed to send start signal: "+e.message)
                            }
                    }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "sendMatchStart: Failed to get player count", error.toException())
                // Fallback: still try to start
                val startTime = System.currentTimeMillis() + 4000L // Align with countdown end
                val updates = mapOf(
                    "started" to true,
                    "startTime" to startTime,
                    LAST_ACTIVITY_NODE to ServerValue.TIMESTAMP // Host started match (fallback)
                )
                gameRef!!.updateChildren(updates)
                    .addOnSuccessListener {
                        // Update gamesList to mark game as started (fallback case)
                        currentGameId?.let { gameId ->
                            updateGamesListStarted(gameId, true)
                        }
                    }
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
                ?.addOnSuccessListener { updateLastActivityTimestamp() } // Paint action sent
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
           ?.addOnSuccessListener { updateLastActivityTimestamp() } // Rematch answer sent
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

    private fun updateLastActivityTimestamp() {
        gameRef?.child(LAST_ACTIVITY_NODE)?.setValue(ServerValue.TIMESTAMP)
            ?.addOnFailureListener { e ->
                Log.w(TAG, "Failed to update lastActivityAt for game $currentGameId", e)
            }
    }

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

                    // 1. Check for staleness by TTL
                    if (currentTime - lastActivityAt > STALE_GAME_TTL_MS) {
                        shouldDelete = true
                        reason = "Stale by TTL (lastActivityAt: ${Date(lastActivityAt)})"
                    }

                    // 2. Check for all players inactive (if not already marked for deletion and game is old enough)
                    if (!shouldDelete && (currentTime - createdAt > INACTIVE_GRACE_PERIOD_MS)) {
                        val playersNode = gameSnapshot.child("players")
                        if (playersNode.exists() && playersNode.hasChildren()) {
                            var activePlayerFoundInGame = false
                            for (playerSnap in playersNode.children) {
                                val isActive = playerSnap.child("active").getValue(Boolean::class.java) ?: false
                                if (isActive) {
                                    activePlayerFoundInGame = true
                                    break
                                }
                            }
                            if (!activePlayerFoundInGame) {
                                shouldDelete = true
                                reason = "All players inactive (createdAt: ${Date(createdAt)})"
                            }
                        } else {
                            // No players node or no players, and game is past grace period
                            shouldDelete = true
                            reason = "No players and past grace period (createdAt: ${Date(createdAt)})"
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
} 

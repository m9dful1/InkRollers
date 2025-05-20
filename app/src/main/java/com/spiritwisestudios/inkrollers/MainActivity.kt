package com.spiritwisestudios.inkrollers
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import com.spiritwisestudios.inkrollers.TimerHudView
import com.spiritwisestudios.inkrollers.GameModeManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.database.FirebaseDatabase
import com.spiritwisestudios.inkrollers.repository.ProfileRepository
import com.spiritwisestudios.inkrollers.model.PlayerProfile
import kotlin.random.Random

class MainActivity:AppCompatActivity(){
  private lateinit var gameView:GameView
  private lateinit var inkHudView: InkHudView
  private lateinit var coverageHudView: CoverageHudView
  private lateinit var timerHudView: TimerHudView
  private var matchDurationMs: Long = 180000L // 3 minute match
  private var mazeComplexity: String = HomeActivity.COMPLEXITY_HIGH // Default High
  
  private lateinit var multiplayerManager: MultiplayerManager
  private var localPlayerId: String? = null
  
  // New fields for dialogs
  private var waitingDialog: AlertDialog? = null
  private var countdownDialog: AlertDialog? = null
  
  // Add Firebase Auth field
  private lateinit var auth: FirebaseAuth

  // Add this field to the MainActivity class
  private var rematchInProgressHandled = false

  private var matchStartTime: Long? = null // Add this field to store the synchronized start time

  companion object {
      private const val TAG = "MainActivity"
      private val NEON_GREEN = Color.parseColor("#39FF14")
      private val NEON_BLUE = Color.parseColor("#1F51FF")
  }

  override fun onCreate(savedInstanceState: Bundle?){
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    // Initialize Firebase Auth
    auth = Firebase.auth

    gameView=findViewById(R.id.game_view)
    inkHudView = findViewById(R.id.ink_hud_view)
    coverageHudView = findViewById(R.id.coverage_hud_view)
    timerHudView = findViewById(R.id.timer_hud_view)

    multiplayerManager = MultiplayerManager()
    
    // Listen for database permission/connectivity issues
    multiplayerManager.onDatabaseError = { errorMessage ->
        runOnUiThread {
            Toast.makeText(this, "Firebase error: $errorMessage", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Firebase database error: $errorMessage")
        }
    }
    
    // Listen for rematch decision
    multiplayerManager.onRematchDecision = { bothYes ->
      Handler(Looper.getMainLooper()).post {
        if (!bothYes) {
          // Show dialog that the other player declined rematch, then finish
          runOnUiThread {
            if (isFinishing || isDestroyed) {
              finish()
              return@runOnUiThread
            }
            try { waitingDialog?.dismiss() } catch (_: Exception) {}
            waitingDialog = null
            try { countdownDialog?.dismiss() } catch (_: Exception) {}
            countdownDialog = null
            AlertDialog.Builder(this)
              .setTitle("Rematch Declined")
              .setMessage("The other player chose not to rematch. Returning to home screen.")
              .setCancelable(false)
              .setPositiveButton("OK") { _, _ -> finish() }
              .show()
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
          restartMatchForRematch()
        } else {
          Log.d(TAG, "Rematch already in progress, ignoring duplicate signal.")
        }
      }
    }

    // Removed: handleIntentExtras() here to prevent duplicate game creation

    val toggleButton = findViewById<Button>(R.id.btn_toggle)
    // Initialize button for Paint mode (shows action to switch to Fill)
    toggleButton.setTextColor(Color.WHITE)
    toggleButton.setBackgroundColor(Color.parseColor("#2196F3")) // Blue for Paint
    toggleButton.text = "FILL"
    toggleButton.setOnClickListener {
        gameView.getLocalPlayer()?.let { player ->
            player.toggleMode()
            val isPaintMode = player.mode == 0
            // Button text shows the next action
            toggleButton.text = if (isPaintMode) "FILL" else "PAINT"
            // Warm orange for Paint mode, cool blue for Fill mode
            val bgColor = if (isPaintMode) Color.parseColor("#2196F3") else Color.parseColor("#FF9800")
            toggleButton.setBackgroundColor(bgColor)
            toggleButton.setTextColor(Color.WHITE)
        }
    }

    findViewById<Button>(R.id.btn_toggle_p2).visibility = android.view.View.GONE

    gameView.setHudView(inkHudView)
    gameView.setCoverageHudView(coverageHudView)
    gameView.setTimerHudView(timerHudView)
    gameView.setMultiplayerManager(multiplayerManager)
    // Handle match end: show rematch dialog
    gameView.onMatchEnd = { didWin ->
      Handler(Looper.getMainLooper()).post { showRematchDialog(didWin) }
    }

    // Sign in anonymously, then proceed with game setup
    signInAnonymouslyAndProceed()
  }

  private fun signInAnonymouslyAndProceed() {
      Log.d(TAG, "Attempting anonymous sign-in...")
      auth.signInAnonymously()
          .addOnCompleteListener(this) { task ->
              if (task.isSuccessful) {
                  // Sign in success, proceed with game flow
                  Log.d(TAG, "Anonymous sign-in successful")
                  val user = auth.currentUser
                  Log.d(TAG, "Authenticated with UID: ${user?.uid}")
                  handleIntentExtras() // Now proceed with hosting/joining
              } else {
                  // If sign in fails, display a message to the user.
                  Log.w(TAG, "Anonymous sign-in failed", task.exception)
                  Toast.makeText(baseContext, "Authentication failed: ${task.exception?.message}",
                      Toast.LENGTH_SHORT).show()
                  finish() // Can't play without auth
              }
          }
  }

  private fun handleIntentExtras() {
      val mode = intent.getStringExtra(HomeActivity.EXTRA_MODE)
      Log.d(TAG, "Received mode: $mode")

      if (mode == HomeActivity.MODE_HOST) {
          matchDurationMs = intent.getIntExtra(HomeActivity.EXTRA_TIME_LIMIT_MINUTES, 3) * 60000L
          mazeComplexity = intent.getStringExtra(HomeActivity.EXTRA_MAZE_COMPLEXITY) ?: HomeActivity.COMPLEXITY_HIGH
          Log.d(TAG, "Host selected settings: Duration=${matchDurationMs}ms, Complexity=$mazeComplexity")
      }
      // For JOIN mode, duration and complexity will be fetched from Firebase by MultiplayerManager

      // Load player profile to get favorite color and name before starting game flow
      val uid = Firebase.auth.currentUser?.uid
      if (uid != null) {
          ProfileRepository.loadPlayerProfile(uid) { profile: PlayerProfile? ->
              val playerColor = if (profile?.favoriteColors?.isNotEmpty() == true) {
                  // Use the player's first favorite color if available
                  profile.favoriteColors[0]
              } else {
                  // Fallback to default colors based on mode if no profile or no favorite colors
                  if (mode == HomeActivity.MODE_HOST) NEON_GREEN else NEON_BLUE
              }
              
              val playerName = profile?.playerName ?: "Player ${if (mode == HomeActivity.MODE_HOST) 1 else 2}" // Use profile name or default

              // Create initial state with player name and UID
              val initialState = PlayerState(
                  color = playerColor,
                  playerName = playerName,
                  uid = uid // Include UID in the initial state
              )

      when (mode) {
          HomeActivity.MODE_HOST -> {
              multiplayerManager.hostGame(initialState, matchDurationMs, mazeComplexity) { success, gameId, gameSettings ->
                  if (success && gameId != null) {
                      this.localPlayerId = multiplayerManager.localPlayerId
                      Log.d(TAG, "Host game successful. Game ID: $gameId. Settings: Duration=${gameSettings?.durationMs}, Complexity=${gameSettings?.complexity}")
                              
                              // Set local player ID with the determined color and name
                              gameView.setLocalPlayerId("player0", playerColor, playerName)
                      
                      // Set up player count listener *immediately* after confirmation
                      multiplayerManager.onPlayerCountChanged = { count ->
                                  Log.d(TAG, "Host: onPlayerCountChanged received count: $count")
                          if (count >= 2) {
                              // Only trigger once
                              Log.d(TAG, "Host: Player count >= 2, triggering countdown.")
                              // Now that all players are present, attach the rematch listener so expected count is accurate
                              multiplayerManager.setupRematchListener()
                              multiplayerManager.onPlayerCountChanged = null // Nullify listener *before* UI action
                              runOnUiThread {
                                          if (!isFinishing && !isDestroyed) {
                                     waitingDialog?.dismiss()
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

                      // Show waiting dialog and toast *after* setting up listener
                      runOnUiThread {
                                  if (!isFinishing && !isDestroyed) {
                             showWaitingForPlayersDialog()
                             Toast.makeText(this, "Hosting Game: $gameId", Toast.LENGTH_LONG).show()
                          } 
                      }
                      
                      Log.i(TAG, "Hosting setup complete. Game ID: $gameId, Player ID: ${this.localPlayerId}")
                  } else {
                      Log.e(TAG, "Failed to host game.")
                      runOnUiThread {
                                   if (!isFinishing && !isDestroyed) {
                              Toast.makeText(this, "Failed to host game", Toast.LENGTH_SHORT).show()
                           } 
                      }
                      finish()
                  }
              }
          }
          HomeActivity.MODE_JOIN -> {
              val gameId = intent.getStringExtra(HomeActivity.EXTRA_GAME_ID)
              if (gameId != null) {
                  multiplayerManager.joinGame(gameId, initialState) { success, playerId, gameSettings ->
                       if (success && playerId != null) {
                           this.localPlayerId = playerId
                                   // Set local player ID with the determined color and name
                                   gameView.setLocalPlayerId(playerId, playerColor, playerName)

                           // Apply game settings received from Firebase
                           gameSettings?.let {
                               matchDurationMs = it.durationMs
                               mazeComplexity = it.complexity
                               Log.d(TAG, "Joined game with settings: Duration=${matchDurationMs}ms, Complexity=$mazeComplexity")
                           }
                           // Show waiting dialog until host starts
                           runOnUiThread {
                               showWaitingForHostDialog()
                               Toast.makeText(this, "Joined Game: $gameId as $playerId", Toast.LENGTH_LONG).show()
                           }
                           multiplayerManager.onMatchStartRequested = {
                               // Only trigger once
                               multiplayerManager.onMatchStartRequested = null
                               runOnUiThread {
                                   waitingDialog?.dismiss()
                                   startPreMatchCountdown(isHost = false)
                               }
                           }
                           Log.i(TAG, "Joining successful. Game ID: $gameId, Player ID: ${this.localPlayerId}")
                       } else {
                            Log.e(TAG, "Failed to join game $gameId.")
                            runOnUiThread {
                                Toast.makeText(this, "Failed to join game $gameId", Toast.LENGTH_SHORT).show()
                            }
                            finish()
                       }
                  }
              } else {
                   // Attempt to join a random game instead
                   runOnUiThread {
                       Toast.makeText(this, "Searching for an available game...", Toast.LENGTH_SHORT).show()
                   }
                   multiplayerManager.joinGame(null, initialState) { success, playerId, gameSettings ->
                       if (success && playerId != null) {
                           this.localPlayerId = playerId
                                   // Set local player ID with the determined color and name
                                   gameView.setLocalPlayerId(playerId, playerColor, playerName)

                           // Apply game settings received from Firebase
                           gameSettings?.let {
                               matchDurationMs = it.durationMs
                               mazeComplexity = it.complexity
                               Log.d(TAG, "Joined random game with settings: Duration=${matchDurationMs}ms, Complexity=$mazeComplexity")
                           }
                           // Show the game ID that was joined
                           val joinedGameId = multiplayerManager.currentGameId
                           runOnUiThread {
                               Toast.makeText(this, "Joined Random Game: $joinedGameId as $playerId", Toast.LENGTH_LONG).show()
                               // Show waiting dialog until host starts
                               showWaitingForHostDialog()
                           }
                           multiplayerManager.onMatchStartRequested = {
                               // Only trigger once
                               multiplayerManager.onMatchStartRequested = null
                               runOnUiThread {
                                   waitingDialog?.dismiss()
                                   startPreMatchCountdown(isHost = false)
                               }
                           }
                           Log.i(TAG, "Random joining successful. Game ID: $joinedGameId, Player ID: ${this.localPlayerId}")
                       } else {
                           Log.e(TAG, "Failed to join any random game.")
                           runOnUiThread {
                               Toast.makeText(this, "No available games found. Try hosting a game instead.", Toast.LENGTH_SHORT).show()
                           }
                           finish()
                       }
                   }
              }
          }
          else -> {
               Log.e(TAG, "Invalid or missing mode specified.")
               Toast.makeText(this, "Error: Invalid mode", Toast.LENGTH_SHORT).show()
               finish()
          }
              }
          }
      } else {
          Log.e(TAG, "User not authenticated during game initiation.")
          Toast.makeText(this, "Error: User not authenticated", Toast.LENGTH_SHORT).show()
          finish()
      }
  }
  
  override fun onDestroy() {
      // Dismiss dialogs to avoid leaks/crashes
      runOnUiThread {
        try { waitingDialog?.dismiss() } catch (_: Exception) {}
        waitingDialog = null
        try { countdownDialog?.dismiss() } catch (_: Exception) {}
        countdownDialog = null
      }
      super.onDestroy()
      Log.d(TAG, "onDestroy called, leaving game...")
      multiplayerManager.leaveGame()
  }

  override fun onPause(){ super.onPause(); gameView.pause() }
  override fun onResume(){ super.onResume(); gameView.resume() }

  /** Display rematch dialog and send answer */
  private fun showRematchDialog(didWin: Boolean) {
    runOnUiThread {
      if (isFinishing || isDestroyed) {
        Log.w(TAG, "showRematchDialog: Activity finishing or destroyed, not showing dialog.")
        return@runOnUiThread
      }
      try { waitingDialog?.dismiss() } catch (_: Exception) {}
      waitingDialog = null
      try { countdownDialog?.dismiss() } catch (_: Exception) {}
      countdownDialog = null
      Log.d(TAG, "showRematchDialog called. didWin=$didWin")
      val message = if (didWin) "You Won!" else "You Lost"
      AlertDialog.Builder(this)
        .setTitle(message)
        .setMessage("Play Again?")
        .setPositiveButton("Yes") { _, _ -> 
          Log.d(TAG, "Rematch dialog: YES selected. Sending rematch answer true.")
          multiplayerManager.sendRematchAnswer(true) 
        }
        .setNegativeButton("No") { _, _ -> 
          Log.d(TAG, "Rematch dialog: NO selected. Sending rematch answer false.")
          multiplayerManager.sendRematchAnswer(false) 
        }
        .setCancelable(false)
        .show()
    }
  }

  /** Reset state for rematch, then show countdown and start match */
  private fun restartMatchForRematch() {
    restartMatch(resetOnly = true)
    showRematchCountdownAndStart()
  }

  /** Show countdown and start the rematch after reset */
  private fun showRematchCountdownAndStart() {
    startPreMatchCountdown(isHost = (localPlayerId == "player0")) {
        // After the countdown, actually start the rematch!
        Log.d(TAG, "showRematchCountdownAndStart: Countdown finished, starting rematch match.")
        actuallyStartMatch()
    }
  }

  // Update restartMatch to optionally only reset state (no countdown/start)
  private fun restartMatch(resetOnly: Boolean = false) {
    Log.d(TAG, "restartMatch called. Beginning rematch reset flow.")
    // 1. Stop the old game thread and wait for it
    gameView.stopThread()
    // 2. Clear non-player Firebase state
    Log.d(TAG, "Clearing Firebase paint/rematch state...")
    multiplayerManager.clearPaintActions()
    multiplayerManager.clearRematchAnswers()
    
    // Load player profile to get favorite color
    val uid = Firebase.auth.currentUser?.uid
    if (uid != null) {
        // Load local player profile first to ensure we have their preferences
        ProfileRepository.loadPlayerProfile(uid) { localProfile: PlayerProfile? ->
    // --- 3. Calculate and Reset Player States in Firebase --- 
    Log.d(TAG, "Calculating and resetting Firebase player states...")
    val currentLevel = gameView.getCurrentLevel() // Get level for coordinate calc
    val playerIds = gameView.getActivePlayerIds() // Get IDs before local state is cleared
    val initialStates = mutableMapOf<String, PlayerState>()
            
            // Map to store each player's profile
            val playerProfiles = mutableMapOf<String, PlayerProfile?>()
            // List to track UIDs we need to load profiles for
            val uidsToLoad = mutableSetOf<String>()

            // Before proceeding, we need to fetch the PlayerState from Firebase for all active players
            multiplayerManager.getPlayersState { playerStatesMap ->
                Log.d(TAG, "restartMatch: Fetched all player states from Firebase: ${playerStatesMap.keys}")
                // Collect UIDs from fetched player states
                playerStatesMap.forEach { (playerId, playerState) ->
                    if (playerState != null && playerState.uid.isNotEmpty()) {
                        uidsToLoad.add(playerState.uid)
                        // Associate player ID with UID temporarily for profile loading context
                        // (This isn't strictly needed if ProfileRepository loads by UID)
                    } else {
                         Log.w(TAG, "restartMatch: Player state for $playerId is null or has empty UID.")
                    }
                }

                if (uidsToLoad.isEmpty()) {
                    Log.w(TAG, "No valid player UIDs found from Firebase states for rematch setup.")
                    // Fallback to default colors/names if no profiles can be loaded
                    assignDefaultColorsAndNames(playerIds, initialStates, currentLevel, multiplayerManager.mazeSeed)
                    gameView.clearPaintSurface()
                    gameView.initGame(mazeComplexity)
                    val localPlayerId = multiplayerManager.localPlayerId
                    if (localPlayerId != null) { // Pass name as empty, will be updated by state
                       gameView.setLocalPlayerId(localPlayerId, initialStates[localPlayerId]?.color, initialStates[localPlayerId]?.playerName ?: "")
                    }
                    // Game start logic is triggered by countdown flow
                    return@getPlayersState // Exit this lambda
                }

                // Load all player profiles concurrently
                var loadedProfileCount = 0
                uidsToLoad.forEach { uidToLoad ->
                    ProfileRepository.loadPlayerProfile(uidToLoad) { profile ->
                        // Find the player ID(s) associated with this UID
                        val playerIdsForUid = playerStatesMap.filter { it.value?.uid == uidToLoad }.keys
                        playerIdsForUid.forEach { playerId ->
                             playerProfiles[playerId] = profile
                        }
                       
                        loadedProfileCount++
                        if (loadedProfileCount == uidsToLoad.size) {
                            // All profiles loaded, now assign colors and names
                            Log.d(TAG, "All profiles loaded for rematch. Assigning colors and names.")
                            assignColorsAndNamesForRematch(playerIds, playerProfiles, initialStates, currentLevel, multiplayerManager.mazeSeed)
                           
                            // 4. Clear the paint surface
                            gameView.clearPaintSurface()
                            
                            // 5. Reinitialize the game
                            gameView.initGame(mazeComplexity)
                            
                            // 6. Set local player ID with their determined color and name
                            val localPlayerId = multiplayerManager.localPlayerId
                            if (localPlayerId != null) {
                                // Pass the determined color and name from initialStates
                                gameView.setLocalPlayerId(localPlayerId, initialStates[localPlayerId]?.color, initialStates[localPlayerId]?.playerName ?: "")
                            }
                            // The game start logic (startGameMode and startGameLoop) is now triggered
                            // by the startPreMatchCountdown/actuallyStartMatch flow as before.
                            if (!resetOnly) { // If not just resetting, start the countdown/match flow
                                // No need to explicitly call showRematchCountdownAndStart here
                                // The flow is initiated from showRematchDialog -> restartMatchForRematch -> restartMatch(resetOnly = false)
                                // The game start will be triggered by the countdown callback
                            }
                        }
                    }
                }
            }
        } // End of local profile load
    } else {
        Log.e(TAG, "Cannot restart match: User not authenticated")
        Toast.makeText(this, "Error: User not authenticated", Toast.LENGTH_SHORT).show()
        // Consider finishing the activity or showing an error to the user
    }
  }

  // Helper function to assign default colors and names if profiles can't be loaded
  private fun assignDefaultColorsAndNames(playerIds: Set<String>, initialStates: MutableMap<String, PlayerState>, currentLevel: Level?, mazeSeed: Long) {
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
               mazeSeed = mazeSeed,
               playerName = defaultName,
               uid = multiplayerManager.getCurrentUserUid() ?: "" // Use getter for local UID as fallback
            )
        }
       Log.d(TAG, "Default initial states created: $initialStates")
       // Update Firebase with default states
        multiplayerManager.resetAllPlayerStatesFirebase(initialStates)
  }

  // Helper function to assign colors and names based on profiles, handling duplicates
  private fun assignColorsAndNamesForRematch(playerIds: Set<String>, playerProfiles: Map<String, PlayerProfile?>, initialStates: MutableMap<String, PlayerState>, currentLevel: Level?, mazeSeed: Long) {
      Log.d(TAG, "Assigning colors and names based on profiles for rematch.")
      if (currentLevel !is MazeLevel) {
          Log.e(TAG, "Cannot assign states based on profiles: currentLevel is not MazeLevel or null")
          // Fallback to defaults
          assignDefaultColorsAndNames(playerIds, initialStates, currentLevel, mazeSeed)
          return
      }

      val playerColors = mutableMapOf<String, Int>()
      val chosenColors = mutableSetOf<Int>()
      val random = Random(System.currentTimeMillis()) // Use a random seed for tie-breaking

      // Attempt to assign first favorite colors
      playerIds.sorted().forEach { playerId -> // Sort to ensure consistent color assignment on both devices
          val profile = playerProfiles[playerId]
          val favoriteColors = profile?.favoriteColors ?: emptyList()
          val playerIndex = try { playerId.replace("player", "").toInt() } catch (e: Exception) { Log.e(TAG, "Failed to parse index for $playerId in assignColorsAndNamesForRematch, using 0", e); 0 }
          val defaultColor = if (playerIndex == 0) NEON_GREEN else NEON_BLUE

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
           // If color not already assigned (meaning first choice was a duplicate or not available)
           if (!playerColors.containsKey(playerId)) {
               val profile = playerProfiles[playerId]
               val favoriteColors = profile?.favoriteColors ?: emptyList()
               val playerIndex = try { playerId.replace("player", "").toInt() } catch (e: Exception) { Log.e(TAG, "Failed to parse index for $playerId in assignColorsAndNamesForRematch (fallback), using 0", e); 0 }
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
               // Ensure the chosen color is marked as used
               val finalAssignedColor = assignedColor // Use a local variable to avoid smart cast issue
               if (finalAssignedColor != null) {
                 playerColors[playerId] = finalAssignedColor
                 chosenColors.add(finalAssignedColor)
               } else { // Fallback to default if somehow no color was assigned
                  playerColors[playerId] = defaultColor
                  chosenColors.add(defaultColor)
                  Log.w(TAG, "assignColorsAndNamesForRematch: Failed to assign color for $playerId, using default.")
               }
           }

           // Create initial state for this player
           val playerIndex = try { playerId.replace("player", "").toInt() } catch (e: Exception) { Log.e(TAG, "Failed to parse index for $playerId in assignColorsAndNamesForRematch (state creation), using 0", e); 0 }
           val startPosScreen = currentLevel.getPlayerStartPosition(playerIndex)
           val (normX, normY) = currentLevel.screenToMazeCoord(startPosScreen.first, startPosScreen.second)
           val playerName = playerProfiles[playerId]?.playerName ?: "Player ${playerIndex + 1}"
           val uid = playerProfiles[playerId]?.uid ?: multiplayerManager.getCurrentUserUid() ?: ""

           initialStates[playerId] = PlayerState(
               normX = normX,
               normY = normY,
               color = playerColors[playerId] ?: (if (playerIndex == 0) NEON_GREEN else NEON_BLUE), // Use assigned color or default
               mode = 0,
               ink = Player.MAX_INK,
               active = true,
               mazeSeed = mazeSeed,
               playerName = playerName,
               uid = uid
           )
      }
       Log.d(TAG, "Final initial states for rematch: $initialStates")
      // Update Firebase with the determined states
      multiplayerManager.resetAllPlayerStatesFirebase(initialStates)
  }

  // Helper: show dialog for host waiting for other players
  private fun showWaitingForPlayersDialog() {
      runOnUiThread {
        if (isFinishing || isDestroyed) {
            Log.w(TAG, "Activity is finishing, cannot show waiting dialog.")
            return@runOnUiThread
        }
        try { waitingDialog?.dismiss() } catch (_: Exception) {}
        waitingDialog = null
        try { countdownDialog?.dismiss() } catch (_: Exception) {}
        countdownDialog = null
        Log.d(TAG, "Showing waiting for players dialog")
        waitingDialog = AlertDialog.Builder(this)
            .setTitle("Waiting")
            .setMessage("Waiting for other players to join...")
            .setCancelable(false)
            .show()
        Log.d(TAG, "Waiting dialog shown: ${waitingDialog != null}")
      }
  }

  // Helper: show dialog for joiner waiting for host
  private fun showWaitingForHostDialog() {
      runOnUiThread {
        if (isFinishing || isDestroyed) {
            Log.w(TAG, "Activity is finishing, cannot show waiting dialog.")
            return@runOnUiThread
        }
        try { waitingDialog?.dismiss() } catch (_: Exception) {}
        waitingDialog = null
        try { countdownDialog?.dismiss() } catch (_: Exception) {}
        countdownDialog = null
        Log.d(TAG, "Showing waiting for host dialog")
        waitingDialog = AlertDialog.Builder(this)
            .setTitle("Waiting")
            .setMessage("Waiting for host to start...")
            .setCancelable(false)
            .show()
        Log.d(TAG, "Waiting dialog shown: ${waitingDialog != null}")
      }
  }

  // Helper: countdown 3-2-1-GO, host signals start when countdown begins
  private fun startPreMatchCountdown(isHost: Boolean, onCountdownFinished: (() -> Unit)? = null) {
      if (isFinishing || isDestroyed) {
          Log.w(TAG, "Activity is finishing, cannot show countdown dialog.")
          return 
      }
      try {
          Log.d(TAG, "Starting pre-match countdown, isHost=$isHost")
          waitingDialog?.dismiss()
          countdownDialog?.dismiss()
          countdownDialog = AlertDialog.Builder(this)
              .setCancelable(false)
              .setMessage("3")
              .show()
          Log.d(TAG, "Countdown dialog shown with initial '3'")
          val messages = listOf("2", "1", "GO")
          val handler = Handler(Looper.getMainLooper())
          var index = 0
          val runnable = object : Runnable {
              override fun run() {
                  try {
                      if (index < messages.size) {
                          countdownDialog?.setMessage(messages[index])
                          Log.d(TAG, "Countdown updated to: "+messages[index])
                          index++
                          handler.postDelayed(this, 1000)
                      } else {
                          Log.d(TAG, "Countdown finished, starting match")
                          countdownDialog?.dismiss()
                          // Read the synchronized startTime from Firebase before starting the match
                          readAndStartWithSynchronizedTime(onCountdownFinished)
                      }
                  } catch (e: Exception) {
                      Log.e(TAG, "Error in countdown runnable", e)
                      countdownDialog?.dismiss()
                      readAndStartWithSynchronizedTime(onCountdownFinished)
                  }
              }
          }
          handler.postDelayed(runnable, 1000)
          if (isHost) {
              Log.d(TAG, "Host is sending match start signal")
              multiplayerManager.sendMatchStart()
          }
      } catch (e: Exception) {
          Log.e(TAG, "Error in startPreMatchCountdown", e)
          readAndStartWithSynchronizedTime(onCountdownFinished)
      }
  }

  private fun readAndStartWithSynchronizedTime(onCountdownFinished: (() -> Unit)?) {
      // Read the startTime from Firebase and store it in matchStartTime
      val gameId = multiplayerManager.currentGameId
      if (gameId == null) {
          Log.e(TAG, "readAndStartWithSynchronizedTime: currentGameId is null!")
          onCountdownFinished?.invoke() ?: actuallyStartMatch()
          return
      }
      val gameRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("games").child(gameId)
      gameRef.child("startTime").addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
          override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
              val startTime = snapshot.getValue(Long::class.java)
              if (startTime != null) {
                  Log.d(TAG, "readAndStartWithSynchronizedTime: Got startTime from Firebase: $startTime")
                  matchStartTime = startTime
              } else {
                  Log.w(TAG, "readAndStartWithSynchronizedTime: startTime not found, using local time")
                  matchStartTime = System.currentTimeMillis()
              }
              onCountdownFinished?.invoke() ?: actuallyStartMatch()
          }
          override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
              Log.e(TAG, "readAndStartWithSynchronizedTime: Failed to read startTime", error.toException())
              matchStartTime = System.currentTimeMillis()
              onCountdownFinished?.invoke() ?: actuallyStartMatch()
          }
      })
  }

  // Helper: finally start the actual game
  private fun actuallyStartMatch() {
      try {
          rematchInProgressHandled = false // Reset the flag after match actually starts
          Log.d(TAG, "Starting actual match with complexity: $mazeComplexity")
          gameView.initGame(mazeComplexity)
          val localPlayerId = multiplayerManager.localPlayerId
          if (localPlayerId != null) {
              val uid = multiplayerManager.getCurrentUserUid()
              if (uid != null) {
                  ProfileRepository.loadPlayerProfile(uid) { profile: PlayerProfile? ->
                      val playerColor = if (profile?.favoriteColors?.isNotEmpty() == true) {
                          profile.favoriteColors[0]
                      } else {
                          if (localPlayerId == "player0") NEON_GREEN else NEON_BLUE
                      }
                      val playerName = profile?.playerName ?: "Player "+(if (localPlayerId == "player0") 1 else 2)
                      gameView.setLocalPlayerId(localPlayerId, playerColor, playerName)
                      Log.d(TAG, "actuallyStartMatch: Set local player ID after profile load: $localPlayerId, name: $playerName, color: $playerColor")
                  }
              } else {
                  val playerIndex = try { localPlayerId.replace("player", "").toInt() } catch (e: Exception) { Log.e(TAG, "Failed to parse index for $localPlayerId in actuallyStartMatch fallback, using 0", e); 0 }
                  val defaultColor = if (playerIndex == 0) NEON_GREEN else NEON_BLUE
                  val defaultName = "Player "+(playerIndex + 1)
                  gameView.setLocalPlayerId(localPlayerId, defaultColor, defaultName)
                  Log.w(TAG, "actuallyStartMatch: Set local player ID with defaults: $localPlayerId, name: $defaultName, color: $defaultColor")
              }
          } else {
              Log.e(TAG, "actuallyStartMatch: localPlayerId is null, cannot set local player.")
          }
          // Use the synchronized matchStartTime if available
          val startTime = matchStartTime ?: System.currentTimeMillis()
          gameView.startGameMode(GameMode.COVERAGE, matchDurationMs, startTime)
          gameView.startGameLoop()
          Log.d(TAG, "Match started successfully")
      } catch (e: Exception) {
          Log.e(TAG, "Error starting match", e)
          Toast.makeText(this, "Error starting game. Please try again.", Toast.LENGTH_LONG).show()
          finish()
      }
  }

  /** Show countdown before restarting the rematch */
  private fun showRematchCountdownAndRestart() { /* no-op, replaced by coordinated flow */ }

  private fun showError(message: String) {
      Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
      finish()
  }
}


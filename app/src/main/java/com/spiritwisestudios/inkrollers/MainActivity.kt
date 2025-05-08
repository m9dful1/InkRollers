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

  companion object {
      private const val TAG = "MainActivity"
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
        if (bothYes) restartMatch() else finish() // back to Home
      }
    }

    // Removed: handleIntentExtras() here to prevent duplicate game creation

    findViewById<Button>(R.id.btn_toggle).setOnClickListener {
        gameView.getLocalPlayer()?.toggleMode()
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

      val initialColor = if (mode == HomeActivity.MODE_HOST) Color.RED else Color.BLUE
      val initialState = PlayerState(color = initialColor)

      when (mode) {
          HomeActivity.MODE_HOST -> {
              multiplayerManager.hostGame(initialState, matchDurationMs, mazeComplexity) { success, gameId, gameSettings ->
                  if (success && gameId != null) {
                      this.localPlayerId = multiplayerManager.localPlayerId
                      Log.d(TAG, "Host game successful. Game ID: $gameId. Settings: Duration=${gameSettings?.durationMs}, Complexity=${gameSettings?.complexity}")
                      
                      // Set up player count listener *immediately* after confirmation
                      multiplayerManager.onPlayerCountChanged = { count ->
                          Log.d(TAG, "Host: onPlayerCountChanged received count: $count") // Add log
                          if (count >= 2) {
                              // Only trigger once
                              Log.d(TAG, "Host: Player count >= 2, triggering countdown.")
                              multiplayerManager.onPlayerCountChanged = null // Nullify listener *before* UI action
                              runOnUiThread {
                                  if (!isFinishing && !isDestroyed) { // Add check before UI update
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
                      Log.d(TAG, "Host: Player count listener attached.") // Confirm listener attachment

                      // Show waiting dialog and toast *after* setting up listener
                      runOnUiThread {
                          if (!isFinishing && !isDestroyed) { // Add check
                             showWaitingForPlayersDialog()
                             Toast.makeText(this, "Hosting Game: $gameId", Toast.LENGTH_LONG).show()
                          } 
                      }
                      
                      Log.i(TAG, "Hosting setup complete. Game ID: $gameId, Player ID: ${this.localPlayerId}")
                  } else {
                      Log.e(TAG, "Failed to host game.")
                      runOnUiThread {
                           if (!isFinishing && !isDestroyed) { // Add check
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
  
  override fun onDestroy() {
      super.onDestroy()
      Log.d(TAG, "onDestroy called, leaving game...")
      multiplayerManager.leaveGame()
  }

  override fun onPause(){ super.onPause(); gameView.pause() }
  override fun onResume(){ super.onResume(); gameView.resume() }

  /** Display rematch dialog and send answer */
  private fun showRematchDialog(didWin: Boolean) {
    val message = if (didWin) "You Won!" else "You Lost"
    AlertDialog.Builder(this)
      .setTitle(message)
      .setMessage("Play Again?")
      .setPositiveButton("Yes") { _, _ -> multiplayerManager.sendRematchAnswer(true) }
      .setNegativeButton("No") { _, _ -> multiplayerManager.sendRematchAnswer(false) }
      .setCancelable(false)
      .show()
  }

  /** Reset state and start a fresh match */
  private fun restartMatch() {
    Log.d(TAG, "Starting restartMatch...")
    // 1. Stop the old game thread and wait for it
    gameView.stopThread()

    // 2. Clear non-player Firebase state
    Log.d(TAG, "Clearing Firebase paint/rematch state...")
    multiplayerManager.clearPaintActions()
    multiplayerManager.clearRematchAnswers()

    // --- 3. Calculate and Reset Player States in Firebase --- 
    Log.d(TAG, "Calculating and resetting Firebase player states...")
    val currentLevel = gameView.getCurrentLevel() // Get level for coordinate calc
    val playerIds = gameView.getActivePlayerIds() // Get IDs before local state is cleared
    val initialStates = mutableMapOf<String, PlayerState>()

    if (currentLevel is MazeLevel) {
        playerIds.forEach { playerId ->
            val playerIndex = try {
                playerId.replace("player", "").toInt()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse index for $playerId in restartMatch, using 0", e)
                0
            }
            val startPosScreen = currentLevel.getPlayerStartPosition(playerIndex)
            val (normX, normY) = currentLevel.screenToMazeCoord(startPosScreen.first, startPosScreen.second)
            // Determine initial color based on index (or fetch from existing GameView player before clear?)
            // Assuming Host=player0=RED, Others=BLUE for simplicity here
            val initialColor = if (playerIndex == 0) Color.RED else Color.BLUE 

            initialStates[playerId] = PlayerState(
                normX = normX,
                normY = normY,
                color = initialColor,
                mode = 0, // Default PAINT mode
                ink = Player.MAX_INK, // Full ink
                active = true,
                mazeSeed = multiplayerManager.mazeSeed // Keep same seed
            )
        }
        multiplayerManager.resetAllPlayerStatesFirebase(initialStates)
    } else {
        Log.e(TAG, "Cannot reset player states: currentLevel is not MazeLevel or null")
        // Handle error - maybe force finish? For now, log and continue cautiously.
    }
    // -----------------------------------------------------------

    // 4. Reset Local GameView state (paint, flags)
    Log.d(TAG, "Clearing GameView surface...")
    gameView.clearPaintSurface()

    // 5. Re-initialize Local GameView (maze, players map, thread)
    Log.d(TAG, "Initializing game view (no thread start)...")
    gameView.initGame(mazeComplexity)
    gameView.setLocalPlayerId(localPlayerId) // Recreates local player object

    // 6. Re-attach listener for the *next* rematch decision
    Log.d(TAG, "Setting up rematch listener...")
    multiplayerManager.setupRematchListener()

    // 7. Delay slightly, then start the game mode and the thread
    Log.d(TAG, "Posting delayed start...")
    Handler(Looper.getMainLooper()).postDelayed({
        Log.d(TAG, "Executing delayed start: startGameMode and startThread")
        gameView.startGameMode(GameMode.COVERAGE, matchDurationMs)
        gameView.startGameLoop() // Use the new method to start the loop
    }, 100) // 100ms delay
  }

  // Helper: show dialog for host waiting for other players
  private fun showWaitingForPlayersDialog() {
      // Check if activity is finishing or destroyed before showing
      if (isFinishing || isDestroyed) {
          Log.w(TAG, "Activity is finishing, cannot show waiting dialog.")
          return
      }
      
      // Dismiss any existing dialogs first
      waitingDialog?.dismiss()
      
      Log.d(TAG, "Showing waiting for players dialog")
      waitingDialog = AlertDialog.Builder(this)
          .setTitle("Waiting")
          .setMessage("Waiting for other players to join...")
          .setCancelable(false)
          .show()
      Log.d(TAG, "Waiting dialog shown: ${waitingDialog != null}")
  }

  // Helper: show dialog for joiner waiting for host
  private fun showWaitingForHostDialog() {
      // Check if activity is finishing or destroyed before showing
      if (isFinishing || isDestroyed) {
          Log.w(TAG, "Activity is finishing, cannot show waiting dialog.")
          return
      }
      
      // Dismiss any existing dialogs first
      waitingDialog?.dismiss()
      
      Log.d(TAG, "Showing waiting for host dialog")
      waitingDialog = AlertDialog.Builder(this)
          .setTitle("Waiting")
          .setMessage("Waiting for host to start...")
          .setCancelable(false)
          .show()
      Log.d(TAG, "Waiting dialog shown: ${waitingDialog != null}")
  }

  // Helper: countdown 3-2-1-GO, host signals start when countdown begins
  private fun startPreMatchCountdown(isHost: Boolean) {
      // Check if activity is finishing or destroyed before showing
      if (isFinishing || isDestroyed) {
          Log.w(TAG, "Activity is finishing, cannot show countdown dialog.")
          // If activity is finishing, we probably shouldn't start the match either
          return 
      }
      
      try {
          Log.d(TAG, "Starting pre-match countdown, isHost=$isHost")
          
          // Dismiss any existing dialogs first
          waitingDialog?.dismiss()
          countdownDialog?.dismiss()
          
          // Immediately show "3"
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
                          Log.d(TAG, "Countdown updated to: ${messages[index]}")
                          index++
                          handler.postDelayed(this, 1000)
                      } else {
                          Log.d(TAG, "Countdown finished, starting match")
                          countdownDialog?.dismiss()
                          actuallyStartMatch()
                      }
                  } catch (e: Exception) {
                      Log.e(TAG, "Error in countdown runnable", e)
                      // Make sure we dismiss dialog and start match even if there was an error
                      countdownDialog?.dismiss()
                      actuallyStartMatch()
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
          // Fallback to direct match start if countdown fails
          actuallyStartMatch()
      }
  }

  // Helper: finally start the actual game
  private fun actuallyStartMatch() {
      try {
          Log.d(TAG, "Starting actual match with complexity: $mazeComplexity")
          gameView.initGame(mazeComplexity)
          gameView.setLocalPlayerId(localPlayerId)
          gameView.startGameMode(GameMode.COVERAGE, matchDurationMs)
          gameView.startGameLoop()
          Log.d(TAG, "Match started successfully")
      } catch (e: Exception) {
          Log.e(TAG, "Error starting match", e)
          Toast.makeText(this, "Error starting game. Please try again.", Toast.LENGTH_LONG).show()
          finish()
      }
  }
}

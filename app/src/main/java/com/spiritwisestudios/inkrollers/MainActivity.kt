package com.spiritwisestudios.inkrollers

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.os.Build
import com.spiritwisestudios.inkrollers.TimerHudView
import com.spiritwisestudios.inkrollers.GameModeManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.spiritwisestudios.inkrollers.ui.DialogManager
import com.spiritwisestudios.inkrollers.ui.GameSetupController
import com.spiritwisestudios.inkrollers.ui.RematchCoordinator
import com.spiritwisestudios.inkrollers.repository.ProfileRepository
import com.spiritwisestudios.inkrollers.model.PlayerProfile
import kotlin.random.Random
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
  private lateinit var gameView: GameView
  private lateinit var inkHudView: InkHudView
  private lateinit var coverageHudView: CoverageHudView
  private lateinit var zoneHudView: ZoneHudView
  private lateinit var timerHudView: TimerHudView
  
  private lateinit var multiplayerManager: MultiplayerManager
  
  // UI helpers
  private lateinit var dialogManager: DialogManager
  private lateinit var gameSetupController: GameSetupController
  private lateinit var rematchCoordinator: RematchCoordinator
  
  // Add Firebase Auth field
  private lateinit var auth: FirebaseAuth
  
  // Audio manager
  private lateinit var audioManager: AudioManager
  
  // Game state manager for persistence
  private lateinit var gameStateManager: GameStateManager
  
  // Flag to track if user is intentionally exiting
  private var isIntentionalExit = false

  companion object {
      private const val TAG = "MainActivity"
      private val NEON_GREEN = Color.parseColor("#39FF14")
      private val NEON_BLUE = Color.parseColor("#1F51FF")
              const val EXTRA_CAMPAIGN_LEVEL = "com.spiritwisestudios.inkrollers.CAMPAIGN_LEVEL"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    // Enable full screen immersive mode
    enableFullScreenMode()

    // Initialize Firebase Auth
    auth = Firebase.auth
    
    // Initialize Game State Manager
    gameStateManager = GameStateManager(this)
    
    // Initialize AudioManager
    audioManager = AudioManager.getInstance(this)
    audioManager.initialize()

    gameView = findViewById(R.id.game_view)
    inkHudView = findViewById(R.id.ink_hud_view)
    coverageHudView = findViewById(R.id.coverage_hud_view)
    zoneHudView = findViewById(R.id.zone_hud_view)
    timerHudView = findViewById(R.id.timer_hud_view)

    multiplayerManager = MultiplayerManager()
    
    // Initialize UI helpers
    dialogManager = DialogManager()
    dialogManager.initialize(this)
    gameSetupController = GameSetupController(this, dialogManager)
    rematchCoordinator = RematchCoordinator(this, dialogManager)
    
    // Initialize the GameSetupController with required dependencies
    gameSetupController.initialize(multiplayerManager, gameView, gameStateManager)
    
    // Initialize the RematchCoordinator with required dependencies
    rematchCoordinator.initialize(multiplayerManager, gameView, gameSetupController, lifecycleScope)
    
    // Set up callbacks for the GameSetupController
    gameSetupController.onGameSetupComplete = { gameId ->
        Log.d(TAG, "Game setup complete for game: $gameId")
    }
    
    gameSetupController.onSetupError = { errorMessage ->
        Log.e(TAG, "Game setup error: $errorMessage")
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        finish()
    }
    
    gameSetupController.onMatchStart = {
        actuallyStartMatch()
    }
    
    // Listen for database permission/connectivity issues
    multiplayerManager.onDatabaseError = { errorMessage ->
        runOnUiThread {
            // Show detailed error in both Toast and Log
            Toast.makeText(this, "Firebase error: $errorMessage", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Firebase database error: $errorMessage")
            
            // Also show in a dialog for longer messages
            dialogManager.showFirebaseErrorDialog(this@MainActivity, errorMessage) { finish() }
        }
    }
    
    // Set up rematch coordinator callbacks
    rematchCoordinator.setupRematchCallbacks()
    rematchCoordinator.onMatchStarted = {
        actuallyStartMatch()
    }
    rematchCoordinator.onRematchError = { errorMessage ->
        showError(errorMessage)
    }

    setupUI()
    
    // Sign in anonymously, then proceed with game setup
    signInAnonymouslyAndProceed()
  }

  /**
   * Sets up the user interface components
   */
  private fun setupUI() {
    val toggleButton = findViewById<Button>(R.id.btn_toggle)
    // Initialize button appearance for default PAINT mode (tap & hold to refill)
    toggleButton.setTextColor(Color.WHITE)
    toggleButton.setBackgroundColor(Color.parseColor("#2196F3")) // Blue indicates paint mode active
    toggleButton.text = "REFILL"
    toggleButton.isClickable = true
    toggleButton.isFocusable = false  // Prevent focus issues

    // Change behavior to hold-to-refill using touch listener
    toggleButton.setOnTouchListener { view, event ->
        val localPlayer = gameView.getLocalPlayer()
        Log.d(TAG, "Button touch event: ${event.action} / ${event.actionMasked}")
        Log.d(TAG, "Local player available: ${localPlayer != null}")
        if (localPlayer != null) {
            Log.d(TAG, "Current player mode: ${localPlayer.mode}")
        }
        
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                Log.d(TAG, "REFILL button pressed - switching to FILL mode")
                if (localPlayer != null) {
                    Log.d(TAG, "Calling changeModeIfNeeded(1) on local player")
                    localPlayer.changeModeIfNeeded(1)
                    toggleButton.setBackgroundColor(Color.parseColor("#FF9800"))
                    toggleButton.text = "REFILLING"
                    // Add haptic feedback
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                } else {
                    Log.w(TAG, "Cannot switch to FILL mode - local player is null")
                }
                return@setOnTouchListener true
            }
            android.view.MotionEvent.ACTION_UP -> {
                Log.d(TAG, "REFILL button released - switching to PAINT mode")
                if (localPlayer != null) {
                    Log.d(TAG, "Calling changeModeIfNeeded(0) on local player")
                    localPlayer.changeModeIfNeeded(0)
                    toggleButton.setBackgroundColor(Color.parseColor("#2196F3"))
                    toggleButton.text = "REFILL"
                } else {
                    Log.w(TAG, "Cannot switch to PAINT mode - local player is null")
                }
                return@setOnTouchListener true
            }
            android.view.MotionEvent.ACTION_CANCEL -> {
                Log.d(TAG, "REFILL button cancelled - switching to PAINT mode")
                if (localPlayer != null) {
                    Log.d(TAG, "Calling changeModeIfNeeded(0) on local player (cancel)")
                    localPlayer.changeModeIfNeeded(0)
                    toggleButton.setBackgroundColor(Color.parseColor("#2196F3"))
                    toggleButton.text = "REFILL"
                } else {
                    Log.w(TAG, "Cannot switch to PAINT mode - local player is null (cancel)")
                }
                return@setOnTouchListener true
            }
        }
        false
    }

    findViewById<Button>(R.id.btn_toggle_p2).visibility = android.view.View.GONE

    gameView.setHudView(inkHudView)
    gameView.setCoverageHudView(coverageHudView)
    gameView.setZoneHudView(zoneHudView)
    gameView.setTimerHudView(timerHudView)
    gameView.setMultiplayerManager(multiplayerManager)
    
    // Set up rematch coordinator to handle match end
    rematchCoordinator.setupMatchEndCallback()
  }

  /**
   * Enables full screen immersive mode by hiding status bar and navigation bar
   */
  private fun enableFullScreenMode() {
    // Allow content to extend into display cutout areas on Android P and above
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val lp = window.attributes
      lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
      window.attributes = lp
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      // Android 11+ (API 30+)
      window.setDecorFitsSystemWindows(false)
      window.insetsController?.let { controller ->
        controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      }
    } else {
      // For Android 10 (API 29) and below
      @Suppress("DEPRECATION")
      window.decorView.systemUiVisibility = (
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        or View.SYSTEM_UI_FLAG_FULLSCREEN
      )
    }
    
    // Keep screen on during gameplay
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }



  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      // Re-enable full screen mode when window regains focus
      enableFullScreenMode()
    }
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
                  
                  // Check if we should attempt to rejoin an existing game
                  if (gameStateManager.shouldAttemptRejoin()) {
                      Log.d(TAG, "Found existing game state, attempting to rejoin")
                      gameSetupController.attemptRejoinExistingGame()
                  } else {
                      Log.d(TAG, "No existing game to rejoin, proceeding with normal flow")
                      gameStateManager.clearIntentionalExit() // Clear any old exit flags
                      handleIntentExtras() // Proceed with hosting/joining new game
                  }
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
      
      when (mode) {
          HomeActivity.MODE_CAMPAIGN -> {
              val campaignLevelId = intent.getStringExtra(EXTRA_CAMPAIGN_LEVEL)
              Log.d(TAG, "Starting campaign level: $campaignLevelId")
              // TODO: Initialize campaign level
              // For now, just show a message
              Toast.makeText(this, "Campaign mode coming soon!", Toast.LENGTH_SHORT).show()
              finish()
          }
          else -> {
              val gameId = intent.getStringExtra(HomeActivity.EXTRA_GAME_ID)
              val timeLimit = if (mode == HomeActivity.MODE_HOST) intent.getIntExtra(HomeActivity.EXTRA_TIME_LIMIT_MINUTES, 3) else null
              val complexity = intent.getStringExtra(HomeActivity.EXTRA_MAZE_COMPLEXITY)
              val gameMode = intent.getStringExtra(HomeActivity.EXTRA_GAME_MODE)
              val isPrivate = if (mode == HomeActivity.MODE_HOST) intent.getBooleanExtra(HomeActivity.EXTRA_IS_PRIVATE_MATCH, false) else null
              
              Log.d(TAG, "Received mode: $mode")
              
              gameSetupController.handleGameSetup(mode, gameId, timeLimit, complexity, gameMode, isPrivate)
          }
      }
  }

  
  override fun onDestroy() {
      // Dismiss dialogs to avoid leaks/crashes
      dialogManager.dismissAllDialogs()
      super.onDestroy()
      
      Log.d(TAG, "onDestroy called. IsIntentionalExit: $isIntentionalExit, IsFinishing: $isFinishing")
      
      if (isIntentionalExit || isFinishing) {
          // Only leave game if user is intentionally exiting or app is finishing
          Log.d(TAG, "Leaving game due to intentional exit or app finishing")
          gameStateManager.markIntentionalExit()
          gameStateManager.clearActiveGameState()
          multiplayerManager.leaveGame()
      } else {
          // App is going to background, save game state for potential rejoin
          Log.d(TAG, "App backgrounding - saving game state for potential rejoin")
          saveCurrentGameState()
      }
  }

  override fun onPause(){ 
    super.onPause()
    audioManager.pauseAudio()
    gameView.pause() 
  }
  override fun onResume() { 
    super.onResume()
    // Re-enable full screen mode when returning to the activity
    enableFullScreenMode()
    audioManager.resumeAudio()
    gameView.resume() 
  }
  
  // Handle back button press as intentional exit
  override fun onBackPressed() {
      Log.d(TAG, "Back button pressed - marking as intentional exit")
      isIntentionalExit = true
      super.onBackPressed()
  }
  
  /**
   * Save the current game state to persistent storage for potential rejoin
   */
     private fun saveCurrentGameState() {
       val gameId = multiplayerManager.currentGameId
       val playerId = gameSetupController.getLocalPlayerId()
       val playerColor = gameView.getLocalPlayer()?.getColor()
       val playerName = gameView.getLocalPlayer()?.playerName ?: ""
       val playerUid = auth.currentUser?.uid ?: ""
      
      if (gameId != null && playerId != null) {
          Log.d(TAG, "Saving game state: gameId=$gameId, playerId=$playerId")
          gameStateManager.saveActiveGameState(
              gameId = gameId,
              localPlayerId = playerId,
              matchDurationMs = gameSetupController.getMatchDurationMs(),
              mazeComplexity = gameSetupController.getMazeComplexity(),
              gameMode = gameSetupController.getGameMode(),
              isPrivateMatch = false, // Default since this info isn't tracked in GameSetupController
              isHost = (playerId == "player0"),
              playerColor = playerColor,
              playerName = playerName,
              playerUid = playerUid
          )
             } else {
           Log.w(TAG, "Cannot save game state: gameId=$gameId, playerId=$playerId")
       }
   }
   












  // Helper: finally start the actual game
  private fun actuallyStartMatch() {
      try {
          val mazeComplexity = gameSetupController.getMazeComplexity()
          Log.d(TAG, "Starting actual match with complexity: $mazeComplexity")
          gameView.initGame(mazeComplexity)
          val localPlayerId = gameSetupController.getLocalPlayerId()
          if (localPlayerId != null) {
              val uid = multiplayerManager.getCurrentUserUid()
              if (uid != null) {
                  // TODO: Convert to coroutines
                  val playerIndex = try { localPlayerId.replace("player", "").toInt() } catch (e: Exception) { 0 }
                  val defaultColor = if (playerIndex == 0) NEON_GREEN else NEON_BLUE
                  val defaultName = "Player "+(playerIndex + 1)
                  gameView.setLocalPlayerId(localPlayerId, defaultColor, defaultName)
                  Log.d(TAG, "actuallyStartMatch: Using default values temporarily")
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
          val startTime = gameSetupController.getMatchStartTime() ?: System.currentTimeMillis()
          val gameMode = gameSetupController.getGameMode()
          val matchDurationMs = gameSetupController.getMatchDurationMs()
          Log.d(TAG, "actuallyStartMatch: Using duration: ${matchDurationMs}ms, startTime: $startTime")
          val selectedGameMode = when (gameMode) {
              HomeActivity.GAME_MODE_ZONES -> GameMode.ZONES
              HomeActivity.GAME_MODE_COVERAGE -> GameMode.COVERAGE // Ensure this case is explicitly handled
              else -> GameMode.COVERAGE // Default to Coverage if string is unexpected
          }
          // Play match start sound and begin background music
          audioManager.playSound(AudioManager.SoundType.MATCH_START)
          audioManager.startBackgroundMusic()
          
          gameView.startGameMode(selectedGameMode, matchDurationMs, startTime)
          gameView.startGameLoop()
          Log.d(TAG, "Match started successfully with game mode: $selectedGameMode")
      } catch (e: Exception) {
          Log.e(TAG, "Error starting match", e)
          Toast.makeText(this, "Error starting game. Please try again.", Toast.LENGTH_LONG).show()
          finish()
      }
  }

  private fun showError(message: String) {
      Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
      finish()
  }

  private fun showFirebaseErrorDialog(message: String) {
      dialogManager.showFirebaseErrorDialog(this, message) { finish() }
  }
}


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

/**
 * Main game activity managing the complete multiplayer match lifecycle.
 * 
 * Coordinates between Firebase authentication, multiplayer game hosting/joining,
 * player profile management, HUD components, and match flow progression.
 * Handles complex scenarios including:
 * 
 * - Anonymous Firebase authentication for multiplayer access
 * - Game hosting with custom settings (duration, complexity, game mode, privacy)
 * - Game joining with matchmaking (specific game ID or random available game)
 * - Player profile loading for color preferences and display names
 * - Pre-match countdown coordination between host and clients
 * - Match timing synchronization using Firebase server timestamps
 * - Post-match rematch voting and game state reset coordination
 * - UI dialog management for various waiting and countdown states
 * 
 * Integrates with GameView for core gameplay and MultiplayerManager for
 * Firebase communication and real-time state synchronization.
 */
class MainActivity:AppCompatActivity(){
  private lateinit var gameView:GameView
  private lateinit var inkHudView: InkHudView
  private lateinit var coverageHudView: CoverageHudView
  private lateinit var zoneHudView: ZoneHudView
  private lateinit var timerHudView: TimerHudView
  private var matchDurationMs: Long = 180000L // 3 minute match
  private var mazeComplexity: String = HomeActivity.COMPLEXITY_HIGH // Default High
  private var gameMode: String = HomeActivity.GAME_MODE_COVERAGE // Default Coverage
  private var isPrivateMatch: Boolean = false // Default to public
  
  private lateinit var multiplayerManager: MultiplayerManager
  private var localPlayerId: String? = null
  
  // New fields for dialogs
  private var waitingDialog: AlertDialog? = null
  private var countdownDialog: AlertDialog? = null
  
  // Add Firebase Auth field
  private lateinit var auth: FirebaseAuth
  
  // Audio manager for game events
  private lateinit var audioManager: com.spiritwisestudios.inkrollers.AudioManager

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

    auth = Firebase.auth
    audioManager = com.spiritwisestudios.inkrollers.AudioManager.getInstance(this)

    gameView=findViewById(R.id.game_view)
    inkHudView = findViewById(R.id.ink_hud_view)
    coverageHudView = findViewById(R.id.coverage_hud_view)
    zoneHudView = findViewById(R.id.zone_hud_view)
    timerHudView = findViewById(R.id.timer_hud_view)

    multiplayerManager = MultiplayerManager(this)
    
    multiplayerManager.onDatabaseError = { errorMessage ->
        runOnUiThread {
            Toast.makeText(this, "Firebase error: $errorMessage", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Firebase database error: $errorMessage")
        }
    }
    
    multiplayerManager.onRematchDecision = { bothYes ->
      Handler(Looper.getMainLooper()).post {
        if (!bothYes) {
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
      }
    }

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

    val toggleButton = findViewById<Button>(R.id.btn_toggle)
    toggleButton.setTextColor(Color.WHITE)
    toggleButton.setBackgroundColor(Color.parseColor("#2196F3"))
    toggleButton.text = "FILL"
    toggleButton.setOnClickListener {
        gameView.getLocalPlayer()?.let { player ->
            player.toggleMode()
            val isPaintMode = player.mode == 0
            toggleButton.text = if (isPaintMode) "FILL" else "PAINT"
            val bgColor = if (isPaintMode) Color.parseColor("#2196F3") else Color.parseColor("#FF9800")
            toggleButton.setBackgroundColor(bgColor)
            toggleButton.setTextColor(Color.WHITE)
        }
    }

    findViewById<Button>(R.id.btn_toggle_p2).visibility = android.view.View.GONE

    gameView.setHudView(inkHudView)
    gameView.setCoverageHudView(coverageHudView)
    gameView.setZoneHudView(zoneHudView)
    gameView.setTimerHudView(timerHudView)
    gameView.setMultiplayerManager(multiplayerManager)
    gameView.onMatchEnd = { didWin ->
      // Stop background music when match ends
      audioManager.stopBackgroundMusic()
      Handler(Looper.getMainLooper()).post { showRematchDialog(didWin) }
    }

    signInAnonymouslyAndProceed()
  }

  /** Authenticates user anonymously with Firebase before proceeding with game setup. */
  private fun signInAnonymouslyAndProceed() {
      Log.d(TAG, "Attempting anonymous sign-in...")
      auth.signInAnonymously()
          .addOnCompleteListener(this) { task ->
              if (task.isSuccessful) {
                  Log.d(TAG, "Anonymous sign-in successful")
                  val user = auth.currentUser
                  Log.d(TAG, "Authenticated with UID: ${user?.uid}")
                  handleIntentExtras()
              } else {
                  Log.w(TAG, "Anonymous sign-in failed", task.exception)
                  Toast.makeText(baseContext, "Authentication failed: ${task.exception?.message}",
                      Toast.LENGTH_SHORT).show()
                  finish()
              }
          }
  }

  /**
   * Processes intent extras to determine game mode (HOST/JOIN) and settings.
   * Loads player profile for color preferences, then initiates appropriate game flow.
   * For HOST: Creates new game with specified settings and waits for players.
   * For JOIN: Joins existing game by ID or searches for random available game.
   */
  private fun handleIntentExtras() {
      val mode = intent.getStringExtra(HomeActivity.EXTRA_MODE)
      Log.d(TAG, "Received mode: $mode")

      if (mode == HomeActivity.MODE_HOST) {
          matchDurationMs = intent.getIntExtra(HomeActivity.EXTRA_TIME_LIMIT_MINUTES, 3) * 60000L
          mazeComplexity = intent.getStringExtra(HomeActivity.EXTRA_MAZE_COMPLEXITY) ?: HomeActivity.COMPLEXITY_HIGH
          gameMode = intent.getStringExtra(HomeActivity.EXTRA_GAME_MODE) ?: HomeActivity.GAME_MODE_COVERAGE
          isPrivateMatch = intent.getBooleanExtra(HomeActivity.EXTRA_IS_PRIVATE_MATCH, false)
          Log.d(TAG, "Host selected settings: Duration=${matchDurationMs}ms, Complexity=$mazeComplexity, GameMode=$gameMode, Private=$isPrivateMatch")
      }

      val uid = Firebase.auth.currentUser?.uid
      if (uid != null) {
          ProfileRepository.loadPlayerProfile(uid) { profile: PlayerProfile? ->
              val playerColor = if (profile?.favoriteColors?.isNotEmpty() == true) {
                  profile.favoriteColors[0]
              } else {
                  if (mode == HomeActivity.MODE_HOST) NEON_GREEN else NEON_BLUE
              }
              
              val playerName = profile?.playerName ?: "Player ${if (mode == HomeActivity.MODE_HOST) 1 else 2}"

              val initialState = PlayerState(
                  color = playerColor,
                  playerName = playerName,
                  uid = uid
              )

      when (mode) {
          HomeActivity.MODE_HOST -> {
              multiplayerManager.hostGame(initialState, matchDurationMs, mazeComplexity, gameMode, isPrivateMatch) { success, gameId, gameSettings ->
                  if (success && gameId != null) {
                      this.localPlayerId = multiplayerManager.localPlayerId
                      Log.d(TAG, "Host game successful. Game ID: $gameId. Settings: Duration=${gameSettings?.durationMs}, Complexity=${gameSettings?.complexity}, GameMode=${gameSettings?.gameMode}")
                              
                      gameView.setLocalPlayerId("player0", playerColor, playerName)
                      
                      multiplayerManager.onPlayerCountChanged = { count ->
                          Log.d(TAG, "Host: onPlayerCountChanged received count: $count")
                          if (count >= 2) {
                              Log.d(TAG, "Host: Player count >= 2, triggering countdown.")
                              multiplayerManager.setupRematchListener()
                              multiplayerManager.onPlayerCountChanged = null
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
                           gameView.setLocalPlayerId(playerId, playerColor, playerName)

                           gameSettings?.let {
                               matchDurationMs = it.durationMs
                               mazeComplexity = it.complexity
                               gameMode = it.gameMode
                               Log.d(TAG, "Joined game with settings: Duration=${matchDurationMs}ms, Complexity=$mazeComplexity, GameMode=$gameMode")
                           }
                           runOnUiThread {
                               showWaitingForHostDialog()
                               Toast.makeText(this, "Joined Game: $gameId as $playerId", Toast.LENGTH_LONG).show()
                           }
                           multiplayerManager.onMatchStartRequested = {
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
                   runOnUiThread {
                       Toast.makeText(this, "Searching for an available game...", Toast.LENGTH_SHORT).show()
                   }
                   multiplayerManager.joinGame(null, initialState) { success, playerId, gameSettings ->
                       if (success && playerId != null) {
                           this.localPlayerId = playerId
                           gameView.setLocalPlayerId(playerId, playerColor, playerName)

                           gameSettings?.let {
                               matchDurationMs = it.durationMs
                               mazeComplexity = it.complexity
                               gameMode = it.gameMode
                               Log.d(TAG, "Joined random game with settings: Duration=${matchDurationMs}ms, Complexity=$mazeComplexity, GameMode=$gameMode")
                           }
                           val joinedGameId = multiplayerManager.currentGameId
                           runOnUiThread {
                               Toast.makeText(this, "Joined Random Game: $joinedGameId as $playerId", Toast.LENGTH_LONG).show()
                               showWaitingForHostDialog()
                           }
                           multiplayerManager.onMatchStartRequested = {
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

  override fun onPause(){
    super.onPause()
    gameView.pause()
    audioManager.pauseAudio()
  }
  override fun onResume(){
    super.onResume()
    gameView.resume()
    audioManager.resumeAudio()
  }

  /** Displays post-match rematch dialog and submits player's vote to Firebase. */
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
      
      // Play win/lose sound effect
      if (didWin) {
        audioManager.playSound(com.spiritwisestudios.inkrollers.AudioManager.SoundType.MATCH_END_WIN)
      } else {
        audioManager.playSound(com.spiritwisestudios.inkrollers.AudioManager.SoundType.MATCH_END_LOSE)
      }
      
      val message = if (didWin) "You Won!" else "You Lost"
      AlertDialog.Builder(this)
        .setTitle(message)
        .setMessage("Play Again?")
        .setPositiveButton("Yes") { _, _ -> 
          audioManager.playSound(com.spiritwisestudios.inkrollers.AudioManager.SoundType.UI_CLICK)
          Log.d(TAG, "Rematch dialog: YES selected. Sending rematch answer true.")
          multiplayerManager.sendRematchAnswer(true) 
        }
        .setNegativeButton("No") { _, _ -> 
          audioManager.playSound(com.spiritwisestudios.inkrollers.AudioManager.SoundType.UI_CLICK)
          Log.d(TAG, "Rematch dialog: NO selected. Sending rematch answer false.")
          multiplayerManager.sendRematchAnswer(false) 
        }
        .setCancelable(false)
        .show()
    }
  }

  /** Initiates rematch flow by resetting game state and showing countdown. */
  private fun restartMatchForRematch() {
    restartMatch(resetOnly = true)
    showRematchCountdownAndStart()
  }

  /** Displays rematch countdown and starts the match after completion. */
  private fun showRematchCountdownAndStart() {
    startPreMatchCountdown(isHost = (localPlayerId == "player0")) {
        Log.d(TAG, "showRematchCountdownAndStart: Countdown finished, starting rematch match.")
        actuallyStartMatch()
    }
  }

  /**
   * Resets game state for rematch coordination.
   * Handles complex player profile loading, color assignment conflict resolution,
   * Firebase state synchronization, and game reinitialization.
   * 
   * @param resetOnly If true, only resets state without starting match flow
   */
  private fun restartMatch(resetOnly: Boolean = false) {
    Log.d(TAG, "restartMatch called. Beginning rematch reset flow.")
    gameView.stopThread()
    Log.d(TAG, "Clearing Firebase paint/rematch state...")
    multiplayerManager.clearPaintActions()
    multiplayerManager.clearRematchAnswers()
    
    val uid = Firebase.auth.currentUser?.uid
    if (uid != null) {
        ProfileRepository.loadPlayerProfile(uid) { _: PlayerProfile? ->
    Log.d(TAG, "Calculating and resetting Firebase player states...")
    val currentLevel = gameView.getCurrentLevel()
    val playerIds = gameView.getActivePlayerIds()
    val initialStates = mutableMapOf<String, PlayerState>()
            
            val playerProfiles = mutableMapOf<String, PlayerProfile?>()
            val uidsToLoad = mutableSetOf<String>()

            multiplayerManager.getPlayersState { playerStatesMap ->
                Log.d(TAG, "restartMatch: Fetched all player states from Firebase: ${playerStatesMap.keys}")
                playerStatesMap.forEach { (playerId, playerState) ->
                    if (playerState != null && playerState.uid.isNotEmpty()) {
                        uidsToLoad.add(playerState.uid)
                    } else {
                         Log.w(TAG, "restartMatch: Player state for $playerId is null or has empty UID.")
                    }
                }

                if (uidsToLoad.isEmpty()) {
                    Log.w(TAG, "No valid player UIDs found from Firebase states for rematch setup.")
                    assignDefaultColorsAndNames(playerIds, initialStates, currentLevel, multiplayerManager.mazeSeed)
                    gameView.clearPaintSurface()
                    gameView.initGame(mazeComplexity)
                    val localPlayerId = multiplayerManager.localPlayerId
                    if (localPlayerId != null) {
                       gameView.setLocalPlayerId(localPlayerId, initialStates[localPlayerId]?.color, initialStates[localPlayerId]?.playerName ?: "")
                    }
                    return@getPlayersState
                }

                var loadedProfileCount = 0
                uidsToLoad.forEach { uidToLoad ->
                    ProfileRepository.loadPlayerProfile(uidToLoad) { profile ->
                        val playerIdsForUid = playerStatesMap.filter { it.value?.uid == uidToLoad }.keys
                        playerIdsForUid.forEach { playerId ->
                             playerProfiles[playerId] = profile
                        }
                       
                        loadedProfileCount++
                        if (loadedProfileCount == uidsToLoad.size) {
                            Log.d(TAG, "All profiles loaded for rematch. Assigning colors and names.")
                            assignColorsAndNamesForRematch(playerIds, playerProfiles, initialStates, currentLevel, multiplayerManager.mazeSeed)
                           
                            gameView.clearPaintSurface()
                            gameView.initGame(mazeComplexity)
                            
                            val localPlayerId = multiplayerManager.localPlayerId
                            if (localPlayerId != null) {
                                gameView.setLocalPlayerId(localPlayerId, initialStates[localPlayerId]?.color, initialStates[localPlayerId]?.playerName ?: "")
                            }
                            
                            if (!resetOnly) {
                            }
                        }
                    }
                }
            }
        }
    } else {
        Log.e(TAG, "Cannot restart match: User not authenticated")
        Toast.makeText(this, "Error: User not authenticated", Toast.LENGTH_SHORT).show()
    }
  }

  /** Assigns default colors and names when player profiles cannot be loaded. */
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
               uid = multiplayerManager.getCurrentUserUid() ?: ""
            )
        }
       Log.d(TAG, "Default initial states created: $initialStates")
        multiplayerManager.resetAllPlayerStatesFirebase(initialStates)
  }

  /**
   * Assigns colors and names based on player profiles with conflict resolution.
   * Handles duplicate color preferences by prioritizing first choice, then second choice,
   * then falling back to defaults. Ensures consistent assignment across devices.
   */
  private fun assignColorsAndNamesForRematch(playerIds: Set<String>, playerProfiles: Map<String, PlayerProfile?>, initialStates: MutableMap<String, PlayerState>, currentLevel: Level?, mazeSeed: Long) {
      Log.d(TAG, "Assigning colors and names based on profiles for rematch.")
      if (currentLevel !is MazeLevel) {
          Log.e(TAG, "Cannot assign states based on profiles: currentLevel is not MazeLevel or null")
          assignDefaultColorsAndNames(playerIds, initialStates, currentLevel, mazeSeed)
          return
      }

      val playerColors = mutableMapOf<String, Int>()
      val chosenColors = mutableSetOf<Int>()

      playerIds.sorted().forEach { playerId ->
          val profile = playerProfiles[playerId]
          val favoriteColors = profile?.favoriteColors ?: emptyList()

          var assignedColor: Int? = null
          if (favoriteColors.isNotEmpty() && favoriteColors[0] !in chosenColors) {
              assignedColor = favoriteColors[0]
          }

          if (assignedColor != null) {
              playerColors[playerId] = assignedColor
              chosenColors.add(assignedColor)
          }
      }

      playerIds.sorted().forEach { playerId ->
           if (!playerColors.containsKey(playerId)) {
               val profile = playerProfiles[playerId]
               val favoriteColors = profile?.favoriteColors ?: emptyList()
               val playerIndex = try { playerId.replace("player", "").toInt() } catch (e: Exception) { Log.e(TAG, "Failed to parse index for $playerId in assignColorsAndNamesForRematch (fallback), using 0", e); 0 }

               var assignedColor: Int? = null
               if (favoriteColors.size > 1 && favoriteColors[1] !in chosenColors) {
                   assignedColor = favoriteColors[1]
               }

               if (assignedColor == null) {
                   assignedColor = favoriteColors.firstOrNull { it !in chosenColors } ?: (if (playerIndex == 0) NEON_GREEN else NEON_BLUE)
               }
               val finalAssignedColor = assignedColor
               if (finalAssignedColor != null) {
                 playerColors[playerId] = finalAssignedColor
                 chosenColors.add(finalAssignedColor)
               } else {
                  playerColors[playerId] = (if (playerIndex == 0) NEON_GREEN else NEON_BLUE)
                  chosenColors.add((if (playerIndex == 0) NEON_GREEN else NEON_BLUE))
                  Log.w(TAG, "assignColorsAndNamesForRematch: Failed to assign color for $playerId, using default.")
               }
           }

           val playerIndex = try { playerId.replace("player", "").toInt() } catch (e: Exception) { Log.e(TAG, "Failed to parse index for $playerId in assignColorsAndNamesForRematch (state creation), using 0", e); 0 }
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
               mazeSeed = mazeSeed,
               playerName = playerName,
               uid = uid
           )
      }
       Log.d(TAG, "Final initial states for rematch: $initialStates")
      multiplayerManager.resetAllPlayerStatesFirebase(initialStates)
  }

  /** Displays waiting dialog for host until other players join. */
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

  /** Displays waiting dialog for joiner until host starts the match. */
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

  /**
   * Displays synchronized 3-2-1-GO countdown for match start.
   * Host signals match start to all clients when countdown begins.
   * Coordinates timing to ensure simultaneous game start across devices.
   */
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
          audioManager.playSound(com.spiritwisestudios.inkrollers.AudioManager.SoundType.COUNTDOWN_TICK)
          Log.d(TAG, "Countdown dialog shown with initial '3'")
          val messages = listOf("2", "1", "GO")
          val handler = Handler(Looper.getMainLooper())
          var index = 0
          val runnable = object : Runnable {
              override fun run() {
                  try {
                      if (index < messages.size) {
                          countdownDialog?.setMessage(messages[index])
                          // Play appropriate sound for countdown
                          if (messages[index] == "GO") {
                              audioManager.playSound(com.spiritwisestudios.inkrollers.AudioManager.SoundType.COUNTDOWN_GO)
                          } else {
                              audioManager.playSound(com.spiritwisestudios.inkrollers.AudioManager.SoundType.COUNTDOWN_TICK)
                          }
                          Log.d(TAG, "Countdown updated to: "+messages[index])
                          index++
                          handler.postDelayed(this, 1000)
                      } else {
                          Log.d(TAG, "Countdown finished, starting match")
                          countdownDialog?.dismiss()
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

  /** Retrieves synchronized start time from Firebase for coordinated match timing. */
  private fun readAndStartWithSynchronizedTime(onCountdownFinished: (() -> Unit)?) {
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

  /**
   * Initializes and starts the actual match gameplay.
   * Handles final player setup, game mode selection, and synchronized timing.
   * Called after countdown completion with Firebase-synchronized start time.
   */
  private fun actuallyStartMatch() {
      try {
          rematchInProgressHandled = false
          Log.d(TAG, "Starting actual match with complexity: $mazeComplexity")
          
          // Play match start sound and begin background music
          audioManager.playSound(com.spiritwisestudios.inkrollers.AudioManager.SoundType.MATCH_START)
          audioManager.startBackgroundMusic()
          
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
          val startTime = matchStartTime ?: System.currentTimeMillis()
          val selectedGameMode = when (gameMode) {
              HomeActivity.GAME_MODE_ZONES -> GameMode.ZONES
              HomeActivity.GAME_MODE_COVERAGE -> GameMode.COVERAGE
              else -> GameMode.COVERAGE
          }
          gameView.startGameMode(selectedGameMode, matchDurationMs, startTime)
          gameView.startGameLoop()
          Log.d(TAG, "Match started successfully with game mode: $selectedGameMode")
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


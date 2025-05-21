package com.spiritwisestudios.inkrollers
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.graphics.PixelFormat
import android.util.Log
import java.util.concurrent.ConcurrentHashMap // Use thread-safe map
import com.spiritwisestudios.inkrollers.GameModeManager
import com.spiritwisestudios.inkrollers.GameMode
import com.spiritwisestudios.inkrollers.CoverageHudView
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.spiritwisestudios.inkrollers.TimerHudView
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF

class GameView @JvmOverloads constructor(ctx:Context,attrs:AttributeSet?=null):
    SurfaceView(ctx,attrs),SurfaceHolder.Callback, MultiplayerManager.RemoteUpdateListener { // Implement listener
  private lateinit var surface:PaintSurface
  // Use a Map to store players, keyed by their Firebase ID (player0, player1, etc.)
  val players = ConcurrentHashMap<String, Player>()
  private val joysticks = ConcurrentHashMap<String, VirtualJoystick>() // Also map joysticks by player ID
  private val pendingPlayerStates = ConcurrentHashMap<String, PlayerState>() // Cache for early arrivals
  private var inkHudView: InkHudView? = null
  private var coverageHudView: CoverageHudView? = null
  private var gameModeManager: GameModeManager? = null
  private var timerHudView: TimerHudView? = null
  // Use var and lateinit for the thread to allow recreation
  private lateinit var thread: GameThread 
  
  private var currentLevel: Level? = null 
  private var coverageStats: Map<Int, Float> = emptyMap()
  private var coverageUpdateFrames = 30 
  private var frameCount = 0
  
  // Multiplayer specific fields
  private var multiplayerManager: MultiplayerManager? = null
  private var localPlayerId: String? = null
  
  // New state flag to prevent premature end-check
  private var isMatchReady: Boolean = false
  
  // New field for match end listener
  var onMatchEnd: ((Boolean) -> Unit)? = null
  // Flag to prevent multiple match end notifications
  private var endNotified: Boolean = false
  
  // Background image for the maze (center-cropped)
  private val bgBitmap: Bitmap by lazy { BitmapFactory.decodeResource(context.resources, R.drawable.space_bg) }
  
  companion object {
      private const val TAG = "GameView"
      var savedPaintBitmap: android.graphics.Bitmap? = null // For paint persistence
  }
  
  init {
    holder.addCallback(this)
    isFocusable = true
    holder.setFormat(PixelFormat.TRANSLUCENT)
    setZOrderMediaOverlay(true)
  }
  
  override fun surfaceCreated(h:SurfaceHolder){
    Log.i(TAG, "surfaceCreated called. SurfaceView size: ${width}x${height}")
    // Restore paint surface if available
    surface = if (savedPaintBitmap != null) {
        PaintSurface(width, height, savedPaintBitmap!!)
    } else {
        PaintSurface(width, height)
    }
    savedPaintBitmap = null
    // Update all players to use the new surface
    for (player in players.values) {
        player.surface = surface
    }
  }
  
  override fun surfaceDestroyed(h:SurfaceHolder){ 
    Log.i(TAG, "surfaceDestroyed called. Stopping game thread if running.")
    // Save paint surface bitmap for persistence
    if (::surface.isInitialized) {
        savedPaintBitmap = surface.getBitmapCopy()
    }
    stopThread() // Ensure thread is stopped cleanly when surface is destroyed
    Log.d(TAG, "Surface destroyed, game thread stopped.")
  }
  override fun surfaceChanged(h:SurfaceHolder,f:Int,w:Int,h2:Int){}
  
  /** Stop the game thread and wait for it to finish. */
  fun stopThread() {
      synchronized(this) {
          // Check if thread is initialized and alive before trying to stop
          if (::thread.isInitialized && thread.isAlive) {
              Log.d(TAG, "stopThread: Attempting to stop game thread. State: ${thread.state}")
              var retry = true
              thread.running = false
              while (retry) {
                  try {
                      thread.join(500)
                      if (!thread.isAlive) {
                          retry = false
                          Log.d(TAG, "Game thread stopped and joined.")
                      } else {
                          Log.w(TAG, "Game thread still alive after join timeout. Retrying...")
                      }
                  } catch (e: InterruptedException) {
                      Log.w(TAG, "Interrupted while joining game thread", e)
                  }
              }
          } else {
              Log.d(TAG, "stopThread called but thread not initialized or not alive. State: ${if (::thread.isInitialized) thread.state else "not initialized"}")
          }
      }
  }

  /** Starts the current game thread instance if initialized and not alive. */
  fun startGameLoop() {
      synchronized(this) {
          Log.d(TAG, "startGameLoop: Called. Thread initialized: ${if (::thread.isInitialized) "yes" else "no"}, Thread state: ${if (::thread.isInitialized) thread.state else "not initialized"}")
          if (!::thread.isInitialized) {
              Log.e(TAG, "Cannot start game loop: thread not initialized.")
              return
          }
          Log.d(TAG, "startGameLoop: Thread state before start: ${thread.state}")
          if (!thread.isAlive) {
              try {
                 if (thread.state == Thread.State.NEW) {
                    thread.running = true // Set running to true BEFORE starting
                    Log.d(TAG, "startGameLoop: Starting thread (NEW state)")
                    thread.start()
                    Log.d(TAG, "Game thread started.")
                 } else {
                     Log.w(TAG, "Attempting to start thread in unexpected state: ${thread.state}. Re-initializing.")
                     thread = GameThread(holder, this)
                     thread.running = true
                     Log.d(TAG, "startGameLoop: Re-initialized thread, now starting.")
                     thread.start()
                     Log.d(TAG, "Game thread re-initialized and started.")
                 }
              } catch(e: IllegalThreadStateException) {
                  Log.e(TAG, "Failed to start game thread", e)
              }
          } else {
              Log.d(TAG, "startGameLoop called but thread already running.")
          }
      }
  }

  fun update(deltaTime: Float){
      val localPlayer = getLocalPlayer()
      val localJoystick = if (localPlayerId != null) joysticks[localPlayerId] else null

      // Added Log
      // if (localJoystick != null) {
      //      Log.d(TAG, "update: Joystick state: dirX=${localJoystick.directionX}, dirY=${localJoystick.directionY}, mag=${localJoystick.magnitude}")
      // } else {
      //      Log.d(TAG, "update: Local joystick is null")
      // }

      // --- Update Local Player --- 
      if (localPlayer != null && localJoystick != null && currentLevel is MazeLevel) { // Ensure level is MazeLevel
          localPlayer.move(localJoystick.directionX, localJoystick.directionY, localJoystick.magnitude, currentLevel, deltaTime)
          
          // Convert local player's screen position to normalized coordinates
          val (nx, ny) = (currentLevel as MazeLevel).screenToMazeCoord(localPlayer.x, localPlayer.y)
          
          // Send local player state to Firebase using normalized coordinates
          val currentState = PlayerState(
              normX = nx,
              normY = ny,
              color = localPlayer.getColor(),
              mode = localPlayer.mode,
              ink = localPlayer.ink,
              active = true, // Mark as active
              playerName = localPlayer.playerName // Pass player name
          )
          multiplayerManager?.updateLocalPlayerState(currentState)
      }
      
      // --- Update Other Game Elements --- 
      currentLevel?.update()
      
      // Periodically update coverage stats
      frameCount++
      if (frameCount >= coverageUpdateFrames) {
          frameCount = 0
          currentLevel?.let { level ->
              // Ensure surface is initialized before calculating coverage
              if (::surface.isInitialized) {
                  coverageStats = level.calculateCoverage(surface)
              } else {
                  Log.w(TAG, "Surface not initialized, skipping coverage calculation")
              }
              // Log coverage stats (remove in production)
              // ... (logging code can remain or be adapted)
          }
      }
      
      // Update HUD based on local player
      localPlayer?.let { 
          inkHudView?.updateHud(it.getInkPercent(), it.getModeText()) 
      }
      // Handle game mode updates and coverage HUD
      if (isMatchReady) { // Only check if match is flagged as ready
          gameModeManager?.let { mgr ->
            // Update countdown timer
            timerHudView?.updateTime(mgr.timeRemainingMs())
            mgr.update() // Update the manager's internal state (timer)

            // Check if finished *after* updating the manager
            if (mgr.isFinished()) {
              if (!endNotified) {
                endNotified = true
                thread.running = false // Stop the game loop
                finishMatch("timer_expired")
              } // end !endNotified
            } else {
              // Match is ongoing, update coverage HUD if applicable
              if (mgr.mode == GameMode.COVERAGE && currentLevel is MazeLevel) {
                try {
                    val allStats = (currentLevel as MazeLevel).calculateCoverage(surface)
                    val activeColors = players.values.map { it.getColor() }.toSet()
                    val activeStats = allStats.filterKeys { it in activeColors }

                    val leftColor = players["player0"]?.getColor()
                    val rightColor = players["player1"]?.getColor()

                    coverageHudView?.updateCoverage(activeStats, leftColor, rightColor)
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating coverage HUD", e)
                }
              }
            }
          } // end gameModeManager?.let
      } // end isMatchReady
  }
  
  override fun draw(c:Canvas){
    // Draw center-cropped background image
    run {
      val bmp = bgBitmap
      val viewW = width.toFloat()
      val viewH = height.toFloat()
      val bmpW = bmp.width.toFloat()
      val bmpH = bmp.height.toFloat()
      val scale = maxOf(viewW / bmpW, viewH / bmpH)
      val scaledW = bmpW * scale
      val scaledH = bmpH * scale
      val left = (viewW - scaledW) / 2
      val top = (viewH - scaledH) / 2
      val dest = RectF(left, top, left + scaledW, top + scaledH)
      c.drawBitmap(bmp, null, dest, null)
    }
    
    // Log.d(TAG, "GameView.draw() called. Canvas: $c")
    // background image covers the canvas; remove white fill
    
    // Log.d(TAG, "GameView.draw() - Before surface.drawTo(c)")
    if(::surface.isInitialized) { // Add this check for safety, though it should be by now
        surface.drawTo(c)
        // Log.d(TAG, "GameView.draw() - After surface.drawTo(c)")
    } else {
        // Log.w(TAG, "GameView.draw() - Surface not initialized, skipping surface.drawTo(c)")
    }
    
    // Log.d(TAG, "GameView.draw() - Before currentLevel?.draw(c). currentLevel is null: ${currentLevel == null}")
    currentLevel?.draw(c)
    // Log.d(TAG, "GameView.draw() - After currentLevel?.draw(c)")

    // Draw all players (local and remote)
    // Log.d(TAG, "GameView.draw() - Drawing ${players.size} players.")
    for ((id, player) in players) { // Changed to iterate with ID for better logging
        // Log.d(TAG, "GameView.draw() - Drawing player $id")
        player.draw(c)
    }

    // Draw local joystick only
    // Log.d(TAG, "GameView.draw() - Before localPlayerId?.let for joystick. localPlayerId: $localPlayerId")
    localPlayerId?.let { joysticks[it]?.draw(c) }
    // Log.d(TAG, "GameView.draw() - After joystick draw.")

    drawCornerNames(c)
  }

  /** Draw the names of the players in the screen corners. */
  private fun drawCornerNames(canvas: Canvas) {
      val textPaint = Paint().apply {
          color = Color.BLACK
          textSize = 40f
          isAntiAlias = true
          typeface = Typeface.DEFAULT_BOLD
      }
      val margin = 16f
      players["player0"]?.playerName?.takeIf { it.isNotEmpty() }?.let { name ->
          textPaint.textAlign = Paint.Align.LEFT
          canvas.drawText(name, margin, margin + textPaint.textSize, textPaint)
      }
      players["player1"]?.playerName?.takeIf { it.isNotEmpty() }?.let { name ->
          textPaint.textAlign = Paint.Align.RIGHT
          canvas.drawText(name, width - margin, height - margin, textPaint)
      }
  }
  
  override fun onTouchEvent(e:MotionEvent):Boolean{
      // Only handle touch events for the local player's joystick
      if (localPlayerId == null) return false // No local player yet
      
      val pointerIndex = e.actionIndex
      val pointerId = e.getPointerId(pointerIndex)
      val action = e.actionMasked
      
      // We only care about the first touch for the local player's joystick for now
      // Multi-touch could be used for other actions later if needed.
      if (pointerId == 0) { 
          val joystick = joysticks[localPlayerId] ?: return false // Safety check
          val x = e.getX(pointerIndex)
          val y = e.getY(pointerIndex)
          
          when (action) {
              MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> joystick.onDown(x, y)
              MotionEvent.ACTION_MOVE -> {
                  // ACTION_MOVE events might report multiple points, ensure we use index 0
                  val moveX = e.getX(0)
                  val moveY = e.getY(0)
                  joystick.onMove(moveX, moveY)
              }
              MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> joystick.onUp()
              MotionEvent.ACTION_CANCEL -> joystick.onUp()
          }
          return true // Indicate we handled the touch event
      }
      
      return false // Don't handle other pointer IDs for now
  }
  
  // --- Multiplayer Integration Methods --- 

  fun setMultiplayerManager(manager: MultiplayerManager) {
      this.multiplayerManager = manager
      // Register this GameView as the listener for remote updates
      this.multiplayerManager?.updateListener = this 
      Log.d(TAG, "MultiplayerManager set.")
  }

  fun setLocalPlayerId(id: String?, playerColor: Int? = null, playerName: String = "") {
      Log.d(TAG, "setLocalPlayerId called with id: $id, color: $playerColor, name: $playerName")
      this.localPlayerId = id
      if (id == null) return // Exit early if ID is null
      Log.d(TAG, "Local player ID set: $id")
      
      try {
          // Initialize joystick for the local player if not already done
          if (!joysticks.containsKey(id)) {
              joysticks[id] = VirtualJoystick()
          }
          
          // Always ensure the local player is created/updated with correct start position
          // AFTER currentLevel is initialized.
          if (currentLevel != null) {
               // Extract player index from ID
               val playerIndex = try {
                   id.replace("player", "").toInt()
               } catch (e: Exception) {
                   Log.e(TAG, "Failed to parse player index from $id, using 0", e)
                   0 // Fallback to 0
               }
               
               val startPos = currentLevel!!.getPlayerStartPosition(playerIndex)
               // Use provided color or fallback to default colors
               val defaultColor = if (playerIndex == 0) Color.parseColor("#39FF14") else Color.parseColor("#1F51FF")
               val playerColor = playerColor ?: defaultColor
               
               // Check if player already exists (e.g., from a very fast Firebase update after initGame)
               var localPlayer = players[id]
               if (localPlayer == null) {
                   Log.d(TAG, "Creating local player $id at definite start position (${startPos.first}, ${startPos.second})")
                   localPlayer = Player(
                       surface,
                       startPos.first,
                       startPos.second,
                       playerColor,
                       multiplayerManager,
                       currentLevel, // Pass level reference
                       playerName // Pass the provided player name
                   )
                   players[id] = localPlayer
               } else {
                   // Player exists, ensure its position is correct
                   Log.d(TAG, "Updating existing local player $id object to definite start position (${startPos.first}, ${startPos.second})")
                   localPlayer.x = startPos.first
                   localPlayer.y = startPos.second
                   localPlayer.playerName = playerName // Set player name for local player directly
                   // Potentially update color/other fields if needed, though less likely
               }
               
               // Update Firebase with the correct initial normalized position
               if (currentLevel is MazeLevel) {
                   val (normX, normY) = (currentLevel as MazeLevel).screenToMazeCoord(localPlayer.x, localPlayer.y)
                   val initialPositionUpdate = mapOf(
                       "normX" to normX,
                       "normY" to normY
                   )
                   Log.d(TAG, "Updating Firebase with initial normalized position for $id: ($normX, $normY)")
                   multiplayerManager?.updateLocalPlayerPartialState(initialPositionUpdate)
               } else {
                   Log.w(TAG, "Could not update initial Firebase position for $id - currentLevel is not MazeLevel.")
               }
          } else {
              Log.w(TAG, "setLocalPlayerId called for $id but currentLevel is still null!")
          }
      } catch (e: Exception) {
          Log.e(TAG, "Error in setLocalPlayerId for $id", e)
      }
  }

  fun getLocalPlayer(): Player? {
      return if (localPlayerId != null) players[localPlayerId] else null
  }

  // --- MultiplayerManager.RemoteUpdateListener Implementation --- 

  private fun actuallyProcessPlayerState(playerId: String, newState: PlayerState) {
      if (currentLevel == null) { // Should be checked by caller or ensured by context
          Log.e(TAG, "actuallyProcessPlayerState called with null currentLevel for $playerId")
          return
      }
      val mazeLevel = currentLevel as? MazeLevel
      if (mazeLevel == null) {
          Log.e(TAG, "MazeLevel not available for coordinate conversion in actuallyProcessPlayerState for $playerId")
          return
      }

      var player = players[playerId]
      if (player == null) {
          // New player joined
          Log.i(TAG, "Adding new player via actuallyProcessPlayerState: $playerId")
          val (sx, sy) = mazeLevel.mazeToScreenCoord(newState.normX, newState.normY)
          player = Player(
              surface,
              sx,
              sy,
              newState.color,
              multiplayerManager,
              currentLevel,
              newState.playerName // Pass player name from newState
          )
          players[playerId] = player
      } else {
          // Update existing player state (Only for REMOTE players)
          if (playerId != localPlayerId) {
              val (sx, sy) = mazeLevel.mazeToScreenCoord(newState.normX, newState.normY)
              Log.d(TAG, "Updating remote player $playerId screen pos to ($sx, $sy) from norm (${newState.normX}, ${newState.normY}) via actuallyProcessPlayerState")
              player.x = sx
              player.y = sy
          }
      }
      // Always update mode and ink for all players based on Firebase state
      player.mode = newState.mode
      player.ink = newState.ink
      if (playerId != localPlayerId) { // Only update name from state for remote players
          player.playerName = newState.playerName // Update player name directly
      }
      // Commented out per-frame logs to reduce clutter
      // Log.d(TAG, "Player $playerId state processed. Position: (${player.x}, ${player.y}), Ink: ${player.ink}, Mode: ${player.mode}")
  }

  override fun onPlayerStateChanged(playerId: String, newState: PlayerState) {
      // Commented out per-frame logs to reduce clutter
      // Log.d(TAG, "onPlayerStateChanged for $playerId. Active: ${newState.active}, NormPos: (${newState.normX}, ${newState.normY})")
      // Log.d(TAG, "Processing player state for $playerId as currentLevel is available.")
      // Log.d(TAG, "Player $playerId state processed. Position: (${player.x}, ${player.y}), Ink: ${player.ink}, Mode: ${player.mode}")

      if (!newState.active) {
          Log.i(TAG, "Player $playerId reported as inactive. Removing.")
          players.remove(playerId)
          pendingPlayerStates.remove(playerId) // Also remove from pending if they leave before processing
          joysticks.remove(playerId)
          return
      }

      if (currentLevel == null) {
          Log.w(TAG, "Caching pending player state for $playerId as currentLevel is null.")
          pendingPlayerStates[playerId] = newState
          return
      }

      // currentLevel is available, process immediately
      Log.d(TAG, "Processing player state for $playerId as currentLevel is available.")
      pendingPlayerStates.remove(playerId) // Remove if it was pending
      try {
          actuallyProcessPlayerState(playerId, newState)
      } catch (e: Exception) {
          Log.e(TAG, "Error in onPlayerStateChanged (direct processing) for $playerId", e)
      }
  }

  override fun onPlayerRemoved(playerId: String) {
      Log.i(TAG, "onPlayerRemoved: $playerId")
      players.remove(playerId)
      joysticks.remove(playerId) // Clean up joystick just in case
  }

  // Updated to better handle errors
  override fun onPaintAction(x: Int, y: Int, color: Int, normalizedX: Float?, normalizedY: Float?) {
      try {
          if (currentLevel == null) {
              Log.w(TAG, "onPaintAction called but currentLevel is null. Paint action dropped.")
              return
          }
          if (normalizedX != null && normalizedY != null && currentLevel is MazeLevel) {
              // Convert normalized coordinates to screen coordinates for this device
              val screenCoords = (currentLevel as MazeLevel).mazeToScreenCoord(normalizedX, normalizedY)
              Log.d(TAG, "Applying remote paint at normalized($normalizedX,$normalizedY) -> screen(${screenCoords.first},${screenCoords.second})")
              
              // Safety check before painting
              if (screenCoords.first >= 0 && screenCoords.first < surface.w.toFloat() &&
                  screenCoords.second >= 0 && screenCoords.second < surface.h.toFloat()) {
                  surface.paintAt(screenCoords.first, screenCoords.second, color)
              } else {
                  Log.w(TAG, "Remote paint coordinates out of bounds: (${screenCoords.first}, ${screenCoords.second})")
              }
          } else {
              // Fallback to original method using absolute coordinates
              if (x >= 0 && x < surface.w && y >= 0 && y < surface.h) {
                  Log.d(TAG, "Applying remote paint at ($x,$y) with color #${color.toString(16)}")
                  surface.paintAt(x.toFloat(), y.toFloat(), color)
              }
          }
      } catch (e: Exception) {
          Log.e(TAG, "Error in onPaintAction", e)
      }
  }

  // For player scale adjustment in onPlayerStateChanged
  private fun getPlayerScaleFactor(): Float {
      return (currentLevel as? MazeLevel)?.getScale() ?: 1.0f
  }

  // --- Existing Methods --- 
  
  fun pause(){ 
    if (::thread.isInitialized) {
        thread.running=false 
    } else {
        Log.w(TAG, "pause() called but thread is not initialized.")
    }
  }
  
  fun resume(){ 
      if (::thread.isInitialized) {
      if (thread.state == Thread.State.TERMINATED) {
              // The thread was stopped when the surface was destroyed. Create a
              // fresh instance so the game can resume correctly.
              Log.w(TAG, "Game thread terminated. Recreating for resume().")

              thread = GameThread(holder, this)
              thread.running = true
              try {
                  thread.start()
              } catch (e: Exception) {
                  Log.e(TAG, "Failed to restart game thread", e)
              }

          } else if (!thread.running) {
              Log.d(TAG, "Resuming game thread.")
              thread.running = true 
              // If thread wasn't alive, start it (shouldn't happen often here unless paused before start)
              if (!thread.isAlive) {
                  try {
                      thread.start()
                  } catch (e: IllegalThreadStateException) {
                       Log.e(TAG, "Failed to restart game thread on resume", e)
                  }
              }
          }
      } else {
          Log.w(TAG, "resume() called but thread is not initialized.")
          // If thread isn't initialized, we might need to trigger game setup if appropriate
          // This depends on the expected state when resume is called before initGame
      }
  }

  fun setHudView(hudView: InkHudView) {
    this.inkHudView = hudView
  }
  
  fun getPlayerCoverage(playerIndex: Int): Float {
    // Needs rework based on player map and potentially localPlayerId mapping
    // For now, return 0f
    return 0f
  }

  /** Helper to safely get the current level instance. */
  fun getCurrentLevel(): Level? {
      Log.v(TAG, "getCurrentLevel called. currentLevel is null: ${currentLevel == null}")
      return currentLevel
  }

  /** Helper to get the IDs of currently active players in the local map. */
  fun getActivePlayerIds(): Set<String> {
      Log.v(TAG, "getActivePlayerIds called. Player keys: ${players.keys}")
      return players.keys
  }

  /**
   * Call this once the shared mazeSeed is available (after host/join completes).
   * It will create the maze, players, and a NEW GameThread instance.
   */
  fun initGame(mazeComplexity: String) { // Added mazeComplexity parameter
    Log.d(TAG, "initGame called with complexity: $mazeComplexity. Surface initialized: ${::surface.isInitialized}")
    if (!::surface.isInitialized) {
        Log.w(TAG, "initGame called but surface not ready")
        return
    }
    stopThread()
    Log.d(TAG, "initGame: After stopThread. Thread state: ${if (::thread.isInitialized) thread.state else "not initialized"}")
    // Determine the seed from the multiplayer manager, fallback to current time
    val seed = multiplayerManager?.mazeSeed?.takeIf { it != 0L } ?: System.currentTimeMillis()
    Log.d(TAG, "Initializing maze with seed: $seed, complexity: $mazeComplexity")

    // Determine the height of the coverage HUD to avoid drawing the maze beneath it
    val hudHeight = coverageHudView?.height
        ?: coverageHudView?.layoutParams?.height
        ?: 0

    // Create the level with the synchronized seed, complexity, and reserved top margin

    currentLevel = MazeLevel(width, height, 12, 20, 12f, seed, mazeComplexity, hudHeight)
    Log.d(TAG, "Created maze with viewport offset: ${(currentLevel as MazeLevel).getViewportOffset()}")
    
    // Clear previous game objects for a fresh start (especially for rematches)
    players.clear()
    joysticks.clear()

    // Process any player states that arrived before currentLevel was initialized
    if (pendingPlayerStates.isNotEmpty()) {
        Log.d(TAG, "Processing ${pendingPlayerStates.size} pending player states in initGame.")
        // Iterate over a copy of keys or entries if modification during iteration is possible,
        // though onPlayerStateChanged should now handle its own logic based on currentLevel.
        for ((pId, state) in pendingPlayerStates.toMap()) { // Use .toMap() for safe iteration against concurrent modification
            // --- START: Skip processing local player's pending state --- 
            if (pId == this.localPlayerId) {
                Log.d(TAG, "Skipping pending state processing for local player $pId in initGame. Will be handled by setLocalPlayerId.")
                continue
            }
            // --- END: Skip processing --- 
            if (state.active) { // Ensure we only try to process active states
                Log.d(TAG, "Attempting to process pending state for active player: $pId")
                try {
                    actuallyProcessPlayerState(pId, state)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing pending state for $pId in initGame", e)
                }
            } else {
                Log.w(TAG, "Skipping inactive pending player state for $pId in initGame.")
            }
        }
        pendingPlayerStates.clear() // Clear after attempting to process them
    }
    
    // Ensure match isn't considered ready until startGameMode is called
    isMatchReady = false
    
    // Create a new thread instance for the new game/match
    thread = GameThread(holder, this)
    Log.d(TAG, "New GameThread instance created.")

    // Do NOT start the thread here; wait for startGameLoop() call
    Log.d(TAG, "initGame: Created maze with seed: $seed, complexity: $mazeComplexity")
    Log.d(TAG, "initGame: Cleared previous game objects. Players: ${players.size}, Joysticks: ${joysticks.size}")
    Log.d(TAG, "initGame: Processed pending player states. Remaining: ${pendingPlayerStates.size}")
    Log.d(TAG, "initGame: New GameThread instance created. Thread state: ${thread.state}")
  }

  /**
   * Assign the coverage HUD for showing game coverage.
   */
  fun setCoverageHudView(view: CoverageHudView) {
    this.coverageHudView = view
  }

  /**
   * Start the given game mode for the specified duration (ms).
   */
  fun startGameMode(mode: GameMode, durationMs: Long, startTime: Long? = null) {
    Log.d(TAG, "startGameMode called with mode: $mode, durationMs: $durationMs, startTime: $startTime")
    gameModeManager = if (startTime != null) {
        GameModeManager(mode, durationMs, startTime)
    } else {
        GameModeManager(mode, durationMs)
    }
    gameModeManager?.start()
    isMatchReady = true
  }

  /** Clears the paint surface (removes all painted areas) */
  fun clearPaintSurface() {
    if (::surface.isInitialized) surface.clear()
    endNotified = false
  }

  /** Assign the timer HUD for showing match countdown. */
  fun setTimerHudView(view: TimerHudView) {
    this.timerHudView = view
  }

  // When match finishes, log the reason and handle win/loss
  private fun finishMatch(reason: String = "unknown") {
      Log.i(TAG, "finishMatch called. Reason: $reason")
      var didWin = false // Default to loss
      try {
          if (gameModeManager?.mode == GameMode.COVERAGE && currentLevel is MazeLevel) {
              val allStats = (currentLevel as MazeLevel).calculateCoverage(surface)
              val localColor = getLocalPlayer()?.getColor()
              val activeColors = players.values.map { it.getColor() }.toSet()
              val activeStats = allStats.filterKeys { it in activeColors }
              val localFrac = if (localColor != null) activeStats[localColor] ?: 0f else 0f
              val maxOther = activeStats.filterKeys { it != localColor }.values.maxOrNull() ?: 0f
              didWin = localFrac >= maxOther // Win includes tie
          }
      } catch (e: Exception) {
          Log.e(TAG, "Error calculating winner in finishMatch", e)
      }
      Log.i(TAG, "finishMatch: didWin=$didWin, calling onMatchEnd")
      Handler(Looper.getMainLooper()).post {
          Log.i(TAG, "onMatchEnd invoked from finishMatch. didWin=$didWin")
          onMatchEnd?.invoke(didWin)
      }
  }
}

class GameThread(private val sh:SurfaceHolder,private val gv:GameView):Thread(){
  var running=false
  private val TAG = "GameThread" // Added TAG for logging
  private var lastTimeNanos: Long = System.nanoTime() // For delta time calculation

  override fun run(){ 
      Log.i(TAG, "run() started. Initial running state: $running")
      lastTimeNanos = System.nanoTime() // Initialize lastTimeNanos before the loop starts
      try {
          while(running){ 
              // --- SAFETY CHECK: Ensure surface is valid before drawing ---
              if (!sh.surface.isValid) {
                  Log.w(TAG, "Surface is not valid, skipping frame.")
                  try { Thread.sleep(8) } catch (e: InterruptedException) { /* ignore */ }
                  continue
              }
              val currentTimeNanos = System.nanoTime()
              val deltaTimeSeconds = (currentTimeNanos - lastTimeNanos) / 1_000_000_000.0f
              lastTimeNanos = currentTimeNanos
              var c: Canvas? = null // Declare c outside try so it can be logged in finally
              try {
                  c = sh.lockCanvas()
                  if(c!=null){ 
                      try {
                          gv.update(deltaTimeSeconds) // Pass deltaTime here
                      } catch (e: Exception) {
                          Log.e(TAG, "Exception in GameView.update()", e)
                      }
                      try {
                          gv.draw(c)
                      } catch (e: Exception) {
                          Log.e(TAG, "Exception in GameView.draw()", e)
                      }
                  } else {
                      // If canvas is consistently null, the surface might not be ready or valid.
                      Log.w(TAG, "lockCanvas() returned null. Surface may not be ready.")
                      try { Thread.sleep(16) } catch (e: InterruptedException) { /* ignore */ }
                  }
              } catch (e: Exception) {
                  Log.e(TAG, "Exception in GameThread run loop (lockCanvas or unlock)", e)
              } finally {
                  // --- SAFETY CHECK: Only unlock if surface is still valid ---
                  if (c != null) {
                      if (sh.surface.isValid) {
                          try {
                              sh.unlockCanvasAndPost(c)
                          } catch (e: Exception) {
                              Log.e(TAG, "Exception in unlockCanvasAndPost", e)
                          }
                      } else {
                          Log.w(TAG, "Surface became invalid before unlockCanvasAndPost. Skipping post.")
                      }
                  }
              } 
          } 
      } catch (e: Exception) {
          Log.e(TAG, "Exception in GameThread main loop", e)
      }
      Log.i(TAG, "run() finished. Final running state: $running")
  }
}
